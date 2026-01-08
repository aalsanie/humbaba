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

import io.github.aalsanie.domains.model.FileFormatReport
import io.github.aalsanie.reporting.FormatCoverageAggregator
import io.github.aalsanie.reporting.FormatCoverageHtmlWriter
import io.github.aalsanie.reporting.FormatCoverageJsonWriter
import io.github.aalsanie.reporting.FormatCoverageXmlWriter
import io.github.aalsanie.runner.EnvInfo
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.system.exitProcess

fun main(rawArgs: Array<String>) {
    val args = Args.parse(rawArgs.toList())

    if (args.showHelp || args.command == null) {
        println(Args.usage())
        exitProcess(0)
    }

    if (args.command != "format") {
        System.err.println("Unknown command: ${args.command}")
        System.err.println(Args.usage())
        exitProcess(2)
    }

    val target = args.targetPath ?: run {
        System.err.println("Missing <path>: humbaba format <path> [options]")
        System.err.println(Args.usage())
        exitProcess(2)
    }

    val targetAbs = target.absolute().normalize()
    if (!targetAbs.exists()) {
        System.err.println("Target does not exist: $targetAbs")
        exitProcess(2)
    }

    val projectRoot =
        (args.projectRoot ?: findProjectRoot(targetAbs)).absolute().normalize()

    val cancel = CancellationFlag()
    val useCase = CliUseCaseFactory.build(projectRoot, args, cancel)

    // Collect files under TARGET (file or directory).
    val files = CliFileCollector.collect(targetAbs)
    if (files.isEmpty()) {
        println("No eligible files found under: $targetAbs")
        exitProcess(0)
    }

    val reports = ArrayList<FileFormatReport>(files.size)

    files.forEachIndexed { idx, file ->
        val display = try { projectRoot.relativize(file).toString() } catch (_: Throwable) { file.toString() }
        println("(${idx + 1}/${files.size}) $display")

        val beforeText = safeReadText(file)
        val beforeHash = hashFile(file)

        val result = useCase.execute(
            CliRequests.buildRequest(file = file, args = args)
        )

        if (result.errors.isNotEmpty()) {
            System.err.println("  ✗ ${display}: ${result.errors.joinToString("; ")}")
        }

        val afterHash = hashFile(file)
        val changed = beforeHash != null && afterHash != null && beforeHash != afterHash

        if (args.dryRun && changed && beforeText != null) {
            try {
                Files.writeString(file, beforeText)
            } catch (_: Throwable) {
            }
        }
        val finalAfterHash = if (args.dryRun) hashFile(file) else afterHash
        val finalChanged = if (args.dryRun) false else changed

        reports += CliReports.toDeterministicReport(
            file = file,
            beforeHash = beforeHash,
            afterHash = finalAfterHash,
            changed = finalChanged,
            result = result,
        )
    }

    val coverage = FormatCoverageAggregator.aggregate(reports)

    val reportsDir = projectRoot.resolve(".humbaba").resolve("reports")
    Files.createDirectories(reportsDir)

    val jsonPath = reportsDir.resolve("format-coverage.json")
    val xmlPath = reportsDir.resolve("format-coverage.xml")
    val htmlPath = reportsDir.resolve("format-coverage.html")

    FormatCoverageJsonWriter.write(coverage, jsonPath)
    FormatCoverageXmlWriter.write(coverage, xmlPath)
    FormatCoverageHtmlWriter.write(coverage, htmlPath)

    val failed = reports.count { it.outcome.name == "FAILED" }

    println("Done. coverage=${coverage.coveragePercent}% formatted=${reports.count { it.outcome.name == "FORMATTED" }} already=${reports.count { it.outcome.name == "ALREADY_FORMATTED" }} failed=$failed")
    println("Reports: $htmlPath")

    exitProcess(0)
}

private object CliRequests {
    fun buildRequest(file: Path, args: Args): io.github.aalsanie.domains.model.FormatRequest {
        val ext = file.fileName.toString().substringAfterLast('.', "").lowercase()
        return io.github.aalsanie.domains.model.FormatRequest(
            filePath = file.toString(),
            extension = ext,
            languageId = null,
            ideInfo = EnvInfo.ideInfo(), // should return IdeInfo
            osInfo = EnvInfo.osInfo(),   // should return OsInfo
            sample = null,
            preferExistingFormatterFirst = true,
            allowAutoInstall = false,
            networkAllowed = true,
            aiEnabled = args.aiEnabled,
            dryRun = args.dryRun,
        )
    }
}

private fun findProjectRoot(targetAbs: Path): Path {
    val start = if (targetAbs.isDirectory()) targetAbs else (targetAbs.parent ?: targetAbs)
    var cur: Path? = start

    while (cur != null) {
        if (Files.exists(cur.resolve("settings.gradle.kts"))) return cur
        if (Files.exists(cur.resolve("settings.gradle"))) return cur
        if (Files.exists(cur.resolve(".git"))) return cur
        if (Files.exists(cur.resolve("gradlew")) || Files.exists(cur.resolve("gradlew.bat"))) return cur
        cur = cur.parent
    }

    // fallback: old behavior
    return start
}

private fun safeReadText(path: Path): String? {
    return try {
        if (!path.isRegularFile()) return null
        val bytes = Files.readAllBytes(path)
        val max = 200_000
        val slice = if (bytes.size > max) bytes.copyOfRange(0, max) else bytes
        slice.toString(Charsets.UTF_8)
    } catch (_: Throwable) {
        null
    }
}

private fun hashFile(path: Path): String? {
    return try {
        if (!path.isRegularFile()) return null
        Files.newInputStream(path).use { input -> hashStream(input, 5_000_000) }
    } catch (_: Throwable) {
        null
    }
}

private fun hashStream(input: InputStream, maxBytes: Long): String {
    val md = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(64 * 1024)
    var remaining = maxBytes
    while (remaining > 0) {
        val toRead = if (remaining < buf.size) remaining.toInt() else buf.size
        val n = input.read(buf, 0, toRead)
        if (n <= 0) break
        md.update(buf, 0, n)
        remaining -= n.toLong()
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}
