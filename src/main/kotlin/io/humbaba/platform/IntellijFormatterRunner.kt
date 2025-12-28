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
package io.humbaba.platform

import com.intellij.openapi.diagnostic.Logger
import io.humbaba.domains.model.FormatterDefinition
import io.humbaba.domains.ports.FormatterRunner
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs external formatters in a safe, cross-platform way.
 *
 * Note: installer may encode "node|<scriptPath>" (or similar) into `executable` to support tools
 * that don't expose a reliable .bin shim on Windows. We support that encoding here.
 */
class IntellijFormatterRunner : FormatterRunner {
    private val log = Logger.getInstance(IntellijFormatterRunner::class.java)

    override fun run(
        def: FormatterDefinition,
        executable: String?,
        args: List<String>,
        filePath: String,
    ): FormatterRunner.RunResult {
        val exeSpec = (executable ?: def.id).trim()

        // Support encoded executable like: "node|C:\path\to\script.js"
        val parts = exeSpec.split("|", limit = 2)
        val exe = parts[0]
        val prefixArg = parts.getOrNull(1)?.takeIf { it.isNotBlank() }

        val cmd = mutableListOf<String>()

        // Expand command template and inject args right before {file}.
        // Template tokens come from the allow-listed registry (NOT the AI).
        for (token in def.commandTemplate) {
            when (token) {
                "{exe}" -> {
                    cmd += exe
                    if (prefixArg != null) cmd += prefixArg
                }
                "{file}" -> {
                    cmd += args
                    cmd += filePath
                }
                else -> cmd += token
            }
        }

        // If template did not include {file}, append args + file (best-effort, still safe).
        if (!def.commandTemplate.contains("{file}")) {
            cmd += args
            cmd += filePath
        }

        val cwd = File(File(filePath).parentFile?.absolutePath ?: ".")

        return try {
            val pb = ProcessBuilder(cmd).directory(cwd)
            val p = pb.start()
            val out = p.inputStream.bufferedReader().readText()
            val err = p.errorStream.bufferedReader().readText()
            p.waitFor(10, TimeUnit.MINUTES)
            val code = p.exitValue()
            FormatterRunner.RunResult(ok = code == 0, stdout = out, stderr = err, exitCode = code)
        } catch (t: Throwable) {
            log.warn("Failed to run formatter: $cmd", t)
            FormatterRunner.RunResult(ok = false, stdout = "", stderr = t.message ?: "run error", exitCode = 1)
        }
    }
}
