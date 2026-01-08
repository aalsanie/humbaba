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

import io.github.aalsanie.domains.model.FormatterDefinition
import io.github.aalsanie.domains.ports.FormatterRunner
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CliFormatterRunner(
    private val cancel: io.github.aalsanie.cli.CancellationFlag,
    private val timeout: Duration = Duration.ofMinutes(5),
) : FormatterRunner {

    override fun run(
        def: FormatterDefinition,
        executable: String?,
        args: List<String>,
        filePath: String,
    ): FormatterRunner.RunResult {
        val exe = executable ?: return FormatterRunner.RunResult(false, "", "Missing executable", -1)

        val cmd = buildFromTemplate(def.commandTemplate, exe, args, filePath)

        val process = try {
            ProcessBuilder(cmd)
                .redirectErrorStream(false)
                .start()
        } catch (t: Throwable) {
            return FormatterRunner.RunResult(false, "", "Failed to start process: ${t.message}", -1)
        }

        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val latch = CountDownLatch(2)

        Thread {
            gobble(process.inputStream, out)
            latch.countDown()
        }.apply {
            isDaemon = true
            start()
        }

        Thread {
            gobble(process.errorStream, err)
            latch.countDown()
        }.apply {
            isDaemon = true
            start()
        }

        val deadlineNanos = System.nanoTime() + timeout.toNanos()

        while (process.isAlive) {
            if (cancel.isCanceled()) {
                destroyProcess(process)
                break
            }
            if (System.nanoTime() >= deadlineNanos) {
                destroyProcess(process)
                break
            }
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                // ignore
            }
        }

        val finished = try {
            process.waitFor(200, TimeUnit.MILLISECONDS)
        } catch (_: Throwable) {
            false
        }

        latch.await(500, TimeUnit.MILLISECONDS)

        val stdout = out.toString(Charset.defaultCharset())
        val stderr = err.toString(Charset.defaultCharset())
        val exitCode = if (finished) safeExitValue(process) else -1

        val ok = finished && exitCode == 0 && !cancel.isCanceled()
        return FormatterRunner.RunResult(ok = ok, stdout = stdout, stderr = stderr, exitCode = exitCode)
    }

    private fun buildFromTemplate(
        template: List<String>,
        exe: String,
        args: List<String>,
        filePath: String,
    ): List<String> {
        val out = ArrayList<String>(template.size + args.size + 2)
        for (t in template) {
            when (t) {
                "{exe}" -> out.add(exe)
                "{args}" -> out.addAll(args)
                "{file}" -> out.add(filePath)
                else -> out.add(t)
            }
        }
        // Safety: ensure a file path is always passed.
        if (!out.contains(filePath)) out.add(filePath)
        return out
    }

    private fun destroyProcess(p: Process) {
        try {
            p.destroy()
        } catch (_: Throwable) {
            // ignore
        }
        try {
            if (p.isAlive) p.destroyForcibly()
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun safeExitValue(p: Process): Int {
        return try {
            p.exitValue()
        } catch (_: Throwable) {
            -1
        }
    }

    private fun gobble(input: InputStream, sink: ByteArrayOutputStream) {
        try {
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                sink.write(buf, 0, n)
            }
        } catch (_: Throwable) {
            // ignore
        } finally {
            try {
                input.close()
            } catch (_: Throwable) {
                // ignore
            }
        }
    }
}
