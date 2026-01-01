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
package io.github.aalsanie.platform

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.SystemInfo
import io.github.aalsanie.domains.model.IdeInfo
import io.github.aalsanie.domains.model.OsInfo

object IntellijEnv {
    fun ideInfo(): IdeInfo {
        val info = ApplicationInfo.getInstance()
        return IdeInfo(
            productCode = info.build.productCode,
            productName = info.versionName,
            buildNumber = info.build.asString(),
        )
    }

    fun osInfo(): OsInfo {
        val name =
            when {
                SystemInfo.isWindows -> "windows"
                SystemInfo.isMac -> "macos"
                SystemInfo.isLinux -> "linux"
                else -> SystemInfo.OS_NAME.lowercase()
            }
        return OsInfo(name = name, arch = SystemInfo.OS_ARCH, version = SystemInfo.OS_VERSION)
    }
}
