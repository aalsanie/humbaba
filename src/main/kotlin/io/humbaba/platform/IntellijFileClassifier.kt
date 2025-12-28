/*
 * Copyright © 2025-2026 | Humbaba: AI based formatter that uses a heuristic and AI scoring system to format the whole project.
 * Reports back format coverage percentage
 *
 * Author: @aalsanie
 *
 * Plugin: https://plugins.jetbrains.com/plugin/29545-humbaba-formatter
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
package io.humbaba.platform

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import io.humbaba.domains.ports.FileClassifier
import java.nio.charset.Charset

class IntellijFileClassifier(
    private val project: Project,
) : FileClassifier {
    override fun classify(filePath: String): Pair<String, String?> {
        return ReadAction.compute(
            ThrowableComputable<Pair<String, String?>, RuntimeException> {
                val vf =
                    LocalFileSystem.getInstance().findFileByPath(filePath)
                        ?: return@ThrowableComputable extOnly(filePath) to null

                val ext = (vf.extension ?: "").lowercase()
                val psi = PsiManager.getInstance(project).findFile(vf)

                // IntelliJ can report TEXT / TextMate for many file types if the corresponding language plugin isn't installed.
                // For Humbaba's reporting + deterministic defaults, we normalize languageId based on extension in those cases.
                val rawLang = psi?.language?.id
                val normalizedLang = normalizeLanguageId(ext, rawLang)

                ext to normalizedLang
            },
        )
    }

    override fun sample(
        filePath: String,
        maxChars: Int,
    ): String? {
        val vf = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return null
        if (vf.isDirectory || vf.fileType.isBinary) return null

        val bytes = runCatching { vf.contentsToByteArray() }.getOrNull() ?: return null
        val text = runCatching { String(bytes, Charset.defaultCharset()) }.getOrNull() ?: return null
        return text.take(maxChars)
    }

    private fun normalizeLanguageId(
        ext: String,
        raw: String?,
    ): String? {
        if (raw == null) return languageFromExtension(ext)

        val lowered = raw.lowercase()
        val looksGeneric =
            lowered == "text" ||
                lowered == "plaintext" ||
                lowered == "textmate" ||
                lowered == "unknown" ||
                lowered == "generic"

        return if (looksGeneric) languageFromExtension(ext) ?: raw else raw
    }

    private fun languageFromExtension(ext: String): String? =
        when (ext.lowercase()) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "xml" -> "xml"
            "json" -> "json"
            "yaml", "yml" -> "yaml"
            "html", "htm" -> "html"
            "css" -> "css"
            "js", "jsx" -> "javascript"
            "ts", "tsx" -> "typescript"
            "sh", "bash" -> "shell"
            "py" -> "python"
            "go" -> "go"
            "lua" -> "lua"
            "c" -> "c"
            "cc", "cpp", "cxx" -> "cpp"
            "h", "hpp", "hh", "hxx" -> "c/cpp-header"
            "cmd", "bat" -> "batch"
            else -> null
        }

    private fun extOnly(path: String): String {
        val i = path.lastIndexOf('.')
        return if (i >= 0 && i < path.length - 1) path.substring(i + 1).lowercase() else ""
    }
}
