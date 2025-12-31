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
package io.humbaba.gradle

import io.humbaba.runner.HumbabaRunner
import io.humbaba.runner.RunOptions
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

abstract class HumbabaFormatTask : DefaultTask() {

    @get:InputDirectory
    abstract val rootDir: DirectoryProperty

    @get:Input
    abstract val dryRun: Property<Boolean>

    @get:Input
    abstract val preview: Property<Boolean>

    @get:Input
    abstract val aiEnabled: Property<Boolean>

    @get:Input
    abstract val yes: Property<Boolean>

    @TaskAction
    fun run() {
        val root = rootDir.get().asFile.toPath()

        val result = HumbabaRunner().formatAndReport(
            root = root,
            options = RunOptions(
                dryRun = dryRun.get(),
                preview = preview.get(),
                aiEnabled = aiEnabled.get(),
                yes = yes.get(),
            ),
            log = { logger.lifecycle(it) },
            isCanceled = { Thread.currentThread().isInterrupted },
        )

        if (result.failedFiles > 0) {
            throw RuntimeException("Humbaba failed on ${result.failedFiles} file(s). See reports under ${result.reportsDir}.")
        }
    }
}
