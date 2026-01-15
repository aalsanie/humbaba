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
package io.github.aalsanie.runner

import io.github.aalsanie.ai.OpenAiFormatAdvisor
import io.github.aalsanie.ai.OpenAiRecommender
import io.github.aalsanie.ai.OpenAiSettings
import io.github.aalsanie.domains.model.FileFormatReport
import io.github.aalsanie.domains.model.FormatOutcome
import io.github.aalsanie.domains.model.FormatRequest
import io.github.aalsanie.domains.model.FormatResult
import io.github.aalsanie.domains.model.FormatStepType
import io.github.aalsanie.domains.usecase.FormatMasterUseCase
import io.github.aalsanie.formatters.DefaultFormatterRegistry
import io.github.aalsanie.formatters.DefaultSafetyPolicy
import io.github.aalsanie.reporting.FormatCoverageAggregator
import io.github.aalsanie.reporting.FormatCoverageHtmlWriter
import io.github.aalsanie.reporting.FormatCoverageJsonWriter
import io.github.aalsanie.reporting.FormatCoverageXmlWriter
import io.github.aalsanie.runner.adapters.ConsoleConsentPrompter
import io.github.aalsanie.runner.adapters.DiskFileContentWriter
import io.github.aalsanie.runner.adapters.FileConsentStore
import io.github.aalsanie.runner.adapters.HardenedProcessFormatterRunner
import io.github.aalsanie.runner.adapters.NoOpAiFormatAdvisor
import io.github.aalsanie.runner.adapters.NoOpAiRecommender
import io.github.aalsanie.runner.adapters.NoOpFormatterInstaller
import io.github.aalsanie.runner.adapters.NoOpNativeFormatter
import io.github.aalsanie.runner.adapters.SimpleFileClassifier
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration

class HumbabaRunner {

    fun formatAndReport(
        root: Path,
        options: RunOptions,
        log: (String) -> Unit,
        isCanceled: () -> Boolean,
    ): RunResult {
        val registry = DefaultFormatterRegistry()
        val safety = DefaultSafetyPolicy()

        val humbabaDir = root.resolve(".humbaba")
        val reportsDir = humbabaDir.resolve("reports")
        Files.createDirectories(reportsDir)

        // Consents are stored in .humbaba/trusted-formatters.txt
        val consentFile = humbabaDir.resolve("trusted-formatters.txt")
        val consentStore = FileConsentStore(consentFile)
        val prompter = ConsoleConsentPrompter(assumeYes = options.yes, log = log)

        val processRunner = HardenedProcessFormatterRunner(
            timeout = Duration.ofMinutes(10),
            log = log,
            isCanceled = isCanceled,
        )

        val (aiRecommender, aiAdvisor) = buildAi(options, log)

        val useCase =
            FormatMasterUseCase(
                classifier = SimpleFileClassifier(),
                nativeFormatter = NoOpNativeFormatter(),
                fileContentWriter = DiskFileContentWriter(),
                aiRecommender = aiRecommender,
                aiAdvisor = aiAdvisor,
                registry = registry,
                safety = safety,
                installer = NoOpFormatterInstaller(),
                runner = processRunner,
                consent = consentStore,
                consentPrompter = prompter,
            )

        val paths = FileCollector.collect(root)

        val eligible =
            paths.filter { p ->
                val ext = extensionOf(p)
                ext.isNotBlank() &&
                        (
                                registry.findByExtension(ext).isNotEmpty() ||
                                        ext in NATIVE_ONLY ||
                                        ext in NO_OP_BUT_SUCCESS
                                )
            }

        val reports = mutableListOf<FileFormatReport>()

        eligible.forEachIndexed { idx, p ->
            if (isCanceled()) return@forEachIndexed

            log("(${idx + 1}/${eligible.size}) ${p}")

            val beforeText = safeReadText(p)
            val beforeHash = hashFile(p)

            val req =
                FormatRequest(
                    filePath = p.toString(),
                    extension = extensionOf(p),
                    languageId = null,
                    ideInfo = EnvInfo.ideInfo(),
                    osInfo = EnvInfo.osInfo(),
                    sample = null,
                    preferExistingFormatterFirst = true,
                    allowAutoInstall = false,
                    networkAllowed = true,
                    aiEnabled = options.aiEnabled,
                    dryRun = options.dryRun,
                )

            val result = useCase.execute(req)

            val afterText = safeReadText(p)
            val afterHash = hashFile(p)
            val changed = beforeHash != null && afterHash != null && beforeHash != afterHash

            // Dry-run must not leave changes behind.
            if (options.dryRun && changed && beforeText != null) {
                Files.writeString(p, beforeText)
            }

            if (options.preview && changed && beforeText != null && afterText != null) {
                log(DiffPreview.preview(p, beforeText, afterText))
            }

            reports += deterministicReport(p, beforeHash, afterHash, changed, result)
        }

        val coverage = FormatCoverageAggregator.aggregate(reports)

        // Remove any legacy outputs under .humbaba root (keep /reports).
        LegacyReportsCleaner.cleanup(humbabaDir, log)

        val jsonPath = reportsDir.resolve("format-coverage.json")
        val xmlPath = reportsDir.resolve("format-coverage.xml")
        val htmlPath = reportsDir.resolve("format-coverage.html")

        FormatCoverageJsonWriter.write(coverage, jsonPath)
        FormatCoverageXmlWriter.write(coverage, xmlPath)
        FormatCoverageHtmlWriter.write(coverage, htmlPath)

        val formatted = reports.count { it.outcome == FormatOutcome.FORMATTED }
        val already = reports.count { it.outcome == FormatOutcome.ALREADY_FORMATTED }
        val failed = reports.count { it.outcome == FormatOutcome.FAILED }

        log("Done. coverage=${coverage.coveragePercent}% formatted=$formatted already=$already failed=$failed")
        log("Reports: $htmlPath")

        return RunResult(
            totalFiles = coverage.totalFiles,
            formattedFiles = formatted,
            alreadyFormattedFiles = already,
            failedFiles = failed,
            coveragePercent = coverage.coveragePercent,
            reportsDir = reportsDir,
        )
    }

