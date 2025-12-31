plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(project(":core"))
}

gradlePlugin {
    plugins {
        create("humbaba") {
            id = "io.humbaba.gradle"
            implementationClass = "io.humbaba.gradle.HumbabaGradlePlugin"
            displayName = "Humbaba Formatter"
            description = "Polyglot formatting orchestrator + coverage reporting."
        }
    }
}

publishing {
    publications {
        // java-gradle-plugin registers publications automatically; keep block for future customization.
    }
}


tasks.register("format") {
    group = "humbaba"
    description = "Alias for humbabaFormat (so you can run ./gradlew humbaba:format)."
    dependsOn("humbabaFormat")
}
