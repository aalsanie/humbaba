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
        val errors = mutableListOf<String>()

        // ---- classify ----
        steps += FormatStep(FormatStepType.CLASSIFY, "Classifying file…")
        val (ext, langId) = classifier.classify(requestBase.filePath)
        val request =
            requestBase.copy(
                extension = ext,
                languageId = langId,
                sample = requestBase.sample ?: classifier.sample(requestBase.filePath),
            )

        steps +=
            FormatStep(
                FormatStepType.CLASSIFY,
                "Detected extension .$ext" + (langId?.let { " (language=$it)" } ?: ""),
            )

        // ---- external-first ----
        val external = tryExternal(request, steps, errors)
        if (external.appliedOk) {
            steps += FormatStep(FormatStepType.NATIVE_REFORMAT, "Re-running IDE formatter (second pass)…")
            runCatching { nativeFormatter.tryFormat(request.filePath) }
            steps += FormatStep(FormatStepType.DONE, "Done.", ok = true)
            return FormatResult(steps, external.output, emptyList())
        }

        // ---- native fallback (worst case) ----
        steps += FormatStep(FormatStepType.NATIVE_FORMAT, "Falling back to IDE/native formatter…")
        val nativeOk = runCatching { nativeFormatter.tryFormat(request.filePath) }.getOrDefault(false)

        return if (nativeOk) {
            steps += FormatStep(FormatStepType.DONE, "Done.", ok = errors.isEmpty())
            FormatResult(steps, external.output, errors)
        } else {
            errors += "No formatter succeeded."
            steps += FormatStep(FormatStepType.DONE, "Done.", ok = false)
            FormatResult(steps, external.output, errors)
        }
    }

    private fun tryExternal(
        request: FormatRequest,
        steps: MutableList<FormatStep>,
        errors: MutableList<String>,
    ): ExternalAttempt {
        val candidates = registry.findByExtension(request.extension)
        if (candidates.isEmpty()) {
            steps +=
                FormatStep(
                    FormatStepType.AI_RECOMMEND,
                    "No allow-listed external formatter for .${request.extension}; skipping external path.",
                    ok = false,
                )
            return ExternalAttempt(false, null)
        }

        // Registry order should reflect preference. First is “best default”.
        val chosen = candidates.first()

        // ---- plan: AI first, deterministic fallback if AI unavailable/offline ----
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
                errors += msg
                steps += FormatStep(FormatStepType.AI_RECOMMEND, msg, ok = false)

                defaultPlanForFormatterId(chosen.id)?.also {
                    steps +=
                        FormatStep(
                            FormatStepType.AI_RECOMMEND,
                            "Using '${chosen.displayName}' with safe defaults (no AI).",
                            ok = true,
                        )
                }
            }

        if (finalPlan == null) {
            val msg = "No safe deterministic default is configured for '${chosen.id}'."
            errors += msg
            steps += FormatStep(FormatStepType.AI_RECOMMEND, msg, ok = false)
            return ExternalAttempt(false, null)
        }

        // ---- safety validate recommendation/plan ----
        val validation = safety.validate(chosen, finalPlan)
        if (!validation.ok) {
            val msg = "Safety policy rejected recommendation: " + validation.reasons.joinToString("; ")
            errors += msg
            steps += FormatStep(FormatStepType.AI_RECOMMEND, msg, ok = false)
            return ExternalAttempt(false, null)
        }

        // ---- consent gate (ask-once) ----
        // If auto-install is disabled, require trust or prompt user to trust.
        if (!request.allowAutoInstall && !consent.isFormatterTrusted(chosen.id)) {
            steps +=
                FormatStep(
                    FormatStepType.ENSURE_INSTALLED,
                    "External formatter '${chosen.displayName}' requires approval (ask once).",
                    ok = false,
                )

            val approved = consentPrompter.askTrustFormatter(chosen.id, chosen.displayName)

            if (!approved) {
                val msg = "Formatter '${chosen.displayName}' not trusted."
                errors += msg
                steps += FormatStep(FormatStepType.ENSURE_INSTALLED, msg, ok = false)
                return ExternalAttempt(false, null)
            }

            // Persist trust for next time
            consent.trustFormatter(chosen.id)
            steps +=
                FormatStep(
                    FormatStepType.ENSURE_INSTALLED,
                    "Trusted '${chosen.displayName}'.",
                    ok = true,
                )
        }

        // ---- ensure installed ----
        steps += FormatStep(FormatStepType.ENSURE_INSTALLED, "Ensuring formatter is installed…")
        val install =
            installer.ensureInstalled(
                chosen,
                validation.sanitizedVersion ?: finalPlan.version,
                finalPlan.installStrategy,
            )
        if (!install.ok) {
            errors += install.message
            steps += FormatStep(FormatStepType.ENSURE_INSTALLED, install.message, ok = false)
            return ExternalAttempt(false, null)
        }
        steps += FormatStep(FormatStepType.ENSURE_INSTALLED, install.message, ok = true)

        // ---- run formatter ----
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
            val msg = "External formatter failed (exit=${run.exitCode})."
            errors += msg
            steps += FormatStep(FormatStepType.RUN_EXTERNAL_FORMATTER, msg, ok = false)
            ExternalAttempt(false, run.stdout.takeIf { it.isNotBlank() })
        }
    }

    private fun defaultPlanForFormatterId(id: String): FormatterRecommendation? =
        when (id.lowercase()) {
            // IMPORTANT: For prettier, you almost always want --write
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

            // Black formats in-place by default when file path is provided
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

            // These require pinned binaries (your installer enforces pins)
            "shfmt" ->
                FormatterRecommendation(
                    formatterId = "shfmt",
                    version = "3.8.0",
                    installStrategy = InstallStrategyType.BINARY,
                    runArgs = listOf("-w"), // writes in-place
                    confidence = 1.0,
                    rationale = "deterministic-default",
                    sources = emptyList(),
                )

            "stylua" ->
                FormatterRecommendation(
                    formatterId = "stylua",
                    version = "0.20.0",
                    installStrategy = InstallStrategyType.BINARY,
                    runArgs = emptyList(), // stylua formats file passed
                    confidence = 1.0,
                    rationale = "deterministic-default",
                    sources = emptyList(),
                )

            "clang-format" ->
                FormatterRecommendation(
                    formatterId = "clang-format",
                    version = "17.0.6",
                    installStrategy = InstallStrategyType.BINARY,
                    runArgs = emptyList(), // command template already includes -i
                    confidence = 1.0,
                    rationale = "deterministic-default",
                    sources = emptyList(),
                )

            "yamlfmt" ->
                FormatterRecommendation(
                    formatterId = "yamlfmt",
                    version = "0.13.0",
                    installStrategy = InstallStrategyType.GO,
                    runArgs = emptyList(), // yamlfmt writes in-place by default
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
