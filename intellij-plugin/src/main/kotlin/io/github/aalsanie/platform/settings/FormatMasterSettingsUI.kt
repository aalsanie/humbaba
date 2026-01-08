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
package io.github.aalsanie.platform.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JPanel

class FormatMasterSettingsUI {
    val preferNative = JBCheckBox("Prefer IDE/native formatter first", true)
    val allowAutoInstall = JBCheckBox("Allow external formatter auto-install", false)
    val networkAllowed = JBCheckBox("Allow network access (AI + downloads)", true)
    val cacheDir = JBTextField(FormatMasterSettings.defaultCacheDir())

    /**
     * Stored securely via Password Safe (OpenAiKeyStore)
     * We only show a placeholder if a key exists.
     */
    val apiKey = JBPasswordField()

    val model = JBTextField("gpt-5.2-mini")
    val baseUrl = JBTextField("https://api.openai.com")

    val panel: JPanel =
        FormBuilder
            .createFormBuilder()
            .addComponent(preferNative)
            .addComponent(allowAutoInstall)
            .addComponent(networkAllowed)
            .addLabeledComponent("Cache directory:", cacheDir)
            .addLabeledComponent("OpenAI API key (stored securely):", apiKey)
            .addLabeledComponent("OpenAI model:", model)
            .addLabeledComponent("OpenAI base URL:", baseUrl)
            .addComponentFillVertically(JPanel(), 0)
            .panel

    fun resetFrom(s: FormatMasterSettings) {
        preferNative.isSelected = s.preferExistingFormatterFirst
        allowAutoInstall.isSelected = s.allowExternalAutoInstall
        networkAllowed.isSelected = s.networkAllowed
        cacheDir.text = s.cacheDir
        model.text = s.openAiModel
        baseUrl.text = s.openAiBaseUrl
// placeholder
        val hasKey = OpenAiKeyStore.get().isNotBlank()
        apiKey.text = if (hasKey) "********" else ""
    }

    fun isModified(s: FormatMasterSettings): Boolean {
        val keyField = String(apiKey.password).trim()
        val keyChanged = keyField.isNotEmpty() && keyField != "********"

        return preferNative.isSelected != s.preferExistingFormatterFirst ||
            allowAutoInstall.isSelected != s.allowExternalAutoInstall ||
            networkAllowed.isSelected != s.networkAllowed ||
            cacheDir.text.trim() != s.cacheDir ||
            keyChanged ||
            model.text.trim() != s.openAiModel ||
            baseUrl.text.trim() != s.openAiBaseUrl
    }

    fun applyTo(s: FormatMasterSettings) {
        s.preferExistingFormatterFirst = preferNative.isSelected
        s.allowExternalAutoInstall = allowAutoInstall.isSelected
        s.networkAllowed = networkAllowed.isSelected
        s.cacheDir = cacheDir.text.trim()
        s.openAiModel = model.text.trim()
        s.openAiBaseUrl = baseUrl.text.trim()

        val key = String(apiKey.password).trim()
        if (key.isNotEmpty() && key != "********") {
            OpenAiKeyStore.set(key)
            apiKey.text = "********"
        }
    }
}
