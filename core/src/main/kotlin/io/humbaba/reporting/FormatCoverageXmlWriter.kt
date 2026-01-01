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
package io.humbaba.reporting

import io.humbaba.domains.model.FormatCoverageReport
import java.nio.file.Files
import java.nio.file.Path

object FormatCoverageXmlWriter {
    fun write(
        report: FormatCoverageReport,
        output: Path,
    ) {
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
                    it.beforeHash?.let { h -> appendLine("      <beforeHash>$h</beforeHash>") }
                    it.afterHash?.let { h -> appendLine("      <afterHash>$h</afterHash>") }
                    appendLine("      <changed>${it.changed}</changed>")
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
