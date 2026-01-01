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
package io.humbaba.cli

object DiffPrinter {
    /**
     * Basic, dependency-free preview. Not a full diff engine.
     * Prints the first differing line and a small context window.
     */
    fun print(d: DiffPreview, contextLines: Int = 2, maxLinesPerSide: Int = 80) {
        println("\n--- ${d.title} ---")

        val beforeLines = d.before.split('\n')
        val afterLines = d.after.split('\n')

        val max = minOf(beforeLines.size, afterLines.size)
        var firstDiff = -1
        for (i in 0 until max) {
            if (beforeLines[i] != afterLines[i]) {
                firstDiff = i
                break
            }
        }
        if (firstDiff == -1) {
            firstDiff = max // length differs
        }

        val start = (firstDiff - contextLines).coerceAtLeast(0)
        val endBefore = (firstDiff + contextLines + 1).coerceAtMost(beforeLines.size)
        val endAfter = (firstDiff + contextLines + 1).coerceAtMost(afterLines.size)

        println("First difference around line ${firstDiff + 1}")
        println("[Before]")
        beforeLines.subList(start, endBefore).take(maxLinesPerSide).forEachIndexed { i, line ->
            println("${start + i + 1}: $line")
        }
        println("[After]")
        afterLines.subList(start, endAfter).take(maxLinesPerSide).forEachIndexed { i, line ->
            println("${start + i + 1}: $line")
        }
    }
}
