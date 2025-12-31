package io.humbaba.cli

import io.humbaba.domains.ports.FileContentWriter
import java.nio.file.Files
import java.nio.file.Path

class CliFileContentWriter : FileContentWriter {
    override fun writeText(filePath: String, newText: String): Boolean {
        return try {
            val p = Path.of(filePath)
            Files.writeString(p, newText)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
