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

import java.nio.file.Path
import kotlin.io.path.Path

data class Args(
    val command: String?,
    val targetPath: Path?,
    val projectRoot: Path?,
    val dryRun: Boolean,
    val preview: Boolean,
    val aiEnabled: Boolean,
    val yes: Boolean,
    val showHelp: Boolean,
) {
    companion object {
        fun parse(argv: List<String>): Args {
            var command: String? = null
            var target: Path? = null
            var root: Path? = null
            var dryRun = false
            var preview = false
            var ai = false
            var yes = false
            var help = false

            val it = argv.iterator()
            while (it.hasNext()) {
                val a = it.next()
                when (a) {
                    "-h", "--help" -> help = true
                    "--dry-run" -> dryRun = true
                    "--preview" -> preview = true
                    "--ai" -> ai = true
                    "-y", "--yes" -> yes = true
                    "--root" -> {
                        if (!it.hasNext()) error("--root requires a value")
                        root = Path(it.next())
                    }
                    else -> {
                        if (command == null) {
                            command = a
                        } else if (target == null) {
                            target = Path(a)
                        } else {
                            error("Unexpected argument: $a")
                        }
                    }
                }
            }

            return Args(
                command = command,
                targetPath = target,
                projectRoot = root,
                dryRun = dryRun,
                preview = preview,
                aiEnabled = ai,
                yes = yes,
                showHelp = help,
            )
        }

        fun usage(): String =
            """
            Humbaba CLI

            Usage:
              humbaba format <path> [options]

            Options:
              --root <path>   Project root (defaults to <path> if directory, otherwise parent dir)
              --dry-run       Compute results and coverage without persisting changes
              --preview       Print a basic preview for changed files (first 10)
              --ai            Enable AI fallback (EXPERIMENTAL; may change semantics)
              -y, --yes       Auto-trust allow-listed formatters without prompting
              -h, --help      Show help

            Exit codes:
              0  success
              1  one or more files failed
              2  invalid arguments
              130 canceled (Ctrl+C)
            """.trimIndent()
    }
}
