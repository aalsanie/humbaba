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
package io.humbaba.reporting

import io.humbaba.domains.model.FileFormatReport
import io.humbaba.domains.model.FormatCoverageReport
import io.humbaba.domains.model.FormatOutcome

object FormatCoverageAggregator {
    fun aggregate(files: List<FileFormatReport>): FormatCoverageReport {
        val total = files.size
        val formatted =
            files.count {
                it.outcome == FormatOutcome.FORMATTED ||
                    it.outcome == FormatOutcome.ALREADY_FORMATTED
            }

        val coverage =
            if (total == 0) {
                0.0
            } else {
                (formatted.toDouble() / total.toDouble()) * 100.0
            }

        return FormatCoverageReport(
            totalFiles = total,
            formattedFiles = formatted,
            coveragePercent = "%.2f".format(coverage).toDouble(),
            files = files,
        )
    }
}
