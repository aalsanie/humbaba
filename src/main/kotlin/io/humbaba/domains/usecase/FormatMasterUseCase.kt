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
package io.humbaba.domains.usecase

import io.humbaba.domains.model.FormatRequest
import io.humbaba.domains.model.FormatResult
import io.humbaba.domains.model.FormatStep
import io.humbaba.domains.model.FormatStepType
import io.humbaba.domains.model.FormatterDefinition
import io.humbaba.domains.model.FormatterRecommendation
import io.humbaba.domains.model.InstallStrategyType
import io.humbaba.domains.ports.AiFormatAdvisor
import io.humbaba.domains.ports.AiRecommender
import io.humbaba.domains.ports.ConsentPrompter
import io.humbaba.domains.ports.ConsentStore
import io.humbaba.domains.ports.FileClassifier
import io.humbaba.domains.ports.FormatterInstaller
import io.humbaba.domains.ports.FormatterRegistry
import io.humbaba.domains.ports.FormatterRunner
import io.humbaba.domains.ports.NativeFormatter
import io.humbaba.domains.ports.SafetyPolicy

class FormatMasterUseCase(
    private val classifier: FileClassifier,
    private val nativeFormatter: NativeFormatter,
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

        val nativeOnly = setOf("xml", "java", "kt", "kts", "json")
        val noOpButSuccess = setOf("js", "jsx", "ts", "tsx", "css", "cmd", "bat")
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

        val externalScore =
            if (externalChanged) {
                aiAdvisor.score(extension, langId, original, afterExternal)
            } else {
                null
            }

        steps +=
            FormatStep(
                FormatStepType.SCORE,
                "External formatter score: ${externalScore ?: "unavailable"}",
                ok = (externalScore ?: 0) >= 90,
            )

        if (externalChanged && (externalScore ?: 0) >= 90) {
            steps += FormatStep(FormatStepType.DONE, "Formatted using external formatter.", ok = true)
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
        val nativeChanged = original != afterNative && afterNative.isNotBlank()

        val nativeScore =
            if (nativeChanged) {
                aiAdvisor.score(extension, langId, original, afterNative)
            } else {
                null
            }

        steps +=
            FormatStep(
                FormatStepType.SCORE,
                "Native formatter score: ${nativeScore ?: "unavailable"}",
                ok = (nativeScore ?: 0) >= 80,
            )

        // ---------------- CHOOSE BEST ----------------

        val externalWins = (externalScore ?: 0) >= (nativeScore ?: 0)

        when {
            externalWins && externalChanged && (externalScore ?: 0) >= 80 -> {
                steps +=
                    FormatStep(
                        FormatStepType.CHOOSE,
                        "External formatter chosen.",
                        ok = true,
                    )
                return FormatResult(steps, null, emptyList())
            }

            nativeChanged && (nativeScore ?: 0) >= 80 -> {
                steps +=
                    FormatStep(
                        FormatStepType.CHOOSE,
                        "Native formatter chosen.",
                        ok = true,
                    )
                return FormatResult(steps, null, emptyList())
            }
        }

        // ---------------- AI LAST RESORT ----------------

        if (request.networkAllowed) {
            steps +=
                FormatStep(
                    FormatStepType.AI_FORMAT,
                    "Attempting AI formatting as last resort…",
                    ok = true,
                )

            val aiFormatted =
                aiAdvisor.format(extension, langId, original)

            if (!aiFormatted.isNullOrBlank()) {
                val aiScore = aiAdvisor.score(extension, langId, original, aiFormatted)
                steps +=
                    FormatStep(
                        FormatStepType.SCORE,
                        "AI formatter score: ${aiScore ?: "unavailable"}",
                        ok = (aiScore ?: 0) >= 70,
                    )

                if ((aiScore ?: 0) >= 70) {
                    // Replace file content
                    java.nio.file.Files.writeString(
                        java.nio.file.Path
                            .of(request.filePath),
                        aiFormatted,
                    )

                    steps +=
                        FormatStep(
                            FormatStepType.DONE,
                            "Formatted using AI fallback.",
                            ok = true,
                        )
                    return FormatResult(steps, null, emptyList())
                }
            }
        }

        // ---------------- FAILURE ----------------

        errors += "File could not be formatted with sufficient confidence."
        steps +=
            FormatStep(
                FormatStepType.DONE,
                "Formatting failed (score below acceptable threshold).",
                ok = false,
            )
        return FormatResult(steps, null, errors)
    }

    // TODO: move this out

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

        val chosen =
            if (forceBest) {
                chooseBestKnown(request.extension, candidates)
            } else {
                candidates.first()
            }

        steps +=
            FormatStep(
                FormatStepType.AI_RECOMMEND,
                "Selected formatter: ${chosen.displayName}",
            )

        val plan = defaultPlanFor(chosen.id, request.extension) ?: return false

        val validation = safety.validate(chosen, plan)
        if (!validation.ok) return false

        if (!request.allowAutoInstall && !consent.isFormatterTrusted(chosen.id)) {
            if (!consentPrompter.askTrustFormatter(chosen.id, chosen.displayName)) return false
            consent.trustFormatter(chosen.id)
        }

        val install =
            installer.ensureInstalled(
                chosen,
                validation.sanitizedVersion ?: plan.version,
                plan.installStrategy,
            )

        if (!install.ok || install.executable == null) return false

        val args = validation.sanitizedArgs.ifEmpty { plan.runArgs }

        val run =
            runner.run(
                def = chosen,
                executable = install.executable,
                args = args,
                filePath = request.filePath,
            )

        return run.ok
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
