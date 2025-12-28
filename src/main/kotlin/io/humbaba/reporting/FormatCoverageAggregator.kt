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
            if (total == 0) 0.0
            else (formatted.toDouble() / total.toDouble()) * 100.0

        return FormatCoverageReport(
            totalFiles = total,
            formattedFiles = formatted,
            coveragePercent = "%.2f".format(coverage).toDouble(),
            files = files,
        )
    }
}
