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
package io.humbaba.domains.ports

import io.humbaba.domains.model.FormatRequest
import io.humbaba.domains.model.FormatterDefinition
import io.humbaba.domains.model.FormatterRecommendation
import io.humbaba.domains.model.InstallStrategyType

interface FileClassifier {
    fun classify(filePath: String): Pair<String, String?> // extension, languageId?

    fun sample(
        filePath: String,
        maxChars: Int = 2000,
    ): String?
}

interface NativeFormatter {
    /** Return true if formatted successfully. */
    fun tryFormat(filePath: String): Boolean
}

interface AiRecommender {
    fun recommend(request: FormatRequest): FormatterRecommendation?
}

interface FormatterRegistry {
    fun findById(id: String): FormatterDefinition?

    fun findByExtension(extension: String): List<FormatterDefinition>
}

interface SafetyPolicy {
    fun validate(
        def: FormatterDefinition,
        rec: FormatterRecommendation,
    ): ValidationResult

    data class ValidationResult(
        val ok: Boolean,
        val reasons: List<String> = emptyList(),
        val sanitizedArgs: List<String> = emptyList(),
        val sanitizedVersion: String? = null,
    )
}

interface FormatterInstaller {
    fun ensureInstalled(
        def: FormatterDefinition,
        version: String,
        strategy: InstallStrategyType,
    ): InstallResult

    data class InstallResult(
        val ok: Boolean,
        val message: String,
        val toolHome: String? = null,
        val executable: String? = null,
    )
}

interface FormatterRunner {
    fun run(
        def: FormatterDefinition,
        executable: String?,
        args: List<String>,
        filePath: String,
    ): RunResult

    data class RunResult(
        val ok: Boolean,
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
    )
}

interface ConsentStore {
    fun isFormatterTrusted(formatterId: String): Boolean

    fun trustFormatter(formatterId: String)

    fun untrustFormatter(formatterId: String)

    fun trustedFormatters(): Set<String>
}
