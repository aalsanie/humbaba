pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        kotlin("jvm") version "1.9.23"
        kotlin("plugin.serialization") version "1.9.23"
        id("org.jetbrains.intellij.platform") version "2.10.5"
        id("com.diffplug.spotless") version "8.1.0"
    }
}

rootProject.name = "humbaba"

include(":core")
include(":intellij-plugin")
include(":humbaba")
include(":maven-plugin")
include(":cli")
