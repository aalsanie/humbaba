package io.humbaba.platform

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import io.humbaba.domains.ports.FileClassifier
import java.nio.charset.Charset

class IntellijFileClassifier(private val project: Project) : FileClassifier {

    override fun classify(filePath: String): Pair<String, String?> {
        return ReadAction.compute(ThrowableComputable<Pair<String, String?>, RuntimeException> {
            val vf = LocalFileSystem.getInstance().findFileByPath(filePath)
                ?: return@ThrowableComputable extOnly(filePath) to null

            val ext = (vf.extension ?: "").lowercase()
            val psi = PsiManager.getInstance(project).findFile(vf)
            val lang = psi?.language?.id
            ext to lang
        })
    }

    override fun sample(filePath: String, maxChars: Int): String? {
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
