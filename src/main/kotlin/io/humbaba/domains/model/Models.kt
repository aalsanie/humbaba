/*
 * Copyright © 2025-2026 | Humbaba is a safe, deterministic formatting orchestrator for polyglot repositories.
 * Reports back format coverage percentage
 *
 * Author: @aalsanie
 *
 * Plugin: https://plugins.jetbrains.com/plugin/29549-humbaba
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
package io.humbaba.domains.model

data class IdeInfo(
    val productCode: String?,
    val productName: String?,
    val buildNumber: String?,
)

data class OsInfo(
    val name: String,
    val arch: String,
    val version: String,
)

data class FormatRequest(
    val filePath: String,
    val extension: String,
    val languageId: String?,
    val ideInfo: IdeInfo,
    val osInfo: OsInfo,
    val sample: String?,
    val preferExistingFormatterFirst: Boolean,
    val allowAutoInstall: Boolean,
    val networkAllowed: Boolean,
    val aiEnabled: Boolean = false,
    val dryRun: Boolean = false,
)

enum class FormatStepType {
    CLASSIFY,
    NATIVE_FORMAT,
    AI_RECOMMEND,
    ENSURE_INSTALLED,
    RUN_EXTERNAL_FORMATTER,
    SCORE,
    AI_FORMAT,
    CHOOSE,
    DONE,
}

data class FormatStep(
    val type: FormatStepType,
    val message: String,
    val ok: Boolean = true,
)

data class FormatResult(
    val applied: List<FormatStep>,
    val output: String?,
    val errors: List<String>,
)

enum class InstallStrategyType { NPM, PIP, GO, BINARY }

data class FormatterRecommendation(
    val formatterId: String,
    val version: String,
    val installStrategy: InstallStrategyType,
    val runArgs: List<String>,
    val confidence: Double,
    val rationale: String,
    val sources: List<String> = emptyList(),
)

data class FormatterDefinition(
    val id: String,
    val displayName: String,
    val supportedExtensions: Set<String>,
    val installStrategies: Set<InstallStrategyType>,
    val allowedArgs: Set<String>,
    val commandTemplate: List<String>,
)
