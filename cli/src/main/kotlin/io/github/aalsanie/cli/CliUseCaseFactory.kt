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

import io.github.aalsanie.ai.OpenAiFormatAdvisor
import io.github.aalsanie.ai.OpenAiRecommender
import io.github.aalsanie.ai.OpenAiSettings
import io.github.aalsanie.domains.ports.AiFormatAdvisor
import io.github.aalsanie.domains.ports.AiRecommender
import io.github.aalsanie.domains.ports.ConsentPrompter
import io.github.aalsanie.domains.usecase.FormatMasterUseCase
import io.github.aalsanie.formatters.DefaultFormatterRegistry
import io.github.aalsanie.formatters.DefaultSafetyPolicy
import java.nio.file.Files
import java.nio.file.Path

object CliUseCaseFactory {

    fun build(projectRoot: Path, args: Args, cancel: CancellationFlag): FormatMasterUseCase {
        val classifier = CliFileClassifier()
        val native = CliNativeFormatter()
        val writer = CliFileContentWriter()

        val registry = DefaultFormatterRegistry()
        val safety = DefaultSafetyPolicy()

        val humbabaDir = projectRoot.resolve(".humbaba")
        Files.createDirectories(humbabaDir)

        val consent = CliConsentStore()
        val prompter: ConsentPrompter = CliConsentPrompter(args.yes)

        val installer = CliFormatterInstaller()
        val runner = io.github.aalsanie.cli.CliFormatterRunner(cancel)

        val (aiRecommender, aiAdvisor) = aiParts(args)

        return FormatMasterUseCase(
            classifier = classifier,
            nativeFormatter = native,
            fileContentWriter = writer,
            aiRecommender = aiRecommender,
            aiAdvisor = aiAdvisor,
            registry = registry,
            installer = installer,
            runner = runner,
            safety = safety,
            consent = consent,
            consentPrompter = prompter,
        )
    }

    private fun aiParts(args: Args): Pair<AiRecommender, AiFormatAdvisor> {
        if (!args.aiEnabled) return NoOpAiRecommender() to NoOpAiFormatAdvisor()

        val apiKey = System.getenv("OPENAI_API_KEY")?.trim().orEmpty()
        if (apiKey.isBlank()) {
            System.err.println("--ai was enabled but OPENAI_API_KEY is not set. AI is disabled.")
            return NoOpAiRecommender() to NoOpAiFormatAdvisor()
        }

        // Keep model selection centralized & explicit for the CLI.
        val settings = OpenAiSettings(
            apiKey = apiKey,
            model = "gpt-4.1-mini",
        )

        // Provider lambda to match core signature: () -> OpenAiSettings?
        val settingsProvider: () -> OpenAiSettings? = { settings }

        return OpenAiRecommender(settingsProvider) to OpenAiFormatAdvisor(settingsProvider)
    }
}
