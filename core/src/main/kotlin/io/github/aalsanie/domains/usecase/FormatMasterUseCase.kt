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
package io.github.aalsanie.domains.usecase

import io.github.aalsanie.domains.model.FormatRequest
import io.github.aalsanie.domains.model.FormatResult
import io.github.aalsanie.domains.model.FormatStep
import io.github.aalsanie.domains.model.FormatStepType
import io.github.aalsanie.domains.model.FormatterDefinition
import io.github.aalsanie.domains.model.FormatterRecommendation
import io.github.aalsanie.domains.model.InstallStrategyType
import io.github.aalsanie.domains.ports.AiFormatAdvisor
import io.github.aalsanie.domains.ports.AiRecommender
import io.github.aalsanie.domains.ports.ConsentPrompter
import io.github.aalsanie.domains.ports.ConsentStore
import io.github.aalsanie.domains.ports.FileClassifier
import io.github.aalsanie.domains.ports.FileContentWriter
import io.github.aalsanie.domains.ports.FormatterInstaller
import io.github.aalsanie.domains.ports.FormatterRegistry
import io.github.aalsanie.domains.ports.FormatterRunner
import io.github.aalsanie.domains.ports.NativeFormatter
import io.github.aalsanie.domains.ports.SafetyPolicy

class FormatMasterUseCase(
    private val classifier: FileClassifier,
    private val nativeFormatter: NativeFormatter,
    private val fileContentWriter: FileContentWriter,
    private val aiRecommender: AiRecommender,
    private val aiAdvisor: AiFormatAdvisor,
    private val registry: FormatterRegistry,
    private val installer: FormatterInstaller,
    private val runner: FormatterRunner,
    private val safety: SafetyPolicy,
    private val consent: ConsentStore,
    private val consentPrompter: ConsentPrompter,
) {
    fun execute(requestBase: FormatRequest): FormatResult {
        val steps = mutableListOf<FormatStep>()
        val errors = mutableListOf<String>()

        // ---------------- CLASSIFY ----------------

        steps += FormatStep(FormatStepType.CLASSIFY, "Classifying file…")
        val (ext, langId) = classifier.classify(requestBase.filePath)
        val extension = ext.lowercase()

        val request =
            requestBase.copy(
                extension = extension,
                languageId = langId,
                sample = requestBase.sample ?: classifier.sample(requestBase.filePath),
            )

        steps +=
            FormatStep(
                FormatStepType.CLASSIFY,
                "Detected extension .$extension" + (langId?.let { " (language=$it)" } ?: ""),
            )

        // ---------------- POLICIES ----------------

        val nativeOnly = setOf("xml", "java", "kt", "kts")
        val noOpButSuccess = setOf("cmd", "bat")
        val enforceExternalFirst =
            setOf(
                "yaml",
                "yml",
                "c",
                "cc",
                "cpp",
                "cxx",
                "h",
                "hpp",
                "hh",
                "hxx",
                "go",
            )

        // ---------------- NO-OP ----------------

        if (extension in noOpButSuccess) {
            steps +=
                FormatStep(
                    FormatStepType.DONE,
                    "Formatting skipped for .$extension by policy (reported as formatted).",
                    ok = true,
                )
            return FormatResult(steps, null, emptyList())
        }

        // ---------------- NATIVE ONLY ----------------

        if (extension in nativeOnly) {
            steps += FormatStep(FormatStepType.NATIVE_FORMAT, "Formatting using IDE/native formatter…")
            val ok = nativeFormatter.tryFormat(request.filePath)
            if (ok) {
                steps += FormatStep(FormatStepType.DONE, "Done.", ok = true)
                return FormatResult(steps, null, emptyList())
            }

            errors += "IDE/native formatter failed."
            steps += FormatStep(FormatStepType.DONE, "Done.", ok = false)
            return FormatResult(steps, null, errors)
        }

        // ---------------- SNAPSHOT ORIGINAL ----------------

        val original = classifier.sample(request.filePath, maxChars = 200_000) ?: ""

        // ---------------- EXTERNAL FORMAT ----------------

        val externalApplied =
            runExternalFormatter(
                request = request,
                steps = steps,
                forceBest = extension in enforceExternalFirst,
            )

        val afterExternal = classifier.sample(request.filePath, maxChars = 200_000) ?: ""
        val externalChanged = original != afterExternal && afterExternal.isNotBlank()

        if (externalApplied) {
            steps +=
                if (externalChanged) {
                    FormatStep(FormatStepType.DONE, "Formatted using external formatter.", ok = true)
                } else {
                    FormatStep(FormatStepType.DONE, "No change detected (already formatted by external formatter).", ok = true)
                }
            return FormatResult(steps, null, emptyList())
        }

        // ---------------- NATIVE FALLBACK ----------------

        steps +=
            FormatStep(
                FormatStepType.NATIVE_FORMAT,
                "External formatter insufficient; attempting IDE/native formatter…",
                ok = false,
            )

        val nativeOk = nativeFormatter.tryFormat(request.filePath)
        val afterNative = classifier.sample(request.filePath, maxChars = 200_000) ?: ""

        if (nativeOk) {
            steps +=
                FormatStep(
                    FormatStepType.CHOOSE,
                    "Native formatter applied.",
                    ok = true,
                )
            return FormatResult(steps, null, emptyList())
        }

        // ---------------- AI LAST RESORT ----------------

        if (request.networkAllowed && request.aiEnabled) {
            steps +=
                FormatStep(
                    FormatStepType.AI_FORMAT,
                    "Attempting AI formatting as last resort (EXPERIMENTAL; may change semantics)…",
                    ok = true,
                )

            val aiFormatted =
                aiAdvisor.format(extension, langId, original)

            if (!aiFormatted.isNullOrBlank()) {
                if (request.dryRun) {
                    steps +=
                        FormatStep(
                            FormatStepType.DONE,
                            "Dry-run: AI fallback would rewrite the file.",
                            ok = true,
                        )
                    return FormatResult(steps, aiFormatted, emptyList())
                }

                val ok = fileContentWriter.writeText(request.filePath, aiFormatted)
                if (ok) {
                    steps +=
                        FormatStep(
                            FormatStepType.DONE,
                            "Formatted using AI fallback (EXPERIMENTAL).",
                            ok = true,
                        )
                    return FormatResult(steps, null, emptyList())
                }

                errors += "AI fallback produced output but could not be applied via IDE document model."
            }
        }

        // ---------------- FAILURE ----------------

        errors += "File could not be formatted (no formatter succeeded)."
        steps +=
            FormatStep(
                FormatStepType.DONE,
                "Formatting failed (no applicable formatter).",
                ok = false,
            )
        return FormatResult(steps, null, errors)
    }

    private fun runExternalFormatter(
        request: FormatRequest,
        steps: MutableList<FormatStep>,
        forceBest: Boolean,
    ): Boolean {
        val candidates = registry.findByExtension(request.extension)
        if (candidates.isEmpty()) {
            steps +=
                FormatStep(
                    FormatStepType.AI_RECOMMEND,
                    "No external formatter registered.",
                    ok = false,
                )
            return false
        }

        val ordered =
            if (forceBest) {
                val best = chooseBestKnown(request.extension, candidates)
                listOf(best) + candidates.filter { it.id != best.id }
            } else {
                candidates
            }

        for (chosen in ordered) {
            steps +=
                FormatStep(
                    FormatStepType.AI_RECOMMEND,
                    "Selected formatter: ${chosen.displayName}",
                    ok = true,
                )

            val plan = defaultPlanFor(chosen.id, request.extension) ?: continue

            val validation = safety.validate(chosen, plan)
            if (!validation.ok) continue

            if (!request.allowAutoInstall && !consent.isFormatterTrusted(chosen.id)) {
                if (!consentPrompter.askTrustFormatter(chosen.id, chosen.displayName)) continue
                consent.trustFormatter(chosen.id)
            }

            val install =
                installer.ensureInstalled(
                    chosen,
                    validation.sanitizedVersion ?: plan.version,
                    plan.installStrategy,
                )

            if (!install.ok || install.executable == null) continue

            val args = validation.sanitizedArgs.ifEmpty { plan.runArgs }

            steps +=
                FormatStep(
                    FormatStepType.RUN_EXTERNAL_FORMATTER,
                    "Running ${chosen.displayName}…",
                    ok = true,
                )

            val run =
                runner.run(
                    def = chosen,
                    executable = install.executable,
                    args = args,
                    filePath = request.filePath,
                )

            if (run.ok) return true

            val details =
                buildString {
                    append("Formatter ${chosen.displayName} failed (exitCode=${run.exitCode}).")
                    if (run.stderr.isNotBlank()) {
                        append(" stderr=")
                        append(run.stderr.trim().take(400))
                    }
                }

            steps +=
                FormatStep(
                    FormatStepType.RUN_EXTERNAL_FORMATTER,
                    details,
                    ok = false,
                )
        }

        return false
    }

    private fun chooseBestKnown(
        extension: String,
        candidates: List<FormatterDefinition>,
    ): FormatterDefinition {
        val id =
            when (extension) {
                "yaml", "yml" -> "yamlfmt"
                "go" -> "gofmt"
                "c", "cc", "cpp", "cxx", "h", "hpp", "hh", "hxx" -> "clang-format"
                else -> null
            }

        if (id != null) {
            candidates.firstOrNull { it.id.equals(id, ignoreCase = true) }?.let { return it }
        }

        return candidates.first()
    }

    private fun defaultPlanFor(
        formatterId: String,
        extension: String,
    ): FormatterRecommendation? =
        when (formatterId.lowercase()) {
            "yamlfmt" ->
                FormatterRecommendation(
                    formatterId = "yamlfmt",
                    version = "0.13.0",
                    installStrategy = InstallStrategyType.GO,
                    runArgs = listOf("-w"),
                    confidence = 1.0,
                    rationale = "best-known",
                )

            "gofmt" ->
                FormatterRecommendation(
                    formatterId = "gofmt",
                    version = "stable",
                    installStrategy = InstallStrategyType.GO,
                    runArgs = listOf("-w"),
                    confidence = 1.0,
                    rationale = "best-known",
                )

            "clang-format" ->
                FormatterRecommendation(
                    formatterId = "clang-format",
                    version = "17",
                    installStrategy = InstallStrategyType.BINARY,
                    runArgs = listOf("-i"),
                    confidence = 1.0,
                    rationale = "best-known",
                )

            "prettier" -> {
                val args =
                    when (extension) {
                        "html", "htm" -> listOf("--write", "--parser=html")
                        "yaml", "yml" -> listOf("--write", "--parser=yaml")
                        else -> listOf("--write")
                    }

                FormatterRecommendation(
                    formatterId = "prettier",
                    version = "3.3.3",
                    installStrategy = InstallStrategyType.NPM,
                    runArgs = args,
                    confidence = 1.0,
                    rationale = "deterministic",
                )
            }

            else -> null
        }
}
