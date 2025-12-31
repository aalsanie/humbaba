/*
 * Copyright © 2025-2026 | Humbaba is a safe, deterministic formatting orchestrator for polyglot repositories.
 * Reports back format coverage percentage
 *
 * Author: @aalsanie
 *
 * Plugin: https://plugins.jetbrains.com/plugin/29573-humbaba
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
package io.humbaba.runner

import java.nio.file.Path

/** Options that control a formatting run. */
data class RunOptions(
    /** Do not persist changes to files (but still execute formatters and compute reports). */
    val dryRun: Boolean = false,
    /** If a file changed, print a human-readable diff preview to the log. */
    val preview: Boolean = false,
    /** Enable AI features (OpenAI) if configured. */
    val aiEnabled: Boolean = false,
    /** Assume "yes" for trust prompts (dangerous; for CI / local experiments). */
    val yes: Boolean = false,
)

/** Summary of a formatting run plus the path where reports were written. */
data class RunResult(
    val totalFiles: Int,
    val formattedFiles: Int,
    val alreadyFormattedFiles: Int,
    val failedFiles: Int,
    /** Coverage percent in [0.0, 100.0]. */
    val coveragePercent: Double,
    val reportsDir: Path,
)
