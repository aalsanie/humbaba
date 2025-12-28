package io.humbaba.platform

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.concurrency.annotations.RequiresEdt
import io.humbaba.domains.ports.ConsentPrompter
import java.util.concurrent.atomic.AtomicBoolean

class IntellijConsentPrompter(private val project: Project) : ConsentPrompter {

    override fun askTrustFormatter(formatterId: String, displayName: String): Boolean {
        val msg =
            "Humbaba wants to install and run a formatter:\n\n" +
                    "Download this formatter on this machine?"

        val approved = AtomicBoolean(false)

        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            approved.set(showDialog(msg, formatterId, displayName))
        } else {
            app.invokeAndWait {
                approved.set(showDialog(msg, formatterId, displayName))
            }
        }

        return approved.get()
    }

    @RequiresEdt
    private fun showDialog(msg: String, formatterId: String, displayName: String): Boolean {
        val result =
            Messages.showYesNoDialog(
                project,
                msg,
                "Download Formatter",
                "Trust & Run",
                "Cancel",
                Messages.getInformationIcon(),
            )
        return result == Messages.YES
    }
}
