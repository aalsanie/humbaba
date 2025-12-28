package io.humbaba.reporting

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.humbaba.domains.model.FormatCoverageReport
import java.nio.file.Files
import java.nio.file.Path

object FormatCoverageJsonWriter {
    private val mapper = jacksonObjectMapper()

    fun write(report: FormatCoverageReport, output: Path) {
        Files.writeString(
            output,
            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report),
        )
    }
}
