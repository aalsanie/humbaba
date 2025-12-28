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
package io.humbaba.platform.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class FormatMasterConfigurable : Configurable {
    private var ui: FormatMasterSettingsUI? = null

    override fun getDisplayName(): String = "Humbaba Formatter"

    override fun createComponent(): JComponent {
        ui = FormatMasterSettingsUI()
        ui!!.resetFrom(service().settings())
        return ui!!.panel
    }

    override fun isModified(): Boolean = ui?.isModified(service().settings()) ?: false

    override fun apply() {
        ui?.applyTo(service().settings())
    }

    override fun reset() {
        ui?.resetFrom(service().settings())
    }

    override fun disposeUIResources() {
        ui = null
    }

    private fun service(): FormatMasterSettingsService =
        ApplicationManager.getApplication().getService(FormatMasterSettingsService::class.java)
}