    private fun buildAi(
        options: RunOptions,
        log: (String) -> Unit,
    ): Pair<io.github.aalsanie.domains.ports.AiRecommender, io.github.aalsanie.domains.ports.AiFormatAdvisor> {
        if (!options.aiEnabled) return NoOpAiRecommender() to NoOpAiFormatAdvisor()

        val key = System.getenv("OPENAI_API_KEY")?.trim().orEmpty()
        if (key.isBlank()) {
            log("OPENAI_API_KEY is missing; AI disabled.")
            return NoOpAiRecommender() to NoOpAiFormatAdvisor()
        }

        val model = System.getenv("OPENAI_MODEL")?.trim().takeUnless { it.isNullOrBlank() } ?: "gpt-4o-mini"
        val baseUrl = System.getenv("OPENAI_BASE_URL").trim().takeUnless { it.isBlank() }

        // Provider lambda to match constructors: () -> OpenAiSettings?
        val settings = baseUrl?.let { OpenAiSettings(apiKey = key, model = model, baseUrl = it) }
        val provider: () -> OpenAiSettings? = { settings }

        return OpenAiRecommender(provider) to OpenAiFormatAdvisor(provider)
    }

    private fun deterministicReport(
        path: Path,
        beforeHash: String?,
        afterHash: String?,
        changed: Boolean,
        result: FormatResult,
    ): FileFormatReport {
        val ext = extensionOf(path)

        val formatterChosen =
            result.applied.firstOrNull { it.type == FormatStepType.CHOOSE }?.message
                ?: result.applied.firstOrNull {
                    it.type == FormatStepType.AI_RECOMMEND && it.message.contains("Selected")
                }?.message
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
            filePath = path.toString(),
            extension = ext,
            outcome = outcome,
            chosenFormatter = formatterChosen,
            beforeHash = beforeHash,
            afterHash = afterHash,
            changed = changed,
            notes = notes,
        )
    }

    private fun extensionOf(p: Path): String {
        val name = p.fileName?.toString() ?: return ""
        val i = name.lastIndexOf('.')
        return if (i >= 0 && i < name.length - 1) name.substring(i + 1).lowercase() else ""
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

    private fun hashStream(input: InputStream, maxBytes: Long): String {
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

    private companion object {
        private const val MAX_HASH_BYTES: Long = 5_000_000
        private val NATIVE_ONLY = setOf("xml", "java", "kt", "kts")
        private val NO_OP_BUT_SUCCESS = setOf("cmd", "bat")
    }
}
