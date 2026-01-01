/*
 * Copyright © 2025-2026 | Humbaba
 * Author: @aalsanie
 * Licensed under Apache-2.0
 */
import org.gradle.api.publish.PublishingExtension

plugins {
    kotlin("jvm")
    application
    `java-library`
    id("com.vanniktech.maven.publish") version "0.35.0"
    signing
}

group = "io.github.aalsanie"
version = (findProperty("pluginVersion") as String?) ?: "0.1.0"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))

    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
}

application {
    applicationName = "humbaba"
    mainClass.set("io.github.aalsanie.cli.MainKt")
}

// Detect local publish so we don't force signing for publishToMavenLocal
val isLocalPublish = gradle.startParameter.taskNames.any {
    it.contains("publishToMavenLocal", ignoreCase = true)
}

mavenPublishing {
    coordinates(
        "io.github.aalsanie",
        "humbaba-cli",
        version.toString(),
    )

    publishToMavenCentral()

    // Sign only for Central/remote publishing, not for Maven Local.
    if (!isLocalPublish) {
        signAllPublications()
    }

    pom {
        name.set("Humbaba CLI")
        description.set("Humbaba command-line formatter and coverage reporting tool.")
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

        // Sign all publications created by the publish plugin (no hardcoded "maven" name)
        val publishing = extensions.getByType(PublishingExtension::class.java)
        sign(publishing.publications)
    }
}

tasks.test {
    useJUnitPlatform()
}

//archived names
tasks.withType<Zip>().configureEach {
    if (name == "distZip") archiveBaseName.set("humbaba")
}
tasks.withType<Tar>().configureEach {
    if (name == "distTar") archiveBaseName.set("humbaba")
}