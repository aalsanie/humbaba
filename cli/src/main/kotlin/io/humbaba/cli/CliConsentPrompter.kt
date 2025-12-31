package io.humbaba.cli
/*
 * Copyright © 2025-2026 | Humbaba is a safe, deterministic formatting orchestrator for polyglot repositories.
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
import io.humbaba.domains.ports.ConsentPrompter


class CliConsentPrompter(private val assumeYes: Boolean) : ConsentPrompter {
    override fun askTrustFormatter(formatterId: String, displayName: String): Boolean {
        if (assumeYes) return true

        print("Trust formatter '$displayName' (id=$formatterId) for this repo? [y/N]: ")
        val ans = readLine()?.trim()?.lowercase()
        return ans == "y" || ans == "yes"
    }
}
