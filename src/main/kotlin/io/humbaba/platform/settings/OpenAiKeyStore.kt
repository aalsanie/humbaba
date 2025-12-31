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
package io.humbaba.platform.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe

object OpenAiKeyStore {
    private const val SERVICE_NAME = "HumbabaFormatter/OpenAI"

    private fun attrs(): CredentialAttributes = CredentialAttributes(SERVICE_NAME)

    fun get(): String {
        val creds: Credentials? = PasswordSafe.instance.get(attrs())
        return creds?.getPasswordAsString().orEmpty()
    }

    fun set(apiKey: String) {
        val trimmed = apiKey.trim()
        val creds = Credentials("openai", trimmed)
        PasswordSafe.instance.set(attrs(), creds)
    }

    fun clear() {
        PasswordSafe.instance.set(attrs(), null)
    }
}
