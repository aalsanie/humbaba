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
package io.github.aalsanie.runner.adapters

import io.github.aalsanie.domains.ports.FileClassifier
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

class SimpleFileClassifier : FileClassifier {

    override fun classify(filePath: String): Pair<String, String?> {
        val p = Path.of(filePath)
        val name = p.fileName?.toString().orEmpty()
        val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()

        val languageId =
            when (ext) {
                "kt", "kts" -> "kotlin"
                "java" -> "java"
                "xml" -> "xml"
                "json" -> "json"
                "js" -> "javascript"
                "ts" -> "typescript"
                "tsx" -> "typescript"
                "jsx" -> "javascript"
                "py" -> "python"
                "go" -> "go"
                "sh", "bash" -> "shell"
                "yml", "yaml" -> "yaml"
                "md" -> "markdown"
                else -> null
            }

        return ext to languageId
    }

    override fun sample(filePath: String, maxChars: Int): String? {
        return try {
            val p = Path.of(filePath)
            if (!p.isRegularFile()) return null
            val text = Files.readString(p)
            if (text.length <= maxChars) text else text.substring(0, maxChars)
        } catch (_: Throwable) {
            null
        }
    }
}
