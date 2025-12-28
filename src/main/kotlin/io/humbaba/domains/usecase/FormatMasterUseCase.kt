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
package io.humbaba.domains.usecase

import io.humbaba.domains.model.FormatRequest
import io.humbaba.domains.model.FormatResult
import io.humbaba.domains.model.FormatStep
import io.humbaba.domains.model.FormatStepType
import io.humbaba.domains.model.FormatterDefinition
import io.humbaba.domains.model.FormatterRecommendation
import io.humbaba.domains.model.InstallStrategyType
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
    private val ai: AiRecommender,
    private val registry: FormatterRegistry,
    private val installer: FormatterInstaller,
    private val runner: FormatterRunner,
    private val safety: SafetyPolicy,
    private val consent: ConsentStore,
    private val consentPrompter: ConsentPrompter,
) {
    fun execute(requestBase: FormatRequest): FormatResult {
        val steps = mutableListOf<FormatStep>()
        // Only fatal failures go into errors (i.e., nothing managed to format).
        // External tool missing / AI unavailable / external exit!=0 are WARNINGS if native formatting succeeds.
        val errors = mutableListOf<String>()

        steps += FormatStep(FormatStepType.CLASSIFY, "Classifying file…")
        val (ext, langId) = classifier.classify(requestBase.filePath)

        val request =
            requestBase.copy(
                extension = ext,
                languageId = langId,
                sample = requestBase.sample ?: classifier.sample(requestBase.filePath),
            )

        steps += FormatStep(
            FormatStepType.CLASSIFY,
            "Detected extension .$ext" + (langId?.let { " (language=$it)" } ?: ""),
        )

        val extLower = ext.lowercase()

        // 1) Always format these with IDE/native only.
        val nativeOnly = setOf("xml", "java", "kt", "kts", "json")

        // 2) Explicit no-op but report as formatted.
        val noOpButReport = setOf("js", "jsx", "ts", "tsx", "css", "cmd", "bat")

        if (extLower in noOpButReport) {
            steps += FormatStep(
                FormatStepType.NATIVE_FORMAT,
                "Formatting is disabled for .$extLower by policy (no-op, reported as formatted).",
                ok = true,
            )
            steps += FormatStep(FormatStepType.DONE, "Done.", ok = true)
            return FormatResult(applied = steps, output = null, errors = emptyList())
        }

        if (extLower in nativeOnly) {
            steps += FormatStep(FormatStepType.NATIVE_FORMAT, "Formatting with IDE/native formatter…")
            val ok = runCatching { nativeFormatter.tryFormat(request.filePath) }.getOrDefault(false)
            if (ok) {
                steps += FormatStep(FormatStepType.NATIVE_REFORMAT, "Re-running IDE formatter (second pass)…")
                runCatching { nativeFormatter.tryFormat(request.filePath) }
                steps += FormatStep(FormatStepType.DONE, "Done.", ok = true)
                return FormatResult(applied = steps, output = null, errors = emptyList())
            }

            errors += "IDE/native formatter failed."
            steps += FormatStep(FormatStepType.DONE, "Done.", ok = false)
            return FormatResult(applied = steps, output = null, errors = errors)
        }

        // External-first for everything else.
        val external = tryExternal(request, steps)

        if (external.appliedOk) {
            steps += FormatStep(FormatStepType.NATIVE_REFORMAT, "Re-running IDE formatter (second pass)…")
            runCatching { nativeFormatter.tryFormat(request.filePath) }
            steps += FormatStep(FormatStepType.DONE, "Done.", ok = true)
            return FormatResult(applied = steps, output = external.output, errors = emptyList())
        }

        // Native fallback. If this succeeds → overall success, with warnings present in steps (ok=false).
        steps += FormatStep(FormatStepType.NATIVE_FORMAT, "Falling back to IDE/native formatter…")
        val nativeOk = runCatching { nativeFormatter.tryFormat(request.filePath) }.getOrDefault(false)

        return if (nativeOk) {
            steps += FormatStep(FormatStepType.DONE, "Done.", ok = true)
            FormatResult(applied = steps, output = external.output, errors = emptyList())
        } else {
            errors += "No formatter succeeded."
            steps += FormatStep(FormatStepType.DONE, "Done.", ok = false)
            FormatResult(applied = steps, output = external.output, errors = errors)
        }
    }

    private fun tryExternal(
        request: FormatRequest,
        steps: MutableList<FormatStep>,
    ): ExternalAttempt {
        val candidates = registry.findByExtension(request.extension)
        if (candidates.isEmpty()) {
            steps += FormatStep(
                FormatStepType.AI_RECOMMEND,
                "No allow-listed external formatter for .${request.extension}; skipping external path.",
                ok = false,
            )
            return ExternalAttempt(false, null)
        }

        // Prefer stable tools when multiple candidates exist (yaml/yml has both Prettier and yamlfmt).
        val chosen = choosePreferred(request.extension, candidates)

        val plan: FormatterRecommendation? =
            if (request.networkAllowed) {
                steps += FormatStep(FormatStepType.AI_RECOMMEND, "Requesting formatter recommendation…")
                runCatching { ai.recommend(request) }.getOrNull()
            } else {
                null
            }

        val finalPlan =
            plan ?: run {
                val msg =
                    if (request.networkAllowed) {
                        "AI unavailable; using deterministic allow-listed defaults."
                    } else {
                        "Network disabled; using deterministic allow-listed defaults (no AI)."
                    }
                steps += FormatStep(FormatStepType.AI_RECOMMEND, msg, ok = false)

                defaultPlanForFormatterId(chosen.id)?.also {
                    steps += FormatStep(
                        FormatStepType.AI_RECOMMEND,
                        "Using '${chosen.displayName}' with safe defaults (no AI).",
                        ok = true,
                    )
                }
            }

        if (finalPlan == null) {
            steps += FormatStep(
                FormatStepType.AI_RECOMMEND,
                "No safe deterministic default is configured for '${chosen.id}'.",
                ok = false,
            )
            return ExternalAttempt(false, null)
        }

        val validation = safety.validate(chosen, finalPlan)
        if (!validation.ok) {
            val msg = "Safety policy rejected recommendation: " + validation.reasons.joinToString("; ")
            steps += FormatStep(FormatStepType.AI_RECOMMEND, msg, ok = false)
            return ExternalAttempt(false, null)
        }

        if (!request.allowAutoInstall && !consent.isFormatterTrusted(chosen.id)) {
            steps += FormatStep(
                FormatStepType.ENSURE_INSTALLED,
                "External formatter '${chosen.displayName}' requires approval (ask once).",
                ok = false,
            )

            val approved = consentPrompter.askTrustFormatter(chosen.id, chosen.displayName)
            if (!approved) {
                steps += FormatStep(
                    FormatStepType.ENSURE_INSTALLED,
                    "Formatter '${chosen.displayName}' not trusted.",
                    ok = false,
                )
                return ExternalAttempt(false, null)
            }

            consent.trustFormatter(chosen.id)
            steps += FormatStep(FormatStepType.ENSURE_INSTALLED, "Trusted '${chosen.displayName}'.", ok = true)
        }

        steps += FormatStep(FormatStepType.ENSURE_INSTALLED, "Ensuring formatter is installed…")
        val install =
            installer.ensureInstalled(
                chosen,
                validation.sanitizedVersion ?: finalPlan.version,
                finalPlan.installStrategy,
            )

        if (!install.ok) {
            steps += FormatStep(FormatStepType.ENSURE_INSTALLED, install.message, ok = false)
            return ExternalAttempt(false, null)
        }

        steps += FormatStep(FormatStepType.ENSURE_INSTALLED, install.message, ok = true)

        steps += FormatStep(FormatStepType.RUN_EXTERNAL_FORMATTER, "Running ${chosen.displayName} on this file…")
        val run =
            runner.run(
                chosen,
                install.executable,
                validation.sanitizedArgs.ifEmpty { finalPlan.runArgs },
                request.filePath,
            )

        return if (run.ok) {
            steps += FormatStep(FormatStepType.RUN_EXTERNAL_FORMATTER, "External formatter applied.", ok = true)
            ExternalAttempt(true, run.stdout.takeIf { it.isNotBlank() })
        } else {
            steps += FormatStep(
                FormatStepType.RUN_EXTERNAL_FORMATTER,
                "External formatter failed (exit=${run.exitCode}).",
                ok = false,
            )
            ExternalAttempt(false, run.stdout.takeIf { it.isNotBlank() })
        }
    }

    private fun choosePreferred(extension: String, candidates: List<FormatterDefinition>): FormatterDefinition {
        val ext = extension.lowercase()
        val preferredOrder =
            when (ext) {
                "yaml", "yml" -> listOf("yamlfmt", "prettier")
                "py" -> listOf("ruff", "black")
                else -> emptyList()
            }

        for (preferred in preferredOrder) {
            val hit = candidates.firstOrNull { it.id.equals(preferred, ignoreCase = true) }
            if (hit != null) return hit
        }

        // Deterministic fallback.
        return candidates.sortedBy { it.id.lowercase() }.first()
    }

    private fun defaultPlanForFormatterId(id: String): FormatterRecommendation? =
        when (id.lowercase()) {
            "prettier" ->
                FormatterRecommendation(
                    formatterId = "prettier",
                    version = "3.3.3",
                    installStrategy = InstallStrategyType.NPM,
                    runArgs = listOf("--write"),
                    confidence = 1.0,
                    rationale = "deterministic-default",
                    sources = emptyList(),
                )

            "black" ->
                FormatterRecommendation(
                    formatterId = "black",
                    version = "24.8.0",
                    installStrategy = InstallStrategyType.PIP,
                    runArgs = emptyList(),
                    confidence = 1.0,
                    rationale = "deterministic-default",
                    sources = emptyList(),
                )

            "ruff" ->
                FormatterRecommendation(
                    formatterId = "ruff",
                    version = "0.7.0",
                    installStrategy = InstallStrategyType.PIP,
                    runArgs = listOf("format"),
                    confidence = 1.0,
                    rationale = "deterministic-default",
                    sources = emptyList(),
                )

            "gofmt" ->
                FormatterRecommendation(
                    formatterId = "gofmt",
                    version = "stable",
                    installStrategy = InstallStrategyType.GO,
                    runArgs = emptyList(),
                    confidence = 1.0,
                    rationale = "deterministic-default",
                    sources = emptyList(),
                )

            "shfmt" ->
                FormatterRecommendation(
                    formatterId = "shfmt",
                    version = "3.8.0",
                    installStrategy = InstallStrategyType.BINARY,
                    runArgs = listOf("-w"),
                    confidence = 1.0,
                    rationale = "deterministic-default",
                    sources = emptyList(),
                )

            "stylua" ->
                FormatterRecommendation(
                    formatterId = "stylua",
                    version = "0.20.0",
                    installStrategy = InstallStrategyType.BINARY,
                    runArgs = emptyList(),
                    confidence = 1.0,
                    rationale = "deterministic-default",
                    sources = emptyList(),
                )

            "clang-format" ->
                FormatterRecommendation(
                    formatterId = "clang-format",
                    version = "17.0.6",
                    installStrategy = InstallStrategyType.BINARY,
                    runArgs = emptyList(),
                    confidence = 1.0,
                    rationale = "deterministic-default",
                    sources = emptyList(),
                )

            "yamlfmt" ->
                FormatterRecommendation(
                    formatterId = "yamlfmt",
                    version = "0.13.0",
                    installStrategy = InstallStrategyType.GO,
                    runArgs = emptyList(),
                    confidence = 1.0,
                    rationale = "deterministic-default",
                    sources = emptyList(),
                )

            else -> null
        }

    private data class ExternalAttempt(
        val appliedOk: Boolean,
        val output: String?,
    )
}
