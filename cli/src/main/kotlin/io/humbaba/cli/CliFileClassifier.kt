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

import io.humbaba.domains.ports.FileClassifier
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

class CliFileClassifier : FileClassifier {
    override fun classify(filePath: String): Pair<String, String?> {
        val p = Path.of(filePath)
        val ext = p.extension.lowercase()
        return ext to null
    }

    override fun sample(filePath: String, maxChars: Int): String? {
        return try {
            val p = Path.of(filePath)
            if (!Files.exists(p) || Files.isDirectory(p)) return null
            val text = Files.readString(p)
            if (text.length <= maxChars) text else text.substring(0, maxChars)
        } catch (_: Throwable) {
            null
        }
    }
}
