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

import com.intellij.openapi.diagnostic.Logger
import io.humbaba.domains.model.FormatterDefinition
import io.humbaba.domains.ports.FormatterRunner
import java.io.File
import java.util.concurrent.TimeUnit

class IntellijFormatterRunner : FormatterRunner {
    private val log = Logger.getInstance(IntellijFormatterRunner::class.java)

    override fun run(
        def: FormatterDefinition,
        executable: String?,
        args: List<String>,
        filePath: String,
    ): FormatterRunner.RunResult {
        // executable may be:
        //  - null -> use def.id
        //  - "C:\path\prettier.cmd"
        //  - "C:\path\node.exe|C:\path\prettier.cjs"  (node + script)
        val exeSpec = (executable ?: def.id).trim()

        val (exe, prefixArgs) = parseExeSpec(exeSpec)

        // Build the final command.
        // Prefer def.commandTemplate if present, otherwise: exe + prefixArgs + args + filePath
        val finalCmd: List<String> =
            if (def.commandTemplate.isNotEmpty()) {
                // Template supports:
                //  {exe} -> exe
                //  {file} -> filePath
                //  {args} -> expanded args (zero or more tokens)
                // plus optional prefixArgs injected immediately after {exe} if {exe} exists
                expandTemplate(def.commandTemplate, exe, prefixArgs, args, filePath)
            } else {
                buildList {
                    add(exe)
                    addAll(prefixArgs)
                    addAll(args)
                    add(filePath)
                }
            }

        val cwd = File(File(filePath).parentFile?.absolutePath ?: ".")

        return try {
            val pb = ProcessBuilder(finalCmd).directory(cwd)
            val p = pb.start()

            val out = p.inputStream.bufferedReader().readText()
            val err = p.errorStream.bufferedReader().readText()

            p.waitFor(10, TimeUnit.MINUTES)
            val code = p.exitValue()

            FormatterRunner.RunResult(
                ok = code == 0,
                stdout = out,
                stderr = err,
                exitCode = code,
            )
        } catch (t: Throwable) {
            log.warn("Failed to run formatter: $finalCmd", t)
            FormatterRunner.RunResult(
                ok = false,
                stdout = "",
                stderr = t.message ?: "run error",
                exitCode = 1,
            )
        }
    }

    private fun parseExeSpec(exeSpec: String): Pair<String, List<String>> {
        // "node.exe|script.cjs" => exe=node.exe, prefixArgs=[script.cjs]
        val parts = exeSpec.split("|", limit = 2)
        val exe = parts[0].trim()
        val prefixArgs = if (parts.size == 2 && parts[1].isNotBlank()) listOf(parts[1].trim()) else emptyList()
        return exe to prefixArgs
    }

    private fun expandTemplate(
        template: List<String>,
        exe: String,
        prefixArgs: List<String>,
        args: List<String>,
        filePath: String,
    ): List<String> {
        val out = mutableListOf<String>()

        for (token in template) {
            when (token) {
                "{exe}" -> {
                    out += exe
                    // Inject prefix args immediately after exe (e.g., node + script)
                    out += prefixArgs
                }
                "{file}" -> out += filePath
                "{args}" -> out += args
                else -> out += token
            }
        }

        // If the template didn't include {args}, append args + file at end (safe default)
        if (!template.contains("{args}")) out += args
        if (!template.contains("{file}")) out += filePath

        return out
    }
}
