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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.concurrency.annotations.RequiresEdt
import io.github.aalsanie.domains.ports.ConsentPrompter
import java.util.concurrent.atomic.AtomicBoolean

class IntellijConsentPrompter(
    private val project: Project,
) : ConsentPrompter {
    override fun askTrustFormatter(
        formatterId: String,
        displayName: String,
    ): Boolean {
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
    private fun showDialog(
        msg: String,
        formatterId: String,
        displayName: String,
    ): Boolean {
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
