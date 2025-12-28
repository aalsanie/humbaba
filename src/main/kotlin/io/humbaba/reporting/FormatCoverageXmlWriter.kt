package io.humbaba.reporting

import io.humbaba.domains.model.*
import java.nio.file.Files
import java.nio.file.Path

object FormatCoverageXmlWriter {

    fun write(report: FormatCoverageReport, output: Path) {
        val xml =
            buildString {
                appendLine("""<formatCoverage percent="${report.coveragePercent}">""")
                appendLine("  <summary>")
                appendLine("    <total>${report.totalFiles}</total>")
                appendLine("    <formatted>${report.formattedFiles}</formatted>")
                appendLine("  </summary>")
                appendLine("  <files>")

                report.files.forEach {
                    appendLine(
                        """    <file path="${it.filePath}" extension="${it.extension}" outcome="${it.outcome}">""",
                    )
                    it.chosenFormatter?.let { f ->
                        appendLine("      <formatter>$f</formatter>")
                    }
                    it.score?.let { s ->
                        appendLine("      <score>$s</score>")
                    }
                    it.notes?.let { n ->
                        appendLine("      <notes>${escape(n)}</notes>")
                    }
                    appendLine("    </file>")
                }

                appendLine("  </files>")
                appendLine("</formatCoverage>")
            }

        Files.writeString(output, xml)
    }

    private fun escape(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
