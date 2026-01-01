<p align="left">
  <img src="assets/humbaba.svg" alt="Humbaba Logo" width="180"/>
</p>

[![current release](https://img.shields.io/badge/release-1.0.1-green)](https://github.com/aalsanie/shamash/releases) | [![install plugin](https://img.shields.io/badge/jetbrains-plugin-red)](https://plugins.jetbrains.com/plugin/29573-humbaba) | [![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)


# Humbaba

A safe, deterministic formatting orchestrator for polyglot repositories.

### What Humbaba Solves
Large repositories often contain multiple languages, each with:
- different formatting tools
- different conventions
- inconsistent developer environments

Humbaba provides:
- One-click formatting across the entire repository
- Coverage reporting (what was formatted, how, and why)
- Safety-first execution of external tools
- Dry-run + diff preview before any destructive change

## Current supported languages & extensions
- Python
- C
- C++
- Shell
- Go
- Lua
- Java
- Kotlin
- (JS/TS/JSON/CSS/HTML/Markdown/YAML)

### Usage

### Humbaba Intellij Plugin
- Right-click in editor or Project view → Humbaba: Format All Files
- Tools → Humbaba Formatter → Format All Files
  - Dry-Run & Preview
- Humbaba shows a dry-run summary
- You can preview diffs per file
- Nothing is written until you explicitly confirm
- Reports are generated under .humbaba/reports

### Gradle Plugin (`io.humbaba.gradle`)

```kotlin
plugins {
    id("io.humbaba.gradle") version "2.0.0"
}

humbaba {
    // examples
    dryRun.set(false)   // compute diffs + reports without leaving changes behind
    preview.set(false)  // print diff previews
    ai.set(false)       // experimental AI assistance (requires OPENAI_API_KEY)
    yes.set(false)      // non-interactive consent prompts
}
```

Run
```shell
gradlew humbabaFormat
```

### Maven Plugin
```xml
<build>
  <plugins>
    <plugin>
      <groupId>io.github.aalsanie</groupId>
      <artifactId>humbaba</artifactId>
      <version>2.0.0</version>
      <executions>
        <execution>
          <goals>
            <goal>format</goal>
          </goals>
        </execution>
      </executions>
      <configuration>
        <!-- optional -->
        <dryRun>false</dryRun>
        <preview>false</preview>
        <ai>false</ai>
        <yes>false</yes>
        <!-- root defaults to ${project.basedir} if not set -->
        <!-- <root>${project.basedir}</root> -->
      </configuration>
    </plugin>
  </plugins>
</build>
```

```shell
mvn -DskipTests humbaba:format
```

### Humbaba core
```kotlin
dependencies {
    implementation("io.github.aalsanie:humbaba-core:2.0.0")
}
```

```xml
<dependency>
  <groupId>io.github.aalsanie</groupId>
  <artifactId>humbaba-core</artifactId>
  <version>2.0.0</version>
</dependency>

```

License
[license](LICENSE.md)

Changelog
[changelog](CHANGELOG.md)