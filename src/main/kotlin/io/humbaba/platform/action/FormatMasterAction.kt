/*
 * Copyright © 2025-2026 | Humbaba is a safe, deterministic formatting orchestrator for polyglot repositories.
 * Reports back format coverage percentage
 *
 * Author: @aalsanie
 *
 * Plugin: https://plugins.jetbrains.com/plugin/29573-humbaba
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.humbaba.platform.action

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import io.humbaba.domains.model.FormatRequest
import io.humbaba.platform.IntellijEnv
import io.humbaba.platform.core.UseCaseFactory

/**
 * Editor-only right-click action: format the current file.
 */
class FormatMasterAction : AnAction() {
    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = project != null && editor != null && vf != null && isEligible(vf)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val (useCase, settings, _) = UseCaseFactory.build(project)

        val aiEnabled =
            if (!settings.networkAllowed) {
                false
            } else {
                Messages.showYesNoDialog(
                    project,
                    "Enable AI formatting fallback for this run?\n\n" +
                        "AI is EXPERIMENTAL and may change semantics. Review diffs.",
                    "Humbaba — AI Fallback (Experimental)",
                    "Enable",
                    "Disable",
                    Messages.getWarningIcon(),
                ) == Messages.YES
            }

        object : Task.Backgroundable(project, "Humbaba: format file", true) {
            override fun run(indicator: ProgressIndicator) {
                val req =
                    FormatRequest(
                        filePath = vf.path,
                        extension = (vf.extension ?: "").lowercase(),
                        languageId = null,
                        ideInfo = IntellijEnv.ideInfo(),
                        osInfo = IntellijEnv.osInfo(),
                        sample = null,
                        preferExistingFormatterFirst = settings.preferExistingFormatterFirst,
                        allowAutoInstall = settings.allowExternalAutoInstall,
                        networkAllowed = settings.networkAllowed,
                        aiEnabled = aiEnabled,
                        dryRun = false,
                    )

                val res = useCase.execute(req)

                val blocked = res.errors.any { it.contains("not yet trusted", ignoreCase = true) }
                if (blocked) {
                    ApplicationManager.getApplication().invokeLater {
                        val choice =
                            Messages.showYesNoDialog(
                                project,
                                """Humbaba Formatter wants to use an external formatter.
                                    For safety, only allow-listed tools are supported, and args are validated.
                                    Enable external formatter auto-install/runs?""",
                                "Humbaba Formatter — Trust",
                                "Enable",
                                "Cancel",
                                Messages.getQuestionIcon(),
                            )
                        if (choice == Messages.YES) {
                            settings.allowExternalAutoInstall = true
                            notify(project, "Enabled external auto-install. Re-run Humbaba.", NotificationType.INFORMATION)
                        } else {
                            notify(project, "Not enabled. No changes made.", NotificationType.WARNING)
                        }
                    }
                    return
                }

                vf.refresh(false, false)

                if (res.errors.isEmpty()) {
                    notify(project, buildSuccess(res, vf), NotificationType.INFORMATION)
                } else {
                    notify(project, buildFailure(res, vf), NotificationType.WARNING)
                }
            }
        }.queue()
    }

    private fun isEligible(vf: VirtualFile): Boolean = !vf.isDirectory && !vf.fileType.isBinary && vf.isWritable

    private fun notify(
        project: Project,
        message: String,
        type: NotificationType,
    ) {
        val group = NotificationGroupManager.getInstance().getNotificationGroup("Humbaba Formatter")
        group.createNotification(message, type).notify(project)
    }

    private fun buildSuccess(
        res: io.humbaba.domains.model.FormatResult,
        vf: VirtualFile,
    ): String {
        val steps = res.applied.joinToString("\n") { "• ${it.type}: ${it.message}" }

        val warnings =
            res.applied
                .filter { !it.ok }
                .joinToString("\n") { "• ${it.type}: ${it.message}" }
                .takeIf { it.isNotBlank() }
                ?.let { "\n\nWarnings:\n$it" }
                ?: ""

        val out = res.output?.let { "\n\nOutput:\n$it" } ?: ""
        return "Humbaba formatted: ${vf.name}\n\n$steps$warnings$out"
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun buildFailure(
        res: io.humbaba.domains.model.FormatResult,
        vf: VirtualFile,
    ): String {
        val steps = res.applied.joinToString("\n") { "• ${it.type}: ${it.message}" }
        val errs = res.errors.joinToString("\n") { "• $it" }
        val out = res.output?.let { "\n\nOutput:\n$it" } ?: ""
        return "Humbaba could not fully format: ${vf.name}\n\n$steps\n\nErrors:\n$errs$out"
    }
}
