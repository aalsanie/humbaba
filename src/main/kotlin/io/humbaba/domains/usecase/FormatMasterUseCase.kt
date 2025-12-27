/*
 * Copyright © 2025-2026 | Humbaba is a formatting tool that formats the whole code base using safe strategy.
 *
 * Author: @aalsanie
 *
 * Plugin: TODO: REPLACEME
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
) {
    fun execute(requestBase: FormatRequest): FormatResult {
        val steps = mutableListOf<FormatStep>()
        val errors = mutableListOf<String>()

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

        val external = tryExternal(request, steps, errors)
        if (external.appliedOk) {
            steps += FormatStep(FormatStepType.NATIVE_REFORMAT, "Re-running IDE formatter (second pass)…")
            runCatching { nativeFormatter.tryFormat(request.filePath) }
            steps += FormatStep(FormatStepType.DONE, "Done.", ok = true)
            return FormatResult(steps, external.output, emptyList())
        }

        steps += FormatStep(FormatStepType.NATIVE_FORMAT, "Falling back to IDE/native formatter…")
        val nativeOk = runCatching { nativeFormatter.tryFormat(request.filePath) }.getOrDefault(false)

        return if (nativeOk) {
            steps += FormatStep(FormatStepType.DONE, "Done.", ok = true)
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
        if (candidates.isEmpty()) return ExternalAttempt(false, null)

        val chosen = candidates.first()

        val rec =
            if (request.networkAllowed) {
                steps += FormatStep(FormatStepType.AI_RECOMMEND, "Requesting formatter recommendation…")
                ai.recommend(request)
            } else {
                null
            }

        val plan =
            rec ?: defaultPlanForFormatterId(chosen.id).also {
                steps +=
                    FormatStep(
                        FormatStepType.AI_RECOMMEND,
                        "Using '${chosen.displayName}' with safe defaults (no AI).",
                        ok = true,
                    )
            } ?: return ExternalAttempt(false, null)

        val validation = safety.validate(chosen, plan)
        if (!validation.ok) return ExternalAttempt(false, null)

        if (!request.allowAutoInstall && !consent.isFormatterTrusted(chosen.id)) {
            errors += "Formatter '${chosen.displayName}' not trusted."
            return ExternalAttempt(false, null)
        }

        val install = installer.ensureInstalled(chosen, plan.version, plan.installStrategy)
        if (!install.ok) {
            errors += install.message
            return ExternalAttempt(false, null)
        }

        val run =
            runner.run(
                chosen,
                install.executable,
                plan.runArgs,
                request.filePath,
            )

        return if (run.ok) {
            ExternalAttempt(true, run.stdout)
        } else {
            errors += "External formatter failed."
            ExternalAttempt(false, run.stdout)
        }
    }

    private fun defaultPlanForFormatterId(id: String): FormatterRecommendation? =
        when (id.lowercase()) {
            "prettier" -> FormatterRecommendation(id, "3.3.3", InstallStrategyType.NPM, emptyList(), 1.0, "default", emptyList())
            "black" -> FormatterRecommendation(id, "24.8.0", InstallStrategyType.PIP, emptyList(), 1.0, "default", emptyList())
            "gofmt" -> FormatterRecommendation(id, "stable", InstallStrategyType.GO, emptyList(), 1.0, "default", emptyList())
            "shfmt" -> FormatterRecommendation(id, "3.8.0", InstallStrategyType.BINARY, emptyList(), 1.0, "default", emptyList())
            "stylua" -> FormatterRecommendation(id, "0.20.0", InstallStrategyType.BINARY, emptyList(), 1.0, "default", emptyList())
            else -> null
        }

    private data class ExternalAttempt(
        val appliedOk: Boolean,
        val output: String?,
    )
}
