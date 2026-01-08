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
import io.github.aalsanie.domains.model.InstallStrategyType
import io.github.aalsanie.domains.ports.FormatterInstaller
import java.nio.file.Files
import java.nio.file.Path

/**
 * CLI installer: "production safe" by default.
 * It does NOT auto-install tools; it only resolves executables from PATH.
 * (This avoids running package managers implicitly.)
 */
class CliFormatterInstaller : FormatterInstaller {
    override fun ensureInstalled(def: FormatterDefinition, version: String, strategy: InstallStrategyType): FormatterInstaller.InstallResult {
        val exeName = when (def.id.lowercase()) {
            "prettier" -> "prettier"
            "black" -> "black"
            "ruff" -> "ruff"
            "gofmt" -> "gofmt"
            "clang-format" -> "clang-format"
            "shfmt" -> "shfmt"
            "stylua" -> "stylua"
            "yamlfmt" -> "yamlfmt"
            else -> def.id
        }

        val resolved = resolveOnPath(exeName)
        return if (resolved != null) {
            FormatterInstaller.InstallResult(ok = true, message = "Resolved $exeName", toolHome = null, executable = resolved.toString())
        } else {
            FormatterInstaller.InstallResult(ok = false, message = "Executable '$exeName' not found on PATH.", toolHome = null, executable = null)
        }
    }

    private fun resolveOnPath(exe: String): Path? {
        val pathEnv = System.getenv("PATH") ?: return null
        val sep = System.getProperty("path.separator") ?: ":"
        val parts = pathEnv.split(sep).filter { it.isNotBlank() }

        val candidates = mutableListOf(exe)
        // Windows
        if (System.getProperty("os.name")?.lowercase()?.contains("win") == true) {
            val pathext = (System.getenv("PATHEXT") ?: ".EXE;.BAT;.CMD").split(";")
            candidates.clear()
            for (ext in pathext) {
                val e = ext.trim()
                if (e.isBlank()) continue
                candidates.add(exe + e.lowercase())
                candidates.add(exe + e.uppercase())
            }
        }

        for (dir in parts) {
            for (cand in candidates) {
                val p = Path.of(dir).resolve(cand)
                if (Files.exists(p) && Files.isRegularFile(p) && Files.isExecutable(p)) return p
            }
        }

        return null
    }
}
