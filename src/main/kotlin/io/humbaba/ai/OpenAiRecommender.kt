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
package io.humbaba.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.humbaba.domains.model.FormatRequest
import io.humbaba.domains.model.FormatterRecommendation
import io.humbaba.domains.model.InstallStrategyType
import io.humbaba.domains.ports.AiRecommender
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Uses OpenAI Responses API: POST /v1/responses
 * Auth: Authorization: Bearer <OPENAI_API_KEY>
 */
class OpenAiRecommender(
    private val settingsProvider: () -> OpenAiSettings?,
    private val cache: RecommendationCache = RecommendationCache(),
    private val mapper: ObjectMapper = jacksonObjectMapper(),
) : AiRecommender {
    override fun recommend(request: FormatRequest): FormatterRecommendation? {
        val settings = settingsProvider() ?: return null
        if (settings.apiKey.isBlank()) return null

        val cacheKey = listOf(request.extension, request.languageId ?: "", request.osInfo.name, settings.model).joinToString("|")
        cache.get(cacheKey)?.let { return it }

        val payload = buildPayload(settings.model, request)
        val client =
            HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(settings.timeoutSeconds))
                .build()

        val httpReq =
            HttpRequest
                .newBuilder()
                .uri(URI.create(settings.baseUrl.trimEnd('/') + "/v1/responses"))
                .timeout(Duration.ofSeconds(settings.timeoutSeconds))
                .header("Authorization", "Bearer ${settings.apiKey}")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build()

        val resp = client.send(httpReq, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) return null

        val root = mapper.readTree(resp.body())
        val outputText = extractOutputText(root) ?: return null
        val node = mapper.readTree(outputText)

        val errs = ResponseValidator.validate(node)
        if (errs.isNotEmpty()) return null

        val formatterId = node["formatter_id"].asText()
        val version = node["version"].asText()
        val strategy = InstallStrategyType.valueOf(node["install_strategy"].asText())
        val runArgs = node["run_args"].map { it.asText() }
        val confidence = node["confidence"].asDouble()
        val rationale = node["rationale"].asText()

        val rec =
            FormatterRecommendation(
                formatterId = formatterId,
                version = version,
                installStrategy = strategy,
                runArgs = runArgs,
                confidence = confidence,
                rationale = rationale,
                sources = emptyList(),
            )
        cache.put(cacheKey, rec)
        return rec
    }

    private fun buildPayload(
        model: String,
        req: FormatRequest,
    ): String {
        val obj =
            mapOf(
                "model" to model,
                "input" to
                    listOf(
                        mapOf(
                            "role" to "system",
                            "content" to listOf(mapOf("type" to "input_text", "text" to PromptFactory.buildSystem())),
                        ),
                        mapOf(
                            "role" to "user",
                            "content" to
                                listOf(
                                    mapOf("type" to "input_text", "text" to PromptFactory.jsonSchemaHint()),
                                    mapOf("type" to "input_text", "text" to PromptFactory.buildUser(req)),
                                ),
                        ),
                    ),
                "text" to mapOf("format" to mapOf("type" to "text")),
            )
        return mapper.writeValueAsString(obj)
    }

    private fun extractOutputText(root: com.fasterxml.jackson.databind.JsonNode): String? {
        if (root.hasNonNull("output_text")) return root["output_text"].asText()
        val out = root.get("output") ?: return null
        if (!out.isArray) return null
        for (item in out) {
            val content = item.get("content") ?: continue
            if (!content.isArray) continue
            for (c in content) {
                val type = c.get("type")?.asText() ?: continue
                if (type.contains("output_text")) {
                    val text = c.get("text")?.asText()
                    if (!text.isNullOrBlank()) return text
                }
            }
        }
        return null
    }
}
