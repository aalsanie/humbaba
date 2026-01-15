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
package io.github.aalsanie.cli

import io.github.aalsanie.domains.ports.FileClassifier
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

class CliFileClassifier : FileClassifier {
    override fun classify(filePath: String): Pair<String, String?> {
        val p = Path.of(filePath)
        val ext = p.extension.lowercase()
        if (ext.isNotBlank()) return ext to null

        // Extensionless executable scripts (e.g. `./script`) are common; infer from shebang.
        val inferred = detectShebangExtension(p)
        return (inferred ?: ext) to null
    }

    private fun detectShebangExtension(p: Path): String? {
        return try {
            if (!Files.exists(p) || Files.isDirectory(p)) return null
            val firstLine = Files.newBufferedReader(p).use { it.readLine() } ?: return null
            if (!firstLine.startsWith("#!")) return null

            val shebang = firstLine.lowercase()
            when {
                shebang.contains("bash") || shebang.contains("/sh") -> "sh"
                shebang.contains("python") -> "py"
                shebang.contains("node") -> "js"
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    override fun sample(filePath: String, maxChars: Int): String? {
        return try {
            val p = Path.of(filePath)
            if (!Files.exists(p) || Files.isDirectory(p)) return null
            val text = Files.readString(p)
            if (text.length <= maxChars) text else text.substring(0, maxChars)
        } catch (_: Throwable) {
            null
        }
    }
}
