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
package io.humbaba.runner

import io.humbaba.domains.model.IdeInfo
import io.humbaba.domains.model.OsInfo

object EnvInfo {

    fun ideInfo(): IdeInfo {
        val productName = System.getProperty("humbaba.ide.productName") ?: "Humbaba-CLI"
        val productCode = System.getProperty("humbaba.ide.productCode") ?: "HB"
        val buildNumber = System.getProperty("humbaba.ide.buildNumber") ?: "0"

        return IdeInfo(
            productName = productName,
            productCode = productCode,
            buildNumber = buildNumber,
        )
    }

    fun osInfo(): OsInfo {
        val name = System.getProperty("os.name") ?: "unknown"
        val version = System.getProperty("os.version") ?: "unknown"
        val arch = System.getProperty("os.arch") ?: "unknown"

        return OsInfo(
            name = name,
            version = version,
            arch = arch,
        )
    }
}
