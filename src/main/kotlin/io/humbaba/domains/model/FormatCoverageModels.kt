package io.humbaba.domains.model

enum class FormatOutcome {
    FORMATTED,
    ALREADY_FORMATTED,
    FAILED,
}

data class FileFormatReport(
    val filePath: String,
    val extension: String,
    val outcome: FormatOutcome,
    val chosenFormatter: String?,
    val score: Int?,
    val notes: String?,
)

data class FormatCoverageReport(
    val totalFiles: Int,
    val formattedFiles: Int,
    val coveragePercent: Double,
    val files: List<FileFormatReport>,
)
