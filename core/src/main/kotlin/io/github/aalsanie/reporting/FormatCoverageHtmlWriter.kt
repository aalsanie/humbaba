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
package io.github.aalsanie.reporting

import io.github.aalsanie.domains.model.FormatCoverageReport
import io.github.aalsanie.domains.model.FormatOutcome
import java.nio.file.Files
import java.nio.file.Path

object FormatCoverageHtmlWriter {
    fun write(
        report: FormatCoverageReport,
        output: Path,
    ) {
        val html =
            buildString {
                appendLine("<!doctype html>")
                appendLine("<html><head>")
                appendLine("<meta charset='utf-8'/>")
                appendLine("<title>Humbaba Format Coverage</title>")
                appendLine(
                    """
                    <style>
                      body { font-family: system-ui; padding: 16px; }
                      table { border-collapse: collapse; width: 100%; }
                      th, td { border: 1px solid #ddd; padding: 8px; }
                      th { background: #f4f4f4; }
                      .ok { color: green; }
                      .fail { color: red; }
                    </style>
                    """.trimIndent(),
                )
                appendLine("</head><body>")
                appendLine("<h1>Format Coverage: ${report.coveragePercent}%</h1>")
                appendLine("<p>${report.formattedFiles} / ${report.totalFiles} files formatted</p>")
                appendLine("<table>")
                appendLine(
                    "<tr><th>File</th><th>Ext</th><th>Outcome</th><th>Formatter</th><th>Changed</th><th>BeforeHash</th><th>AfterHash</th><th>Notes</th></tr>",
                )

                report.files.forEach {
                    val cls =
                        if (it.outcome == FormatOutcome.FAILED) "fail" else "ok"

                    appendLine(
                        """
                        <tr class="$cls">
                          <td>${it.filePath}</td>
                          <td>${it.extension}</td>
                          <td>${it.outcome}</td>
                          <td>${it.chosenFormatter ?: "-"}</td>
                          <td>${it.changed}</td>
                          <td>${it.beforeHash ?: "-"}</td>
                          <td>${it.afterHash ?: "-"}</td>
                          <td>${it.notes ?: "-"}</td>
                        </tr>
                        """.trimIndent(),
                    )
                }

                appendLine("</table>")
                appendLine("</body></html>")
            }

        Files.writeString(output, html)
    }
}
