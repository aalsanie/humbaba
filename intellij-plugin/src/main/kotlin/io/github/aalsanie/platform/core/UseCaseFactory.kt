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
package io.github.aalsanie.platform.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import io.github.aalsanie.ai.OpenAiFormatAdvisor
import io.github.aalsanie.ai.OpenAiRecommender
import io.github.aalsanie.ai.OpenAiSettings
import io.github.aalsanie.domains.usecase.FormatMasterUseCase
import io.github.aalsanie.formatters.DefaultFormatterRegistry
import io.github.aalsanie.formatters.DefaultSafetyPolicy
import io.github.aalsanie.platform.IntellijConsentPrompter
import io.github.aalsanie.platform.IntellijConsentStore
import io.github.aalsanie.platform.IntellijFileClassifier
import io.github.aalsanie.platform.IntellijFileContentWriter
import io.github.aalsanie.platform.IntellijFormatterInstaller
import io.github.aalsanie.platform.IntellijFormatterRunner
import io.github.aalsanie.platform.IntellijNativeFormatter
import io.github.aalsanie.platform.settings.FormatMasterSettingsService
import io.github.aalsanie.platform.settings.OpenAiKeyStore
import java.nio.file.Path

object UseCaseFactory {
    fun build(project: Project): Triple<FormatMasterUseCase, io.github.aalsanie.platform.settings.FormatMasterSettings, DefaultFormatterRegistry> {
        val settingsService = ApplicationManager.getApplication().getService(FormatMasterSettingsService::class.java)
        val settings = settingsService.settings()

        val consentStore = ApplicationManager.getApplication().getService(IntellijConsentStore::class.java)

        val classifier = IntellijFileClassifier(project)
        val nativeFormatter = IntellijNativeFormatter(project)
        val fileContentWriter = IntellijFileContentWriter(project)

        val apiKey = OpenAiKeyStore.get().ifBlank { System.getenv("OPENAI_API_KEY").orEmpty() }

        val settingsProvider: () -> OpenAiSettings? = {
            if (!settings.networkAllowed) {
                null
            } else {
                OpenAiSettings(
                    apiKey = apiKey,
                    model = settings.openAiModel,
                    baseUrl = settings.openAiBaseUrl,
                )
            }
        }

        val ai =
            OpenAiRecommender(
                settingsProvider = settingsProvider,
            )

        val aiAdvisor =
            OpenAiFormatAdvisor(
                settingsProvider = settingsProvider,
            )

        val registry = DefaultFormatterRegistry()
        val safety = DefaultSafetyPolicy()

        val installer =
            IntellijFormatterInstaller(
                cacheDirProvider = { Path.of(settings.cacheDir) },
                networkAllowedProvider = { settings.networkAllowed },
            )

        val runner = IntellijFormatterRunner()

        val consentPrompter = IntellijConsentPrompter(project)

        val useCase =
            FormatMasterUseCase(
                classifier = classifier,
                nativeFormatter = nativeFormatter,
                fileContentWriter = fileContentWriter,
                aiRecommender = ai,
                aiAdvisor = aiAdvisor,
                registry = registry,
                installer = installer,
                runner = runner,
                safety = safety,
                consent = consentStore,
                consentPrompter = consentPrompter,
            )

        return Triple(useCase, settings, registry)
    }
}
