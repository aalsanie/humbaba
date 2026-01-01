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
package io.github.aalsanie.runner.adapters

import io.github.aalsanie.domains.model.FormatterDefinition
import io.github.aalsanie.domains.model.InstallStrategyType
import io.github.aalsanie.domains.ports.FormatterInstaller

/**
 * CLI/Gradle runner: we do not auto-install formatters.
 * We simply report "ok" and let external runner fail if executable is missing.
 */
class NoOpFormatterInstaller : FormatterInstaller {
    override fun ensureInstalled(
        def: FormatterDefinition,
        version: String,
        strategy: InstallStrategyType,
    ): FormatterInstaller.InstallResult {
        return FormatterInstaller.InstallResult(
            ok = true,
            message = "Installer disabled in CLI runner (no-op).",
            toolHome = null,
            executable = null,
        )
    }
}
