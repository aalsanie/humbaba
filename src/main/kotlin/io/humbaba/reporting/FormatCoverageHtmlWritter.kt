package io.humbaba.reporting

import io.humbaba.domains.model.*
import java.nio.file.Files
import java.nio.file.Path

object FormatCoverageHtmlWriter {

    fun write(report: FormatCoverageReport, output: Path) {
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
                    "<tr><th>File</th><th>Ext</th><th>Outcome</th><th>Formatter</th><th>Score</th><th>Notes</th></tr>",
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
                          <td>${it.score ?: "-"}</td>
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
