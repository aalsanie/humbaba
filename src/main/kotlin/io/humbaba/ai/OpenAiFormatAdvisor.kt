/*
 * Copyright © 2025-2026 | Humbaba: AI based formatter that uses a heuristic and AI scoring system to format the whole project.
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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.humbaba.domains.ports.AiFormatAdvisor
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Small OpenAI helper used for (optional) scoring and last-resort formatting.
 *
 * IMPORTANT:
 * - Best-effort: returns null when unavailable
 * - Uses OpenAI Responses API: POST /v1/responses
 */
class OpenAiFormatAdvisor(
    private val settingsProvider: () -> OpenAiSettings?,
    private val mapper: ObjectMapper = jacksonObjectMapper(),
) : AiFormatAdvisor {
    override fun score(
        extension: String,
        languageId: String?,
        original: String,
        candidate: String,
    ): Int? {
        val settings = settingsProvider() ?: return null
        if (settings.apiKey.isBlank()) return null

        val payload = buildScorePayload(settings.model, extension, languageId, original, candidate)
        val resp = send(settings, payload) ?: return null
        val text = extractOutputText(resp) ?: return null

        // Expect JSON: {"score": 0..100}
        return runCatching {
            val node = mapper.readTree(text)
            val s = node.path("score").asInt(-1)
            if (s in 0..100) s else null
        }.getOrNull()
    }

    override fun format(
        extension: String,
        languageId: String?,
        content: String,
    ): String? {
        val settings = settingsProvider() ?: return null
        if (settings.apiKey.isBlank()) return null

        val payload = buildFormatPayload(settings.model, extension, languageId, content)
        val resp = send(settings, payload) ?: return null
        val text = extractOutputText(resp) ?: return null

        // We request raw formatted output only.
        return text.trim().takeIf { it.isNotBlank() }
    }

    private fun send(
        settings: OpenAiSettings,
        payload: String,
    ): com.fasterxml.jackson.databind.JsonNode? {
        return runCatching {
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
            if (resp.statusCode() !in 200..299) return@runCatching null
            mapper.readTree(resp.body())
        }.getOrNull()
    }

    private fun extractOutputText(resp: com.fasterxml.jackson.databind.JsonNode): String? {
        // Responses API usually contains output_text aggregated or message parts
        val direct = resp.path("output_text").asText(null)
        if (!direct.isNullOrBlank()) return direct

        // Fallback: iterate output -> content -> text
        val output = resp.path("output")
        if (!output.isArray) return null
        val sb = StringBuilder()
        for (item in output) {
            val content = item.path("content")
            if (!content.isArray) continue
            for (c in content) {
                val text = c.path("text").asText(null)
                if (!text.isNullOrBlank()) {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(text)
                }
            }
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    private fun buildScorePayload(
        model: String,
        extension: String,
        languageId: String?,
        original: String,
        candidate: String,
    ): String {
        val lang = languageId ?: "unknown"
        val prompt =
            """
            You are a strict code-formatting judge.
            
            Rate ONLY the formatting quality of the candidate output for the given file type.
            Score must be an integer 0..100.
            
            Consider:
            - indentation consistency
            - tag/brace integrity (must not break syntax)
            - spacing normalization
            - common best practices for this language
            
            Output MUST be JSON only: {"score": <int>}
            
            File extension: .$extension
            Language id: $lang
            
            ORIGINAL:
            ---
            $original
            ---
            
            CANDIDATE:
            ---
            $candidate
            ---
            """.trimIndent()

        val payload =
            mapOf(
                "model" to model,
                "input" to listOf(mapOf("role" to "user", "content" to prompt)),
                "max_output_tokens" to 200,
            )
        return mapper.writeValueAsString(payload)
    }

    private fun buildFormatPayload(
        model: String,
        extension: String,
        languageId: String?,
        content: String,
    ): String {
        val lang = languageId ?: "unknown"
        val prompt =
            """
            Format the following file content using best practices for its language.
            Return ONLY the formatted content. No explanations. No markdown fences.
            
            File extension: .$extension
            Language id: $lang
            
            CONTENT:
            ---
            $content
            ---
            """.trimIndent()

        val payload =
            mapOf(
                "model" to model,
                "input" to listOf(mapOf("role" to "user", "content" to prompt)),
                "max_output_tokens" to 4000,
            )
        return mapper.writeValueAsString(payload)
    }

    private fun String?.isNullOrBlank(): Boolean = this == null || this.isBlank()
}
