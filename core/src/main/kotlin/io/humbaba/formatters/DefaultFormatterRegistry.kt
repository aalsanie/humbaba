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
package io.humbaba.formatters

import io.humbaba.domains.model.FormatterDefinition
import io.humbaba.domains.model.InstallStrategyType
import io.humbaba.domains.ports.FormatterRegistry

class DefaultFormatterRegistry : FormatterRegistry {
    private val definitions: Map<String, FormatterDefinition> =
        listOf(
            prettier(),
            black(),
            ruff(),
            gofmt(),
            clangFormat(),
            shfmt(),
            stylua(),
            yamlfmt(),
        ).associateBy { it.id }

    override fun findById(id: String): FormatterDefinition? = definitions[id]

    override fun findByExtension(extension: String): List<FormatterDefinition> {
        val ext = extension.lowercase()
        return definitions.values.filter { it.supportedExtensions.contains(ext) }
    }

    private fun prettier() =
        FormatterDefinition(
            id = "prettier",
            displayName = "Prettier",
            supportedExtensions = setOf("js", "ts", "jsx", "tsx", "json", "css", "html", "md", "yaml", "yml"),
            installStrategies = setOf(InstallStrategyType.NPM),
            allowedArgs = setOf("--write", "--log-level", "warn"),
            commandTemplate = listOf("{exe}", "{args}", "{file}"),
        )

    private fun black() =
        FormatterDefinition(
            id = "black",
            displayName = "Black",
            supportedExtensions = setOf("py"),
            installStrategies = setOf(InstallStrategyType.PIP),
            allowedArgs = setOf("--quiet"),
            commandTemplate = listOf("{exe}", "{args}", "{file}"),
        )

    private fun ruff() =
        FormatterDefinition(
            id = "ruff",
            displayName = "Ruff Format",
            supportedExtensions = setOf("py"),
            installStrategies = setOf(InstallStrategyType.PIP),
            allowedArgs = setOf("format"),
            commandTemplate = listOf("{exe}", "format", "{args}", "{file}"),
        )

    private fun gofmt() =
        FormatterDefinition(
            id = "gofmt",
            displayName = "gofmt",
            supportedExtensions = setOf("go"),
            installStrategies = setOf(InstallStrategyType.GO),
            allowedArgs = setOf("-w"),
            commandTemplate = listOf("{exe}", "{args}", "{file}"),
        )

    private fun clangFormat() =
        FormatterDefinition(
            id = "clang-format",
            displayName = "clang-format",
            supportedExtensions = setOf("c", "cc", "cpp", "cxx", "h", "hpp", "hh", "hxx"),
            installStrategies = setOf(InstallStrategyType.BINARY),
            allowedArgs = setOf("-i"),
            commandTemplate = listOf("{exe}", "{args}", "{file}"),
        )

    private fun shfmt() =
        FormatterDefinition(
            id = "shfmt",
            displayName = "shfmt",
            supportedExtensions = setOf("sh", "bash"),
            installStrategies = setOf(InstallStrategyType.BINARY),
            allowedArgs = setOf("-w"),
            commandTemplate = listOf("{exe}", "{args}", "{file}"),
        )

    private fun stylua() =
        FormatterDefinition(
            id = "stylua",
            displayName = "StyLua",
            supportedExtensions = setOf("lua"),
            installStrategies = setOf(InstallStrategyType.BINARY),
            allowedArgs = setOf("--search-parent-directories"),
            commandTemplate = listOf("{exe}", "{args}", "{file}"),
        )

    private fun yamlfmt() =
        FormatterDefinition(
            id = "yamlfmt",
            displayName = "yamlfmt",
            supportedExtensions = setOf("yaml", "yml"),
            installStrategies = setOf(InstallStrategyType.GO),
            allowedArgs = setOf("-w"),
            commandTemplate = listOf("{exe}", "{args}", "{file}"),
        )
}
