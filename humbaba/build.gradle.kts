plugins {
    kotlin("jvm")
    id("com.gradle.plugin-publish") version "2.0.0"
}

group = "io.github.aalsanie"
version = (findProperty("pluginVersion") as String?) ?: "0.1.0"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
}

gradlePlugin {
    website.set("https://github.com/aalsanie/humbaba")
    vcsUrl.set("https://github.com/aalsanie/humbaba")

    plugins {
        create("humbaba") {
            id = "io.github.aalsanie.humbaba"
            implementationClass = "io.github.aalsanie.gradle.HumbabaGradlePlugin"
            displayName = "Humbaba"
            description = "Polyglot formatting orchestrator and coverage reporting."
            tags.set(listOf("format", "formatter", "kotlin", "java", "go", "lint", "coverage"))
        }
    }
}
