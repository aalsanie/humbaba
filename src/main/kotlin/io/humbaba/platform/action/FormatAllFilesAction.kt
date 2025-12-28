/*
 * Copyright © 2025-2026 | Humbaba: AI based formatter that uses a heuristic and AI scoring system to format the whole project.
 * Reports back format coverage percentage
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
import com.intellij.openapi.vfs.VirtualFileManager
import io.humbaba.domains.model.FileFormatReport
import io.humbaba.domains.model.FormatOutcome
import io.humbaba.domains.model.FormatRequest
import io.humbaba.domains.model.FormatResult
import io.humbaba.domains.model.FormatStepType
import io.humbaba.platform.IntellijEnv
import io.humbaba.platform.core.UseCaseFactory
import io.humbaba.reporting.FormatCoverageAggregator
import io.humbaba.reporting.FormatCoverageHtmlWriter
import io.humbaba.reporting.FormatCoverageJsonWriter
import io.humbaba.reporting.FormatCoverageXmlWriter
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Formats all eligible files in project content and generates HTML / XML / JSON
 * format coverage reports.
 *
 * Deterministic outcome rules:
 * - FAILED: execute() returns errors
 * - FORMATTED: on-disk content hash changed after execute()
 * - ALREADY_FORMATTED: no errors and hash did not change
 *
 * Coverage counts FORMATTED + ALREADY_FORMATTED as covered.
 *
 * Reports saved to: <project>/target/humbaba/
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
                        val lower = ext.lowercase()
                        lower.isNotBlank() && (
                            registry.findByExtension(lower).isNotEmpty() ||
                                lower in NATIVE_ONLY ||
                                lower in NO_OP_BUT_SUCCESS
                        )
                    }

                if (eligible.isEmpty()) {
                    notify(project, "No eligible files found in project content.", NotificationType.INFORMATION)
                    return
                }

                val reports = mutableListOf<FileFormatReport>()

                eligible.forEachIndexed { idx, vf ->
                    if (indicator.isCanceled) return

                    indicator.fraction = (idx + 1).toDouble() / eligible.size.toDouble()
                    indicator.text = "Formatting (${idx + 1}/${eligible.size}): ${vf.presentableUrl}"

                    val path = Path.of(vf.path)
                    val beforeHash = hashFile(path)

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

                    val result = useCase.execute(req)

                    try {
                        vf.refresh(false, false)
                        VirtualFileManager.getInstance().syncRefresh()
                    } catch (_: Throwable) {
                        // ignore
                    }

                    val afterHash = hashFile(path)

                    reports += buildDeterministicFileReport(vf, beforeHash, afterHash, result)
                }

                val coverage = FormatCoverageAggregator.aggregate(reports)

                val reportDir = reportDir(project)
                Files.createDirectories(reportDir)

                FormatCoverageJsonWriter.write(coverage, reportDir.resolve("format-coverage.json"))
                FormatCoverageXmlWriter.write(coverage, reportDir.resolve("format-coverage.xml"))
                FormatCoverageHtmlWriter.write(coverage, reportDir.resolve("format-coverage.html"))

                // counts are based on disk changes not cache.
                val formatted = reports.count { it.outcome == FormatOutcome.FORMATTED }
                val already = reports.count { it.outcome == FormatOutcome.ALREADY_FORMATTED }
                val failed = reports.count { it.outcome == FormatOutcome.FAILED }

                val msg =
                    buildString {
                        append("Humbaba Formatter completed.\n")
                        append("• Files processed: ${coverage.totalFiles}\n")
                        append("• Formatted: $already\n")
                        append("• Failed: $failed\n")
                        append("• Format coverage: ${coverage.coveragePercent}%\n")
                        append("\nReports saved to:\n")
                        append("• ${reportDir.resolve("format-coverage.html")}\n")
                        append("• ${reportDir.resolve("format-coverage.json")}\n")
                        append("• ${reportDir.resolve("format-coverage.xml")}\n")
                    }.trim()

                notify(
                    project,
                    msg,
                    if (failed == 0) NotificationType.INFORMATION else NotificationType.WARNING,
                )
            }
        }.queue()
    }

    // ============================================================

    private fun buildDeterministicFileReport(
        vf: VirtualFile,
        beforeHash: String?,
        afterHash: String?,
        result: FormatResult,
    ): FileFormatReport {
        val ext = (vf.extension ?: "").lowercase()

        val formatterChosen =
            result.applied.firstOrNull { it.type == FormatStepType.CHOOSE }?.message
                ?: result.applied.firstOrNull { it.type == FormatStepType.AI_RECOMMEND && it.message.contains("Selected") }?.message
                ?: result.applied.firstOrNull { it.type == FormatStepType.RUN_EXTERNAL_FORMATTER }?.message

        val score =
            result.applied
                .lastOrNull { it.type == FormatStepType.SCORE }
                ?.message
                ?.filter { it.isDigit() }
                ?.toIntOrNull()

        val changed =
            beforeHash != null && afterHash != null && beforeHash != afterHash

        val outcome =
            when {
                result.errors.isNotEmpty() -> FormatOutcome.FAILED
                changed -> FormatOutcome.FORMATTED
                else -> FormatOutcome.ALREADY_FORMATTED
            }

        val notes =
            when {
                result.errors.isNotEmpty() -> result.errors.joinToString("; ")
                changed -> "Content changed."
                else -> "No change detected (already formatted)."
            }

        return FileFormatReport(
            filePath = vf.path,
            extension = ext,
            outcome = outcome,
            chosenFormatter = formatterChosen,
            score = score,
            notes = notes,
        )
    }

    private fun reportDir(project: Project): Path {
        val base = project.basePath ?: "."
        return Path.of(base).resolve("target").resolve("humbaba")
    }

    private fun hashFile(path: Path): String? {
        return try {
            if (!Files.exists(path) || Files.isDirectory(path)) return null
            val size = Files.size(path)
            val maxBytes = MAX_HASH_BYTES.coerceAtLeast(0L)

            Files.newInputStream(path).use { input ->
                hashStream(input, if (size > maxBytes) maxBytes else Long.MAX_VALUE)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun hashStream(
        input: InputStream,
        maxBytes: Long,
    ): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        var remaining = maxBytes

        while (remaining > 0) {
            val toRead = if (remaining < buf.size) remaining.toInt() else buf.size
            val n = input.read(buf, 0, toRead)
            if (n <= 0) break
            md.update(buf, 0, n)
            remaining -= n.toLong()
        }

        return md.digest().joinToString("") { "%02x".format(it) }
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

    private companion object {
        // hashing cap keeps action fast; formatting changes always occur early in the file too.
        private const val MAX_HASH_BYTES: Long = 5_000_000

        private val NATIVE_ONLY = setOf("xml", "java", "kt", "kts", "json")
        private val NO_OP_BUT_SUCCESS = setOf("js", "jsx", "ts", "tsx", "css", "cmd", "bat")
    }
}
