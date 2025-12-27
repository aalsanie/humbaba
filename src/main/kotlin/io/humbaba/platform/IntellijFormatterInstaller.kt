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
import io.humbaba.domains.model.InstallStrategyType
import io.humbaba.domains.ports.FormatterInstaller
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class IntellijFormatterInstaller(
    private val cacheDirProvider: () -> Path,
    private val networkAllowedProvider: () -> Boolean,
) : FormatterInstaller {
    private val log = Logger.getInstance(IntellijFormatterInstaller::class.java)

    override fun ensureInstalled(
        def: FormatterDefinition,
        version: String,
        strategy: InstallStrategyType,
    ): FormatterInstaller.InstallResult =
        when (strategy) {
            InstallStrategyType.NPM -> ensureNpm(def, version)
            InstallStrategyType.PIP -> ensurePip(def, version)
            InstallStrategyType.GO -> ensureGo(def, version)
            InstallStrategyType.BINARY -> ensureBinary(def, version)
        }

    private fun ensureNpm(
        def: FormatterDefinition,
        version: String,
    ): FormatterInstaller.InstallResult {
        val base = cacheDirProvider().resolve("npm").resolve(def.id).resolve(version)
        val nodeModules = base.resolve("node_modules")
        val binDir = nodeModules.resolve(".bin")

        // Preferred shim paths (if created)
        val shim =
            when {
                isWindows() -> binDir.resolve("${def.id}.cmd")
                else -> binDir.resolve(def.id)
            }

        // If shim exists, use it.
        if (Files.exists(shim)) return ok("Found ${def.displayName} in cache.", base, shim)
        if (!networkAllowedProvider()) return fail("Network disabled; cannot install ${def.displayName}.")

        Files.createDirectories(base)

        val npm =
            findNpmExecutable() ?: return fail(
                "npm not found. On Windows, ensure Node.js is installed and npm.cmd is on PATH.",
            )

        // Make install location deterministic (critical on Windows)
        val cmd =
            listOf(
                npm,
                "--prefix",
                base.toString(),
                "install",
                "${def.id}@$version",
                "--silent",
                "--no-progress",
                "--no-fund",
                "--no-audit",
            )

        val r = runProcess(cmd, base.toFile())
        if (r.exitCode != 0) return fail("npm install failed: ${r.stderr}".trim())

        // 1) Try shim again
        if (Files.exists(shim)) return ok("Installed ${def.displayName} via npm.", base, shim)

        // 2) Fallback: for Prettier, execute via node script (most reliable)
        // Prettier package path:
        //   node_modules/prettier/bin/prettier.cjs
        if (def.id == "prettier") {
            val node = findOnPath(if (isWindows()) "node.exe" else "node") ?: findOnPath("node")
            if (node == null) return fail("node not found on PATH (needed to run prettier without .bin shim).")

            val script =
                base
                    .resolve("node_modules")
                    .resolve("prettier")
                    .resolve("bin")
                    .resolve("prettier.cjs")
            if (!Files.exists(script)) return fail("Installed but prettier script not found: $script")

            // Encode as "node|<script>" so runner can split (see runner fix below)
            return ok("Installed Prettier via npm (using node script).", base, Path.of("$node|$script"))
        }

        // 3) Generic last resort: fail with useful diagnostics
        return fail(
            "Installed but executable not found. Expected: $shim. " +
                "Check if node_modules was created under: $base",
        )
    }

    private fun ensurePip(
        def: FormatterDefinition,
        version: String,
    ): FormatterInstaller.InstallResult {
        val base = cacheDirProvider().resolve("venv").resolve(def.id).resolve(version)
        val venvDir = base.resolve("venv")
        val exe = venvDir.resolve(if (isWindows()) "Scripts/${def.id}.exe" else "bin/${def.id}")

        if (Files.exists(exe)) return ok("Found ${def.displayName} venv in cache.", base, exe)
        if (!networkAllowedProvider()) return fail("Network disabled; cannot install ${def.displayName}.")

        val py = findOnPath("python3") ?: findOnPath("python") ?: return fail("python not found on PATH.")
        Files.createDirectories(base)

        val venvRes = runProcess(listOf(py, "-m", "venv", venvDir.toString()), base.toFile())
        if (venvRes.exitCode != 0) return fail("Failed to create venv: ${venvRes.stderr}".trim())

        val pipExe = venvDir.resolve(if (isWindows()) "Scripts/pip.exe" else "bin/pip")
        val instRes =
            runProcess(
                listOf(pipExe.toString(), "install", "--no-input", "${def.id}==$version"),
                base.toFile(),
            )
        if (instRes.exitCode != 0) return fail("pip install failed: ${instRes.stderr}".trim())
        if (!Files.exists(exe)) return fail("Installed but executable not found: $exe")

        return ok("Installed ${def.displayName} via pip.", base, exe)
    }

    private fun ensureGo(
        def: FormatterDefinition,
        version: String,
    ): FormatterInstaller.InstallResult {
        val base = cacheDirProvider().resolve("go").resolve(def.id).resolve(version)
        Files.createDirectories(base)

        if (def.id == "gofmt") {
            val sys = findOnPath("gofmt") ?: return fail("gofmt not found on PATH.")
            return ok("Using system gofmt.", base, Path.of(sys))
        }

        val go = findOnPath("go") ?: return fail("go not found on PATH.")
        val binDir = base.resolve("bin")
        Files.createDirectories(binDir)

        val module =
            when (def.id) {
                "yamlfmt" -> "github.com/google/yamlfmt/cmd/yamlfmt"
                else -> return fail("Unknown Go tool mapping for ${def.id}.")
            }

        val exe = binDir.resolve(if (isWindows()) "${def.id}.exe" else def.id)
        if (Files.exists(exe)) return ok("Found ${def.displayName} in cache.", base, exe)
        if (!networkAllowedProvider()) return fail("Network disabled; cannot install ${def.displayName}.")

        val env = mapOf("GOBIN" to binDir.toString())
        val r = runProcess(listOf(go, "install", "$module@$version"), base.toFile(), env)

        if (r.exitCode != 0) return fail("go install failed: ${r.stderr}".trim())
        if (!Files.exists(exe)) return fail("Installed but executable not found: $exe")

        return ok("Installed ${def.displayName} via go install.", base, exe)
    }

    private fun ensureBinary(
        def: FormatterDefinition,
        version: String,
    ): FormatterInstaller.InstallResult {
        if (!networkAllowedProvider()) return fail("Network disabled; cannot download ${def.displayName}.")
        return fail("Binary auto-install not configured for ${def.id}.")
    }

    // ---- helpers ----

    private fun findNpmExecutable(): String? {
        if (isWindows()) return findOnPath("npm.cmd") ?: findOnPath("npm")
        return findOnPath("npm")
    }

    private fun runProcess(
        cmd: List<String>,
        cwd: File,
        env: Map<String, String> = emptyMap(),
    ): ProcRes =
        try {
            val pb = ProcessBuilder(cmd).directory(cwd)
            env.forEach { (k, v) -> pb.environment()[k] = v }
            val p = pb.start()
            val out = p.inputStream.bufferedReader().readText()
            val err = p.errorStream.bufferedReader().readText()
            p.waitFor(10, TimeUnit.MINUTES)
            ProcRes(p.exitValue(), out, err)
        } catch (t: Throwable) {
            log.warn("Process failed: $cmd", t)
            ProcRes(1, "", t.message ?: "process error")
        }

    private fun findOnPath(name: String): String? =
        try {
            val cmd = if (isWindows()) listOf("where", name) else listOf("which", name)
            val p = ProcessBuilder(cmd).start()
            val out = p.inputStream.bufferedReader().readLine()
            if (p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0) out?.trim() else null
        } catch (_: Throwable) {
            null
        }

    private fun ok(
        msg: String,
        home: Path,
        exe: Path,
    ) = FormatterInstaller.InstallResult(true, msg, home.toString(), exe.toString())

    private fun fail(msg: String) = FormatterInstaller.InstallResult(false, msg, null, null)

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")

    private data class ProcRes(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )
}
