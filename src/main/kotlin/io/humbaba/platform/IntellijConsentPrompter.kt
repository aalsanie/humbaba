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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.wm.WindowManager
import io.humbaba.domains.ports.ConsentPrompter
import java.awt.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class IntellijConsentPrompter(
    private val project: Project,
) : ConsentPrompter {

    override fun askTrustFormatter(formatterId: String, displayName: String): Boolean {
        // If the project is already disposed, never block or show the UI.
        if (project.isDisposed) return false

        // If we're already on EDT, show directly.
        if (ApplicationManager.getApplication().isDispatchThread) {
            return showDialog(displayName)
        }

        // Otherwise, hop to EDT and wait for a response.
        val fut = CompletableFuture<Boolean>()
        ApplicationManager.getApplication().invokeLater(
            {
                if (project.isDisposed) {
                    fut.complete(false)
                    return@invokeLater
                }
                fut.complete(showDialog(displayName))
            },
            ModalityState.any()
        )

        // Wait for user response (keep it bounded).
        return try {
            fut.get(10, TimeUnit.MINUTES)
        } catch (_: Throwable) {
            false
        }
    }

    private fun showDialog(displayName: String): Boolean {
        val parent: Component? = WindowManager.getInstance().suggestParentWindow(project)
        return MessageDialogBuilder.yesNo(
            "Trust external formatter?",
            """
            Humbaba needs your approval to install & run an external formatter:

            • $displayName

            This is allow-listed and will run on the current file only.

            Trust this formatter on this machine?
            """.trimIndent()
        )
            .yesText("Trust")
            .noText("Cancel")
            .ask(parent)
    }
}
