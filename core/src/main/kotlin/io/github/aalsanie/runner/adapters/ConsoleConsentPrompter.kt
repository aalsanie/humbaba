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
package io.github.aalsanie.runner.adapters

import io.github.aalsanie.domains.ports.ConsentPrompter

/**
 * Simple interactive consent prompter for CLI usage.
 *
 * In IntelliJ integration you typically won't use this; the UI layer should provide its own prompter.
 */
class ConsoleConsentPrompter(
    private val assumeYes: Boolean = false,
    private val log: (String) -> Unit = {},
) : ConsentPrompter {

    override fun askTrustFormatter(formatterId: String, displayName: String): Boolean {
        if (assumeYes) {
            log("Trusting formatter '$displayName' ($formatterId) because --yes is enabled")
            return true
        }

        log("Formatter '$displayName' ($formatterId) wants to run. Trust it? [y/N]")
        return try {
            val input = readLine()?.trim()?.lowercase().orEmpty()
            input == "y" || input == "yes"
        } catch (_: Throwable) {
            false
        }
    }
}
