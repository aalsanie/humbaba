/*
 * Copyright © 2025-2026 | Humbaba is a safe, deterministic formatting orchestrator for polyglot repositories.
 * Reports back format coverage percentage
 *
 * Author: @aalsanie
 *
 * Plugin: https://plugins.jetbrains.com/plugin/29549-humbaba
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
package io.humbaba.platform.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import javax.swing.JScrollPane
import javax.swing.JTextArea

class FormatMasterToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        val area =
            JTextArea().apply {
                isEditable = false
                text = """Humbaba Formatter

Right-click inside an editor:
  Humbaba Formatter: Format This File

Settings:
  Settings → Tools → Humbaba Formatter
"""
            }
        val content = ContentFactory.getInstance().createContent(JScrollPane(area), "Overview", false)
        toolWindow.contentManager.addContent(content)
    }
}
