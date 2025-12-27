/*
 * Copyright © 2025-2026 | Humbaba is a formatting tool that formats the whole code base using safe strategy.
 *
 * Author: @aalsanie
 *
 * Plugin: TODO: REPLACEME
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
import java.util.concurrent.ConcurrentHashMap

@Service
@State(name = "FormatMasterConsentStore", storages = [Storage("format-master-consent.xml")])
class IntellijConsentStore :
    PersistentStateComponent<IntellijConsentStore.State>,
    ConsentStore {
    data class State(
        var trusted: MutableMap<String, Boolean> = mutableMapOf(),
    )

    private var state = State()
    private val cache = ConcurrentHashMap<String, Boolean>()

    override fun getState(): State {
        state.trusted.forEach { cache[it.key] = it.value }
        return state
    }

    override fun loadState(state: State) {
        this.state = state
        cache.clear()
        state.trusted.forEach { cache[it.key] = it.value }
    }

    override fun isFormatterTrusted(formatterId: String): Boolean = cache[formatterId] ?: state.trusted[formatterId] ?: false

    override fun markFormatterTrusted(
        formatterId: String,
        trusted: Boolean,
    ) {
        state.trusted[formatterId] = trusted
        cache[formatterId] = trusted
    }
}
