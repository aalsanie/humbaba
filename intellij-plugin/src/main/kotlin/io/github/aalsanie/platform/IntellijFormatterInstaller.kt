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
package io.github.aalsanie.platform

import com.intellij.openapi.diagnostic.Logger
import io.github.aalsanie.domains.model.FormatterDefinition
import io.github.aalsanie.domains.model.InstallStrategyType
import io.github.aalsanie.domains.ports.FormatterInstaller
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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
        val binDir = base.resolve("node_modules").resolve(".bin")

        val shim =
            if (isWindows()) {
                binDir.resolve("${def.id}.cmd")
            } else {
                binDir.resolve(def.id)
            }

        if (Files.exists(shim)) return ok("Found ${def.displayName} in cache.", base, shim)
        if (!networkAllowedProvider()) return fail("Network disabled; cannot install ${def.displayName}.")

        Files.createDirectories(base)

        val npm =
            findNpmExecutable()
                ?: return fail("npm not found. Install Node.js so npm is available on PATH.")

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

        if (Files.exists(shim)) return ok("Installed ${def.displayName} via npm.", base, shim)

        // Prettier fallback: run as node script when .bin shim isn't created reliably
        if (def.id == "prettier") {
            val node =
                findOnPath(if (isWindows()) "node.exe" else "node") ?: findOnPath("node")
                    ?: return fail("node not found on PATH (needed to run prettier).")

            val script =
                base
                    .resolve("node_modules")
                    .resolve("prettier")
                    .resolve("bin")
                    .resolve("prettier.cjs")
            if (!Files.exists(script)) return fail("Installed but prettier script not found: $script")

            // Runner should split by "|" into [node, script]
            return ok("Installed Prettier via npm (node script mode).", base, Paths.get("$node|$script"))
        }

        return fail(
            "Installed but executable not found. Expected: $shim. " +
                "Check if node_modules exists under: $base",
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

        val py =
            findOnPath("python3") ?: findOnPath("python")
                ?: return fail(
                    "python not found on PATH. Install Python (or disable Microsoft Store python alias) to allow auto-install.",
                )

        Files.createDirectories(base)

        val venvRes = runProcess(listOf(py, "-m", "venv", venvDir.toString()), base.toFile())
        if (venvRes.exitCode != 0) return fail("Failed to create venv: ${venvRes.stderr}".trim())

        val pipExe = venvDir.resolve(if (isWindows()) "Scripts/pip.exe" else "bin/pip")
        val instRes = runProcess(listOf(pipExe.toString(), "install", "--no-input", "${def.id}==$version"), base.toFile())
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

        // gofmt is part of the Go distribution: we can only use it if Go is installed.
        if (def.id == "gofmt") {
            val sys = findOnPath("gofmt") ?: return fail("gofmt not found on PATH. Install Go to enable gofmt.")
            return ok("Using system gofmt.", base, Path.of(sys))
        }

        val go = findOnPath("go") ?: return fail("go not found on PATH. Install Go to enable ${def.displayName}.")
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
        // Prefer system binary
        val sys = findOnPath(def.id) ?: (if (!def.id.endsWith(".exe")) findOnPath("${def.id}.exe") else null)
        if (!sys.isNullOrBlank()) {
            return ok("Using system ${def.displayName} from PATH.", cacheDirProvider().resolve("bin"), Path.of(sys))
        }

        // Optional: pinned download
        val base = cacheDirProvider().resolve("bin").resolve(def.id).resolve(version)
        val exeName = if (isWindows()) "${def.id}.exe" else def.id
        val exe = base.resolve(exeName)

        if (Files.exists(exe)) return ok("Found ${def.displayName} in cache.", base, exe)

        val pin =
            io.github.aalsanie.platform.BinaryPins.find(def.id, version, platformTag())
                ?: return fail("Install ${def.displayName} and ensure it is on PATH (auto-download not configured).")

        if (!networkAllowedProvider()) return fail("Network disabled; cannot download ${def.displayName}.")

        Files.createDirectories(base)
        val bytes = io.github.aalsanie.platform.BinaryPins.download(pin.url) ?: return fail("Failed to download ${def.displayName}.")
        val sha = io.github.aalsanie.platform.BinaryPins.sha256(bytes)
        if (!sha.equals(pin.sha256, ignoreCase = true)) return fail("Checksum mismatch for ${def.id} $version. Aborting.")

        Files.write(exe, bytes)
        exe.toFile().setExecutable(true)
        return ok("Downloaded ${def.displayName} (pinned) and verified checksum.", base, exe)
    }

    // ---------- helpers ----------

    private fun findNpmExecutable(): String? =
        if (isWindows()) {
            findOnPath("npm.cmd") ?: findOnPath("npm")
        } else {
            findOnPath("npm")
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

    // IMPORTANT: block-body so we can use `return` safely
    private fun findOnPath(name: String): String? {
        return try {
            val cmd = if (isWindows()) listOf("where", name) else listOf("which", name)
            val p = ProcessBuilder(cmd).start()

            val lines =
                p.inputStream
                    .bufferedReader()
                    .readLines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

            val ok = p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0
            if (!ok || lines.isEmpty()) return null

            val pick =
                if (isWindows()) {
                    lines.firstOrNull { it.lowercase().endsWith(".cmd") }
                        ?: lines.firstOrNull { it.lowercase().endsWith(".exe") }
                        ?: lines.first()
                } else {
                    lines.first()
                }

            if (isWindows()) {
                // Prefer cmd shims when `where npm` returns the JS shim
                val lower = pick.lowercase()
                if ((lower.endsWith("\\npm") || lower.endsWith("/npm")) && File(pick + ".cmd").exists()) return pick + ".cmd"
                if ((lower.endsWith("\\npx") || lower.endsWith("/npx")) && File(pick + ".cmd").exists()) return pick + ".cmd"
            }

            pick
        } catch (_: Throwable) {
            null
        }
    }

    private fun platformTag(): String {
        val osName = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()

        val os =
            when {
                osName.contains("win") -> "windows"
                osName.contains("mac") || osName.contains("darwin") -> "mac"
                else -> "linux"
            }

        val normalizedArch =
            when {
                arch.contains("aarch64") || arch.contains("arm64") -> "arm64"
                arch.contains("x86_64") || arch.contains("amd64") -> "x64"
                arch.contains("x86") || arch.contains("i386") || arch.contains("i686") -> "x86"
                else -> arch.replace(Regex("[^a-z0-9]+"), "")
            }

        return "$os-$normalizedArch"
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
