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
package io.humbaba.runner.adapters

import io.humbaba.domains.model.FormatterDefinition
import io.humbaba.domains.ports.FormatterRunner
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Runs external formatters in a hardened way:
 *  - timeout
 *  - cancellation hook
 *  - bounded stdout/stderr capture
 */
class HardenedProcessFormatterRunner(
    private val timeout: Duration,
    private val log: (String) -> Unit,
    private val isCanceled: () -> Boolean,
) : FormatterRunner {

    override fun run(
        def: FormatterDefinition,
        executable: String?,
        args: List<String>,
        filePath: String,
    ): FormatterRunner.RunResult {
        val exe = executable?.trim().orEmpty()
        if (exe.isEmpty()) {
            return FormatterRunner.RunResult(
                ok = false,
                stdout = "",
                stderr = "Missing executable for formatter '${def.id}'.",
                exitCode = -1,
            )
        }

        val cmd = buildList {
            add(exe)
            addAll(args)
            add(filePath)
        }

        val workingDir = runCatching { Path.of(filePath).parent?.toFile() }.getOrNull()
        val pb = ProcessBuilder(cmd)
        if (workingDir != null) pb.directory(workingDir)
        pb.redirectErrorStream(false)

        val proc = try {
            pb.start()
        } catch (t: Throwable) {
            return FormatterRunner.RunResult(
                ok = false,
                stdout = "",
                stderr = "Failed to start formatter '${def.id}' ($exe): ${t.message}",
                exitCode = -1,
            )
        }

        val pool = Executors.newFixedThreadPool(2)
        try {
            val stdoutF: Future<String> = pool.submit(Callable { readAll(proc.inputStream) })
            val stderrF: Future<String> = pool.submit(Callable { readAll(proc.errorStream) })

            val deadlineNanos = System.nanoTime() + timeout.toNanos()
            var finished = false

            while (!finished) {
                if (isCanceled()) {
                    proc.destroy()
                    proc.destroyForcibly()
                    log("Canceled: ${cmd.joinToString(" ")}")
                    return FormatterRunner.RunResult(
                        ok = false,
                        stdout = safeGet(stdoutF),
                        stderr = safeGet(stderrF) + "\nCanceled.",
                        exitCode = -2,
                    )
                }

                val remaining = deadlineNanos - System.nanoTime()
                if (remaining <= 0) break

                finished = proc.waitFor(minOf(remaining, TimeUnit.MILLISECONDS.toNanos(200)), TimeUnit.NANOSECONDS)
            }

            if (!proc.isAlive) {
                val code = proc.exitValue()
                val out = safeGet(stdoutF)
                val err = safeGet(stderrF)
                return FormatterRunner.RunResult(
                    ok = code == 0,
                    stdout = out,
                    stderr = err,
                    exitCode = code,
                )
            }

            // Timeout
            proc.destroy()
            proc.destroyForcibly()
            log("Timeout after ${timeout.toSeconds()}s: ${cmd.joinToString(" ")}")
            return FormatterRunner.RunResult(
                ok = false,
                stdout = safeGet(stdoutF),
                stderr = safeGet(stderrF) + "\nTimed out after ${timeout.toSeconds()}s.",
                exitCode = -3,
            )
        } finally {
            pool.shutdownNow()
        }
    }

    private fun safeGet(f: Future<String>): String {
        return try {
            f.get(200, TimeUnit.MILLISECONDS)
        } catch (_: Throwable) {
            ""
        }
    }

    private fun readAll(input: InputStream): String {
        val maxChars = 200_000
        BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { br ->
            val sb = StringBuilder()
            var total = 0
            var line: String?
            while (true) {
                line = br.readLine() ?: break
                val toAppend = line + "\n"
                if (total + toAppend.length > maxChars) {
                    sb.append(toAppend, 0, maxChars - total)
                    sb.append("\n…(truncated)…\n")
                    break
                }
                sb.append(toAppend)
                total += toAppend.length
            }
            return sb.toString()
        }
    }
}
