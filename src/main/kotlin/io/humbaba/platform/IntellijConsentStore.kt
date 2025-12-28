/*
 * Copyright © 2025-2026 | Humbaba: AI based formatter that uses a heuristic and AI scoring system to format the whole project.
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
package io.humbaba.platform

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import io.humbaba.domains.ports.ConsentStore
import java.util.Locale

@Service(Service.Level.APP)
@State(
    name = "HumbabaConsentStore",
    storages = [Storage("humbaba-consent.xml")],
)
class IntellijConsentStore :
    ConsentStore,
    PersistentStateComponent<IntellijConsentStore.State> {
    data class State(
        var trustedFormatterIds: MutableSet<String> = mutableSetOf(),
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        // defensive copy (avoid sharing mutable reference)
        this.state = State(trustedFormatterIds = state.trustedFormatterIds.toMutableSet())
    }

    override fun isFormatterTrusted(formatterId: String): Boolean {
        val key = normalize(formatterId)
        return state.trustedFormatterIds.any { normalize(it) == key }
    }

    override fun trustFormatter(formatterId: String) {
        state.trustedFormatterIds.add(normalize(formatterId))
    }

    override fun untrustFormatter(formatterId: String) {
        val key = normalize(formatterId)
        state.trustedFormatterIds.removeIf { normalize(it) == key }
    }

    override fun trustedFormatters(): Set<String> = state.trustedFormatterIds.toSet()

    private fun normalize(id: String): String = id.trim().lowercase(Locale.ROOT)
}
