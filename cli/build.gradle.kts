plugins {
    kotlin("jvm")
    application
}

group = "io.humbaba"
version = (findProperty("pluginVersion") as String?) ?: "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))

    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
}

application {
    mainClass.set("io.humbaba.cli.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
