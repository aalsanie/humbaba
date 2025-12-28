/*
 * Copyright © 2025-2026 | Humbaba is a formatting tool that formats the whole code base using safe strategy.
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

import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import io.humbaba.domains.ports.FileClassifier
import java.nio.charset.Charset

class IntellijFileClassifier(
    private val project: Project,
) : FileClassifier {
    override fun classify(filePath: String): Pair<String, String?> {
        return ReadAction.compute(ThrowableComputable {
            val vf = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return@ThrowableComputable extOnly(filePath) to null
            val ext = (vf.extension ?: "").lowercase()
            val psi = PsiManager.getInstance(project).findFile(vf)
            val lang = psi?.language?.id
            ext to lang
        })
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

    private fun extOnly(path: String): String {
        val i = path.lastIndexOf('.')
        return if (i >= 0 && i < path.length - 1) path.substring(i + 1).lowercase() else ""
    }
}
