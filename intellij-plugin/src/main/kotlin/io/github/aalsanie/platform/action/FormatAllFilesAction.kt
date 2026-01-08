/*
 * Copyright © 2025-2026 | Humbaba is a formatting orchestrator for polyglot repositories.
 * Reports back format coverage percentage
 *
 * Author: @aalsanie
 *
 * 
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
package io.github.aalsanie.platform.action

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import io.github.aalsanie.domains.model.FileFormatReport
import io.github.aalsanie.domains.model.FormatOutcome
import io.github.aalsanie.domains.model.FormatRequest
import io.github.aalsanie.domains.model.FormatResult
import io.github.aalsanie.domains.model.FormatStepType
import io.github.aalsanie.platform.IntellijEnv
import io.github.aalsanie.platform.IntellijFileContentWriter
import io.github.aalsanie.platform.core.UseCaseFactory
import io.github.aalsanie.reporting.FormatCoverageAggregator
import io.github.aalsanie.reporting.FormatCoverageHtmlWriter
import io.github.aalsanie.reporting.FormatCoverageJsonWriter
import io.github.aalsanie.reporting.FormatCoverageXmlWriter
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
 * Reports saved to: <project>/.humbaba/reports/
 */
class FormatAllFilesAction : AnAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val dialog = FormatAllFilesRunDialog(project)
        if (!dialog.showAndGet()) return

        val dryRun = dialog.isDryRun
        val previewDiffs = dialog.isPreviewDiffs
        val aiEnabled = dialog.isAiEnabled

        if (aiEnabled) {
            val ok =
                Messages.showOkCancelDialog(
                    project,
                    "AI formatting is EXPERIMENTAL and may change semantics.\n\n" +
                        "Only enable it if you have backups / version control, and review diffs.\n\n" +
                        "Proceed?",
                    "Enable AI Formatting (Experimental)",
                    "Proceed",
                    "Cancel",
                    null,
                )
            if (ok != Messages.OK) return
        }

        object : Task.Backgroundable(project, "Humbaba: format all files", true) {
            override fun run(indicator: ProgressIndicator) {
                val (useCase, settings, registry) = UseCaseFactory.build(project)
                val writer = IntellijFileContentWriter(project)

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
                val diffs = mutableListOf<DiffPreview>()

                eligible.forEachIndexed { idx, vf ->
                    if (indicator.isCanceled) return

                    indicator.fraction = (idx + 1).toDouble() / eligible.size.toDouble()
                    indicator.text = "Formatting (${idx + 1}/${eligible.size}): ${vf.presentableUrl}"

                    val path = Path.of(vf.path)
                    val beforeText = safeReadText(path)
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
                            aiEnabled = aiEnabled,
                            dryRun = dryRun,
                        )

                    val result = useCase.execute(req)

                    try {
                        vf.refresh(false, false)
                        VirtualFileManager.getInstance().syncRefresh()
                    } catch (_: Throwable) {
                        // ignore
                    }

                    val afterText = safeReadText(path)
                    val afterHash = hashFile(path)

                    val changed = beforeHash != null && afterHash != null && beforeHash != afterHash

                    // Dry-run should not leave any modifications behind.
                    if (dryRun && changed && beforeText != null) {
                        writer.writeText(vf.path, beforeText)
                    }

                    if (previewDiffs && changed && beforeText != null && afterText != null) {
                        diffs += DiffPreview(vf.presentableUrl, beforeText, afterText)
                    }

                    reports += buildDeterministicFileReport(vf, beforeHash, afterHash, changed, result)
                }

                val coverage = FormatCoverageAggregator.aggregate(reports)

                // remove those legacy files so users don't see duplicates or stale data.
                val baseDir = baseDir(project)
                val reportsDir = baseDir.resolve("reports")
                Files.createDirectories(reportsDir)
                cleanupLegacyReports(baseDir)

                val jsonPath = reportsDir.resolve("format-coverage.json")
                val xmlPath = reportsDir.resolve("format-coverage.xml")
                val htmlPath = reportsDir.resolve("format-coverage.html")

                FormatCoverageJsonWriter.write(coverage, jsonPath)
                FormatCoverageXmlWriter.write(coverage, xmlPath)
                FormatCoverageHtmlWriter.write(coverage, htmlPath)

                val already = reports.count { it.outcome == FormatOutcome.ALREADY_FORMATTED }
                val failed = reports.count { it.outcome == FormatOutcome.FAILED }

                val msg =
                    buildString {
                        append("Humbaba Formatter completed.\n")
                        append("• Files processed: ${coverage.totalFiles}\n")
                        append("• Already formatted: $already\n")
                        append("• Failed: $failed\n")
                        append("• Format coverage: ${coverage.coveragePercent}%\n")
                        append("\nReports saved to:\n")
                        append("• $htmlPath\n")
                        append("• $jsonPath\n")
                        append("• $xmlPath\n")
                    }.trim()

                notify(
                    project,
                    msg,
                    if (failed == 0) NotificationType.INFORMATION else NotificationType.WARNING,
                )

                if (previewDiffs && diffs.isNotEmpty()) {
                    ApplicationManager.getApplication().invokeLater {
                        showDiffs(project, diffs)
                    }
                }
            }
        }.queue()
    }

    private fun buildDeterministicFileReport(
        vf: VirtualFile,
        beforeHash: String?,
        afterHash: String?,
        changed: Boolean,
        result: FormatResult,
    ): FileFormatReport {
        val ext = (vf.extension ?: "").lowercase()

        val formatterChosen =
            result.applied.firstOrNull { it.type == FormatStepType.CHOOSE }?.message
                ?: result.applied.firstOrNull { it.type == FormatStepType.AI_RECOMMEND && it.message.contains("Selected") }?.message
                ?: result.applied.firstOrNull { it.type == FormatStepType.RUN_EXTERNAL_FORMATTER }?.message

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
            beforeHash = beforeHash,
            afterHash = afterHash,
            changed = changed,
            notes = notes,
        )
    }

    private data class DiffPreview(
        val title: String,
        val before: String,
        val after: String,
    )

    private fun showDiffs(
        project: Project,
        diffs: List<DiffPreview>,
    ) {
        val max = 10
        val shown = diffs.take(max)
        val factory = DiffContentFactory.getInstance()
        for (d in shown) {
            val request =
                SimpleDiffRequest(
                    "Humbaba Preview: ${d.title}",
                    factory.create(d.before),
                    factory.create(d.after),
                    "Before",
                    "After",
                )
            DiffManager.getInstance().showDiff(project, request)
        }
    }

    /**
     * Reports are tooling artifacts, not build artifacts:
     * <projectRoot>/.humbaba/reports/
     *
     * This creates the directory if it doesn't exist.
     */
    private fun baseDir(project: Project): Path {
        val base = project.basePath ?: "."
        return Path.of(base).resolve(".humbaba")
    }

    /**
     * Legacy cleanup: older versions used to write reports directly under <project>/.humbaba
     * (instead of <project>/.humbaba/reports). Those files are misleading and can appear as
     * "duplicate" exports.
     */
    private fun cleanupLegacyReports(baseDir: Path) {
        try {
            if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) return

            val legacyNames =
                listOf(
                    "format-coverage.json",
                    "format-coverage.xml",
                    "format-coverage.html",
                    // allow for older naming variants if they existed
                    "format_coverage.json",
                    "format_coverage.xml",
                    "format_coverage.html",
                )

            for (name in legacyNames) {
                try {
                    Files.deleteIfExists(baseDir.resolve(name))
                } catch (_: Throwable) {
                    // ignore per-file failures
                }
            }
        } catch (_: Throwable) {
            // ignore
        }
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

    private fun safeReadText(path: Path): String? {
        return try {
            if (!Files.exists(path) || Files.isDirectory(path)) return null
            val bytes = Files.readAllBytes(path)
            val max = 200_000
            val slice = if (bytes.size > max) bytes.copyOfRange(0, max) else bytes
            slice.toString(Charsets.UTF_8)
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
        private const val MAX_HASH_BYTES: Long = 5_000_000
        private val NATIVE_ONLY = setOf("xml", "java", "kt", "kts", "json")
        private val NO_OP_BUT_SUCCESS = setOf("js", "jsx", "ts", "tsx", "css", "cmd", "bat")
    }
}
