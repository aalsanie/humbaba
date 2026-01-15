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
package io.github.aalsanie.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.streams.toList

object CliFileCollector {
    private val eligibleExt = setOf(
        // Supported languages & configs
        "java", "kt", "kts", "xml",
        "py",
        "go",
        "c", "cc", "cpp", "cxx", "h", "hpp", "hh", "hxx",
        "sh", "bash",
        "lua",
        "js", "mjs", "cjs", "jsx", "ts", "tsx",
        "json", "jsonc",
        "css", "html", "htm", "md", "mdx",
        "yaml", "yml",
    )

    fun collect(target: Path): List<Path> {
        val root = target.toAbsolutePath().normalize()
        if (!Files.exists(root)) return emptyList()

        if (Files.isRegularFile(root)) {
            val ext = root.extension.lowercase()
            return if (ext in eligibleExt) listOf(root) else emptyList()
        }

        return Files.walk(root)
            .filter { Files.isRegularFile(it) }
            .filter { it.extension.lowercase() in eligibleExt }
            .toList()
            .sorted()
    }
}
