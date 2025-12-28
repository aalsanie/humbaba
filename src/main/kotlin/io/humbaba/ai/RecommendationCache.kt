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
package io.humbaba.ai

import io.humbaba.domains.model.FormatterRecommendation
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class RecommendationCache(
    private val ttlSeconds: Long = 3600,
) {
    private data class Entry(
        val rec: FormatterRecommendation,
        val ts: Long,
    )

    private val map = ConcurrentHashMap<String, Entry>()

    fun get(key: String): FormatterRecommendation? {
        val e = map[key] ?: return null
        if (Instant.now().epochSecond - e.ts > ttlSeconds) {
            map.remove(key)
            return null
        }
        return e.rec
    }

    fun put(
        key: String,
        rec: FormatterRecommendation,
    ) {
        map[key] = Entry(rec, Instant.now().epochSecond)
    }
}
