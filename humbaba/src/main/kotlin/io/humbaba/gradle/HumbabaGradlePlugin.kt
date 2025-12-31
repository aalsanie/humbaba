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

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Gradle plugin entrypoint.
 *
 * Registers:
 * - Task `humbabaFormat` on the applying project
 *
 * In this repository, you can also run `./gradlew humbaba:format` since the `:humbaba`
 * subproject registers an alias task named `format`.
 */
class HumbabaGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val ext = project.extensions.create("humbaba", HumbabaExtension::class.java)

        project.tasks.register("humbabaFormat", HumbabaFormatTask::class.java) { t ->
            t.group = "humbaba"
            t.description = "Format repository files and generate coverage reports under .humbaba/reports."
            t.rootDir.set(project.layout.projectDirectory.dir(ext.rootDir.getOrElse(".")))
            t.dryRun.set(ext.dryRun)
            t.preview.set(ext.preview)
            t.aiEnabled.set(ext.aiEnabled)
            t.yes.set(ext.yes)
        }
    }
}
