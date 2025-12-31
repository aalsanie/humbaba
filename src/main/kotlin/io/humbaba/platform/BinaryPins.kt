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
package io.humbaba.platform

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration

object BinaryPins {
    data class Pin(
        val toolId: String,
        val version: String,
        val os: String,
        val url: String,
        val sha256: String,
    )

    // safety-first: empty by default. add official pinned assets + sha256 deliberately.
    private val pins: List<Pin> = listOf()

    fun find(
        toolId: String,
        version: String,
        os: String,
    ): Pin? = pins.firstOrNull { it.toolId == toolId && it.version == version && it.os == os }

    fun download(url: String): ByteArray? =
        try {
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()
            val req =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build()
            val resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray())
            if (resp.statusCode() in 200..299) resp.body() else null
        } catch (_: Throwable) {
            null
        }

    fun sha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
