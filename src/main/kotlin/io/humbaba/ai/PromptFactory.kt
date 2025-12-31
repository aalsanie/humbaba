/*
 * Copyright © 2025-2026 | Humbaba is a safe, deterministic formatting orchestrator for polyglot repositories.
 * Reports back format coverage percentage
 *
 * Author: @aalsanie
 *
 * Plugin: https://plugins.jetbrains.com/plugin/29549-humbaba
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
package io.humbaba.ai

import io.humbaba.domains.model.FormatRequest

object PromptFactory {
    fun buildSystem(): String =
        """
        You are a formatter selector.
        Return ONLY valid JSON that matches the schema exactly.
        Do not include markdown, explanations outside JSON, or extra keys.
        Choose a formatter ONLY from this allow-list of formatter_id values:
          - prettier
          - black
          - ruff
          - gofmt
          - clang-format
          - shfmt
          - stylua
          - yamlfmt

        Install strategies must be one of: NPM, PIP, GO, BINARY.
        run_args must contain ONLY flags that are safe and commonly used.
        """.trimIndent()

    fun buildUser(req: FormatRequest): String {
        val sample = (req.sample ?: "").take(800)
        return """
            Select the best formatter for this file.

            extension: ${req.extension}
            languageId: ${req.languageId ?: "unknown"}
            os: ${req.osInfo.name} ${req.osInfo.version} (${req.osInfo.arch})
            ide: ${req.ideInfo.productName ?: "unknown"} ${req.ideInfo.buildNumber ?: ""}
            preferExistingFormatterFirst: ${req.preferExistingFormatterFirst}

            First 800 chars sample (may be empty):
            $sample
            """.trimIndent()
    }

    fun jsonSchemaHint(): String =
        """
        JSON schema:
        {
          "formatter_id": "prettier|black|ruff|gofmt|clang-format|shfmt|stylua|yamlfmt",
          "version": "stable version string (e.g. 3.3.3)",
          "install_strategy": "NPM|PIP|GO|BINARY",
          "run_args": ["..."],
          "confidence": 0.0,
          "rationale": "short"
        }
        """.trimIndent()
}
