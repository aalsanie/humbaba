/*
 * Copyright © 2025-2026 | Humbaba is a formatting tool that formats the whole code base using safe strategy.
 *
 * Author: @aalsanie
 *
 * Plugin: https://plugins.jetbrains.com/plugin/29545-humbaba-formatter
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
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import io.humbaba.domains.model.FormatRequest
import io.humbaba.platform.IntellijEnv
import io.humbaba.platform.core.UseCaseFactory

/**
 * Tools / Popup action: format all eligible files in the *project content* (excludes libraries/excluded dirs).
 * Uses the same pipeline as single-file format:
 *   native formatter -> AI allow-listed recommendation -> safe install -> run -> refresh -> optional native pass.
 */
class FormatAllFilesAction : AnAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        object : Task.Backgroundable(project, "Humbaba: format all files", true) {
            override fun run(indicator: ProgressIndicator) {
                val (useCase, settings, registry) = UseCaseFactory.build(project)

                val eligible =
                    collectEligibleProjectFiles(project) { ext ->
                        ext.isNotBlank() && registry.findByExtension(ext).isNotEmpty()
                    }

                if (eligible.isEmpty()) {
                    notify(project, "No eligible files found in project content.", NotificationType.INFORMATION)
                    return
                }

                var okCount = 0
                var failCount = 0
                val failures = mutableListOf<String>()

                eligible.forEachIndexed { idx, vf ->
                    if (indicator.isCanceled) return
                    indicator.fraction = (idx.toDouble() / eligible.size.toDouble()).coerceIn(0.0, 1.0)
                    indicator.text = "Formatting (${idx + 1}/${eligible.size}): ${vf.presentableUrl}"

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
                        )

                    val res = useCase.execute(req)
                    vf.refresh(false, false)

                    if (res.errors.isEmpty()) {
                        okCount++
                    } else {
                        failCount++
                        failures += "${vf.name}: ${res.errors.firstOrNull() ?: "unknown error"}"
                        if (failures.size >= 20) {
                            // Prevent huge notifications; keep first 20 failures.
                            return@forEachIndexed
                        }
                    }
                }

                val msg =
                    buildString {
                        append("Humbaba Formatter completed.\n")
                        append("• Eligible files: ${eligible.size}\n")
                        append("• Success: $okCount\n")
                        append("• Failed: $failCount\n")
                        if (failures.isNotEmpty()) {
                            append("\nFailures (first ${failures.size}):\n")
                            failures.forEach { append("• ").append(it).append("\n") }
                        }
                    }.trim()

                notify(project, msg, if (failCount == 0) NotificationType.INFORMATION else NotificationType.WARNING)
            }
        }.queue()
    }

    private fun collectEligibleProjectFiles(
        project: Project,
        isEligibleExt: (String) -> Boolean,
    ): List<VirtualFile> {
        val out = mutableListOf<VirtualFile>()
        val fileIndex = ProjectRootManager.getInstance(project).fileIndex

        fileIndex.iterateContent { vf ->
            if (vf.isDirectory) return@iterateContent true
            if (vf.fileType.isBinary) return@iterateContent true
            if (!vf.isWritable) return@iterateContent true

            val ext = (vf.extension ?: "").lowercase()
            if (isEligibleExt(ext)) out += vf
            true
        }
        return out
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun notify(
        project: Project,
        message: String,
        type: NotificationType,
    ) {
        val group = NotificationGroupManager.getInstance().getNotificationGroup("Humbaba Formatter")
        group.createNotification(message, type).notify(project)
    }
}
