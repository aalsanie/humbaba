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
package io.humbaba.runner.adapters

import io.humbaba.domains.ports.FileContentWriter
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

class DiskFileContentWriter : FileContentWriter {
    override fun writeText(filePath: String, newText: String): Boolean {
        Files.writeString(Path.of(filePath), newText)
        return true
    }
}

