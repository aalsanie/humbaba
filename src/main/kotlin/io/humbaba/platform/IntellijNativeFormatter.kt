/*
 * Copyright © 2025-2026 | Humbaba is a formatting tool that formats the whole code base using safe strategy.
 *
 * Author: @aalsanie
 *
 * Plugin: TODO: REPLACEME
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

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import io.humbaba.domains.ports.NativeFormatter

class IntellijNativeFormatter(
    private val project: Project,
) : NativeFormatter {
    override fun tryFormat(filePath: String): Boolean {
        val vf = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return false
        if (vf.isDirectory || vf.fileType.isBinary || !vf.isWritable) return false

        val psiFile = PsiManager.getInstance(project).findFile(vf) ?: return false
        val doc = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return false

        return WriteCommandAction
            .writeCommandAction(project)
            .compute<Boolean, RuntimeException> {
                PsiDocumentManager.getInstance(project).commitDocument(doc)
                runCatching { CodeStyleManager.getInstance(project).reformat(psiFile) }.isSuccess
            }
    }
}
