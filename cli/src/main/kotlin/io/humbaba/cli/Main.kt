/*
 * Copyright © 2025-2026 | Humbaba is a safe, deterministic formatting orchestrator for polyglot repositories.
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
package io.humbaba.cli

import io.humbaba.runner.HumbabaRunner
import io.humbaba.runner.RunOptions
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.system.exitProcess

/**
 * Humbaba CLI entrypoint.
 *
 * Usage:
 *   humbaba format <path> [--root <path>] [--dry-run] [--preview] [--ai] [--yes]
 */
fun main(args: Array<String>) {
    val parsed = try {
        Args.parse(args.toList())
    } catch (t: Throwable) {
        System.err.println(t.message ?: "Invalid arguments.")
        System.err.println()
        System.err.println(Args.usage())
        exitProcess(2)
    }

    if (parsed.showHelp || parsed.command == null) {
        println(Args.usage())
        exitProcess(0)
    }

    val command = parsed.command!!.lowercase()
    if (command != "format") {
        System.err.println("Unknown command: ${parsed.command}")
        System.err.println()
        System.err.println(Args.usage())
        exitProcess(2)
    }

    val target = parsed.targetPath
    if (target == null) {
        System.err.println("Missing <path> argument.")
        System.err.println()
        System.err.println(Args.usage())
        exitProcess(2)
    }

    val root: Path =
        (parsed.projectRoot ?: run {
            val normalized = target.normalize()
            when {
                Files.isDirectory(normalized) -> normalized
                normalized.parent != null -> normalized.parent
                else -> Path(".")
            }
        }).normalize()

    val options = RunOptions(
        dryRun = parsed.dryRun,
        preview = parsed.preview,
        aiEnabled = parsed.aiEnabled,
        yes = parsed.yes,
    )

    val runner = HumbabaRunner()

    val result = try {
        runner.formatAndReport(
            root = root,
            options = options,
            log = { println(it) },
            isCanceled = { false },
        )
    } catch (t: Throwable) {
        // Ctrl+C usually maps to InterruptedException in JVM tools; handle that as 130.
        val name = t::class.simpleName ?: ""
        if (t is InterruptedException || name.contains("Interrupted", ignoreCase = true)) {
            System.err.println("Canceled.")
            exitProcess(130)
        }

        System.err.println("Failed: ${t.message ?: t::class.qualifiedName}")
        t.printStackTrace(System.err)
        exitProcess(1)
    }

    // Exit code: non-zero if failures happened.
    if (result.failedFiles > 0) exitProcess(1)
    exitProcess(0)
}
