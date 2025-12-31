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
package io.humbaba.cli

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.humbaba.domains.ports.ConsentStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CliConsentStore(private val file: Path) : ConsentStore {
    private val lock = ReentrantLock()
    private val mapper = jacksonObjectMapper()

    override fun isFormatterTrusted(formatterId: String): Boolean = lock.withLock {
        read().contains(formatterId)
    }

    override fun trustFormatter(formatterId: String) {
        lock.withLock {
            val set = read().toMutableSet()
            set.add(formatterId)
            write(set)
        }
    }

    override fun untrustFormatter(formatterId: String) {
        lock.withLock {
            val set = read().toMutableSet()
            set.remove(formatterId)
            write(set)
        }
    }

    override fun trustedFormatters(): Set<String> = lock.withLock { read() }

    private fun read(): Set<String> {
        return try {
            if (!Files.exists(file)) return emptySet()
            mapper.readValue(file.toFile())
        } catch (_: Throwable) {
            emptySet()
        }
    }

    private fun write(values: Set<String>) {
        try {
            Files.createDirectories(file.parent)
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), values.toList().sorted())
        } catch (_: Throwable) {
            // ignore
        }
    }
}
