/*
 * Copyright © 2025-2026 | Humbaba is a formatting orchestrator for polyglot repositories.
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
package io.github.aalsanie.cli

import io.github.aalsanie.domains.model.FileFormatReport
import io.github.aalsanie.domains.model.FormatOutcome
import io.github.aalsanie.domains.model.FormatResult
import io.github.aalsanie.domains.model.FormatStepType
import java.nio.file.Path

object CliReports {
    fun toDeterministicReport(
        file: Path,
        beforeHash: String?,
        afterHash: String?,
        changed: Boolean,
        result: FormatResult,
    ): FileFormatReport {
        val ext = file.extension().lowercase()

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
            filePath = file.toString(),
            extension = ext,
            outcome = outcome,
            chosenFormatter = formatterChosen,
            beforeHash = beforeHash,
            afterHash = afterHash,
            changed = changed,
            notes = notes,
        )
    }

    private fun Path.extension(): String {
        val n = fileName.toString()
        val i = n.lastIndexOf('.')
        if (i <= 0 || i == n.length - 1) return ""
        return n.substring(i + 1)
    }
}
