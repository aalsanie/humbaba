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
package io.humbaba.platform

import com.intellij.openapi.diagnostic.Logger
import io.humbaba.domains.model.FormatterDefinition
import io.humbaba.domains.ports.FormatterRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Expands allow-listed command templates. No "smart" injection beyond placeholders.
 *
 * Supports encoded executable: "node|/path/to/script.js"
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

        // Support encoded executable: "node|script"
        val parts = exeSpec.split("|", limit = 2)
        val exe = parts[0]
        val prefixArg = parts.getOrNull(1)?.takeIf { it.isNotBlank() }

        val cmd = mutableListOf<String>()
        for (token in def.commandTemplate) {
            when (token) {
                "{exe}" -> {
                    cmd += exe
                    if (prefixArg != null) cmd += prefixArg
                }
                "{args}" -> cmd += args
                "{file}" -> cmd += filePath
                else -> if (token.isNotBlank()) cmd += token
            }
        }

        val cwd = File(File(filePath).parentFile?.absolutePath ?: ".")

        return try {
            val pb = ProcessBuilder(cmd).directory(cwd)
            val p = pb.start()

            val pool = Executors.newFixedThreadPool(2)
            val stdoutFuture = pool.submit<String> { readStreamCapped(p.inputStream) }
            val stderrFuture = pool.submit<String> { readStreamCapped(p.errorStream) }

            val finished = p.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)
            if (!finished) {
                runCatching { p.destroy() }
                runCatching { p.destroyForcibly() }
                pool.shutdownNow()
                val msg = "timeout after ${TIMEOUT_MINUTES}m"
                log.warn("Formatter timed out: ${cmd.joinToString(" ")}")
                return FormatterRunner.RunResult(ok = false, stdout = "", stderr = msg, exitCode = 124)
            }

            val code = p.exitValue()
            val out = runCatching { stdoutFuture.get(100, TimeUnit.MILLISECONDS) }.getOrDefault("")
            val err = runCatching { stderrFuture.get(100, TimeUnit.MILLISECONDS) }.getOrDefault("")
            pool.shutdownNow()
            if (code != 0) {
                log.warn("Formatter exited with $code: ${cmd.joinToString(" ")}")
            }

            FormatterRunner.RunResult(ok = code == 0, stdout = out, stderr = err, exitCode = code)
        } catch (t: Throwable) {
            log.warn("Failed to run formatter: ${cmd.joinToString(" ")}", t)
            FormatterRunner.RunResult(ok = false, stdout = "", stderr = t.message ?: "run error", exitCode = 1)
        }
    }

    private fun readStreamCapped(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var remaining = MAX_OUTPUT_BYTES
        while (remaining > 0) {
            val toRead = minOf(buf.size.toLong(), remaining).toInt()
            val n = input.read(buf, 0, toRead)
            if (n <= 0) break
            out.write(buf, 0, n)
            remaining -= n.toLong()
        }
        return out.toByteArray().toString(Charsets.UTF_8)
    }

    private companion object {
        private const val TIMEOUT_MINUTES: Long = 10
        private const val MAX_OUTPUT_BYTES: Long = 1_000_000
    }
}
