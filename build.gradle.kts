import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.intellij") version "1.17.4"
    id("com.diffplug.spotless") version "8.1.0"
}

group = "io.humbaba"
version = "1.0.0"

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
    kotlinOptions.jvmTarget = "17"
}

tasks {
    patchPluginXml {
        sinceBuild.set("242")
        untilBuild.set("")
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
