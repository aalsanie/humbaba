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
package io.github.aalsanie.platform

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import io.github.aalsanie.domains.ports.FileContentWriter

/**
 * Applies text changes via the IDE document model to avoid desync between PSI, document, and VFS.
 */
class IntellijFileContentWriter(
    private val project: Project,
) : FileContentWriter {
    override fun writeText(
        filePath: String,
        newText: String,
    ): Boolean {
        val vf = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return false
        if (vf.isDirectory || vf.fileType.isBinary || !vf.isWritable) return false

        val psiFile =
            ReadAction.compute(
                ThrowableComputable {
                    PsiManager.getInstance(project).findFile(vf)
                },
            ) ?: return false

        val doc =
            ReadAction.compute(
                ThrowableComputable {
                    PsiDocumentManager.getInstance(project).getDocument(psiFile)
                },
            ) ?: return false

        return WriteCommandAction.runWriteCommandAction(
            project,
            ThrowableComputable<Boolean, RuntimeException> {
                doc.setText(newText)
                val pdm = PsiDocumentManager.getInstance(project)
                pdm.commitDocument(doc)
                pdm.doPostponedOperationsAndUnblockDocument(doc)
                FileDocumentManager.getInstance().saveDocument(doc)
                true
            },
        )
    }
}
