/*
 * Copyright © 2025-2026 | Humbaba is a formatting tool that formats the whole code base using safe strategy.
 *
 * Author: @aalsanie
 *
 * Plugin: TODO: REPLACEME
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
package io.humbaba.platform.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import io.humbaba.ai.OpenAiRecommender
import io.humbaba.ai.OpenAiSettings
import io.humbaba.domains.usecase.FormatMasterUseCase
import io.humbaba.formatters.DefaultFormatterRegistry
import io.humbaba.formatters.DefaultSafetyPolicy
import io.humbaba.platform.IntellijConsentStore
import io.humbaba.platform.IntellijFileClassifier
import io.humbaba.platform.IntellijFormatterInstaller
import io.humbaba.platform.IntellijFormatterRunner
import io.humbaba.platform.IntellijNativeFormatter
import io.humbaba.platform.settings.FormatMasterSettingsService
import io.humbaba.platform.settings.OpenAiKeyStore
import java.nio.file.Path

object UseCaseFactory {
    fun build(project: Project): Triple<FormatMasterUseCase, io.humbaba.platform.settings.FormatMasterSettings, DefaultFormatterRegistry> {
        val settingsService = ApplicationManager.getApplication().getService(FormatMasterSettingsService::class.java)
        val settings = settingsService.settings()

        val consentStore = ApplicationManager.getApplication().getService(IntellijConsentStore::class.java)

        val classifier = IntellijFileClassifier(project)
        val nativeFormatter = IntellijNativeFormatter(project)

        val apiKey = OpenAiKeyStore.get().ifBlank { System.getenv("OPENAI_API_KEY").orEmpty() }

        val ai =
            OpenAiRecommender(
                settingsProvider = {
                    if (!settings.networkAllowed) return@OpenAiRecommender null
                    OpenAiSettings(
                        apiKey = apiKey,
                        model = settings.openAiModel,
                        baseUrl = settings.openAiBaseUrl,
                    )
                },
            )

        val registry = DefaultFormatterRegistry()
        val safety = DefaultSafetyPolicy()
        val installer =
            IntellijFormatterInstaller(
                cacheDirProvider = { Path.of(settings.cacheDir) },
                networkAllowedProvider = { settings.networkAllowed },
            )
        val runner = IntellijFormatterRunner()

        val useCase =
            FormatMasterUseCase(
                classifier = classifier,
                nativeFormatter = nativeFormatter,
                ai = ai,
                registry = registry,
                installer = installer,
                runner = runner,
                safety = safety,
                consent = consentStore,
            )

        return Triple(useCase, settings, registry)
    }
}
