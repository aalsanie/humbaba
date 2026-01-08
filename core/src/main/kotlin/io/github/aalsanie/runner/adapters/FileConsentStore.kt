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
package io.github.aalsanie.runner.adapters

import io.github.aalsanie.domains.ports.ConsentStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

class FileConsentStore(private val file: Path) : ConsentStore {

    private val trusted = mutableSetOf<String>()

    init {
        if (file.exists()) {
            trusted += Files.readAllLines(file)
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
        }
    }

    override fun isFormatterTrusted(formatterId: String): Boolean = formatterId in trusted

    override fun trustFormatter(formatterId: String) {
        if (trusted.add(formatterId)) persist()
    }

    override fun untrustFormatter(formatterId: String) {
        if (trusted.remove(formatterId)) persist()
    }

    override fun trustedFormatters(): Set<String> = trusted.toSet()

    private fun persist() {
        Files.createDirectories(file.parent)
        Files.writeString(file, trusted.sorted().joinToString("\n") + "\n")
    }
}
