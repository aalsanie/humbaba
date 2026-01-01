/* Copyright © 2025-2026 | Author: @aalsanie
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

plugins {
    kotlin("jvm") version "1.9.23" apply false
    kotlin("plugin.serialization") version "1.9.23" apply false
    id("org.jetbrains.intellij.platform") version "2.5.0" apply false

    id("com.diffplug.spotless") version "8.1.0"
}

allprojects {
    group = "io.github.aalsanie"
    version = (findProperty("pluginVersion") as String?) ?: "0.1.0"

    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3")
            force("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
        }
    }
}
