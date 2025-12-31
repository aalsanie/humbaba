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
package io.humbaba.runner

import java.nio.file.Path

object DiffPreview {
    fun preview(path: Path, before: String, after: String): String {
        // Basic, bounded preview: show first differing line context.
        val b = before.lines()
        val a = after.lines()
        val n = minOf(b.size, a.size)
        var i = 0
        while (i < n && b[i] == a[i]) i++

        val start = (i - 2).coerceAtLeast(0)
        val end = (i + 2).coerceAtMost(n - 1)

        val sb = StringBuilder()
        sb.append("---- diff preview: ").append(path).append('\n')
        for (k in start..end) {
            sb.append("B ").append(b.getOrElse(k) { "" }).append('\n')
        }
        sb.append("----").append('\n')
        for (k in start..end) {
            sb.append("A ").append(a.getOrElse(k) { "" }).append('\n')
        }
        sb.append("----").append('\n')
        return sb.toString()
    }
}
