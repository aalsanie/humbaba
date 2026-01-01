/*
 * Copyright © 2025-2026 | Humbaba
 * Author: @aalsanie
 * Licensed under Apache-2.0
 */
import org.gradle.api.publish.PublishingExtension

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
    id("com.vanniktech.maven.publish") version "0.35.0"
    signing
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
    withSourcesJar()
    // IMPORTANT: do NOT call withJavadocJar() here; Vanniktech publishes its own javadoc jar (plainJavadocJar).
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

// Detect local publish so we don't force signing for publishToMavenLocal
val isLocalPublish = gradle.startParameter.taskNames.any {
    it.contains("publishToMavenLocal", ignoreCase = true)
}

mavenPublishing {
    coordinates(
        "io.github.aalsanie",
        "humbaba-core",
        (findProperty("pluginVersion") as String?) ?: "0.1.0",
    )

    publishToMavenCentral()

    // Sign only for Central/remote publishing, not for Maven Local.
    if (!isLocalPublish) {
        signAllPublications()
    }

    pom {
        name.set("Humbaba Core")
        description.set("Core engine for formatting orchestration and format coverage report")
        url.set("https://github.com/aalsanie/humbaba")

        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("aalsanie")
                name.set("A.ALSANIE")
                url.set("https://github.com/aalsanie")
            }
        }

        scm {
            url.set("https://github.com/aalsanie/humbaba")
            connection.set("scm:git:git://github.com/aalsanie/humbaba.git")
            developerConnection.set("scm:git:ssh://git@github.com/aalsanie/humbaba.git")
        }
    }
}

// Fix Gradle validation: metadata task must depend on produced artifacts
tasks.matching { it.name == "generateMetadataFileForMavenPublication" }
    .configureEach {
        dependsOn(tasks.matching { it.name == "plainJavadocJar" })
        dependsOn(tasks.matching { it.name == "sourcesJar" })
    }

    if (!isLocalPublish) {
    signing {
        val key =
            (findProperty("signingInMemoryKey") as String?)
                ?: System.getenv("SIGNING_KEY")
        val pass =
            (findProperty("signingInMemoryKeyPassword") as String?)
                ?: System.getenv("SIGNING_PASSWORD")

        require(!key.isNullOrBlank()) {
            "Missing signing key. Provide SIGNING_KEY env var or signingInMemoryKey in ~/.gradle/gradle.properties"
        }
        require(!pass.isNullOrBlank()) {
            "Missing signing password. Provide SIGNING_PASSWORD env var or signingInMemoryKeyPassword in ~/.gradle/gradle.properties"
        }

        useInMemoryPgpKeys(key, pass)

        val publishing = extensions.getByType(PublishingExtension::class.java)
        sign(publishing.publications)
    }
    }
