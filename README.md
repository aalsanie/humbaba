<p align="left">
  <img src="assets/humbaba.svg" alt="Humbaba Logo" width="180"/>
</p>

[![current release](https://img.shields.io/badge/release-1.0.1-green)](https://github.com/aalsanie/shamash/releases) | [![install plugin](https://img.shields.io/badge/jetbrains-plugin-red)](https://plugins.jetbrains.com/plugin/29549-humbaba) | [![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)


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

## Usage
- Export your open api key or save it using password save
- Go to: Settings / Preferences → Tools → Humbaba Formatter (your plugin settings page)
- Paste your OpenAI key in the OpenAI API Key field
- Click Save Key
- Right-click inside an editor → **Humbaba: Format Single File**
- Right-click in editor or Project view → **Humbaba: Format All Files**
- Tools → **Humbaba Formatter** → **Humbaba: Format All Files**

### Usage
- Right-click in editor or Project view → Humbaba: Format All Files
- Tools → Humbaba Formatter → Format All Files
  - Dry-Run & Preview
- Humbaba shows a dry-run summary
- You can preview diffs per file
- Nothing is written until you explicitly confirm

## Build & Run

Run

```bash
gradlew.bat spotlessApply
gradlew runIde
```

Build & Verify

```bash
gradlew runPluginVerifier
gradlew buildPlugin
```

License
[license](LICENSE.md)

Changelog
[changelog](CHANGELOG.md)