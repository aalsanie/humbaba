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
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.intellij") version "1.17.4"
    id("com.diffplug.spotless") version "8.1.0"
}

group = "io.humbaba"
version = "1.0.1"

repositories {
    mavenCentral()
}

intellij {
    version.set("2024.2.0.1")
    type.set("IC")
    plugins.set(listOf())
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks {
    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("252.*")
    }
}

tasks {
    runPluginVerifier {
        ideVersions.set(listOf("2023.3", "2024.1", "2024.2", "2024.3"))
    }
}

spotless {
    kotlin {
        ktlint()

        licenseHeaderFile(rootProject.file("spotless/HEADER.kt"), "package ")
    }

    kotlinGradle {
        ktlint()
    }
}
