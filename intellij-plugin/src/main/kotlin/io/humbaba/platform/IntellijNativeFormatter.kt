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
package io.humbaba.platform

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import io.humbaba.domains.ports.NativeFormatter

class IntellijNativeFormatter(
    private val project: Project,
) : NativeFormatter {
    override fun tryFormat(filePath: String): Boolean {
        val vf = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return false
        if (vf.isDirectory || vf.fileType.isBinary || !vf.isWritable) return false

        // PSI access must be in a read action
        val psiFile: PsiFile =
            ReadAction.compute(
                ThrowableComputable<PsiFile?, RuntimeException> {
                    PsiManager.getInstance(project).findFile(vf)
                },
            ) ?: return false

        val document =
            ReadAction.compute(
                ThrowableComputable {
                    PsiDocumentManager.getInstance(project).getDocument(psiFile)
                },
            ) ?: return false

        return WriteCommandAction.runWriteCommandAction(
            project,
            ThrowableComputable<Boolean, RuntimeException> {
                val pdm = PsiDocumentManager.getInstance(project)
                pdm.commitDocument(document)
                runCatching { CodeStyleManager.getInstance(project).reformat(psiFile) }.isSuccess
            },
        )
    }
}
