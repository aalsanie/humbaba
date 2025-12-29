<p align="left">
  <img src="assets/humbaba.svg" alt="Humbaba Logo" width="180"/>
</p>

[![current release](https://img.shields.io/badge/release-1.0.1-green)](https://github.com/aalsanie/shamash/releases) | [![install plugin](https://img.shields.io/badge/jetbrains-plugin-red)](https://plugins.jetbrains.com/plugin/29549-humbaba) | [![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)


# Humbaba

AI based formatter that uses a deterministic, heuristic and AI scoring strategy to format the whole project.
Reports back format coverage percentage in `xml`, `json` and `html` format missing and formated files.

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

## How it works
- Prefer AI + allow-listed external formatter FIRST (best quality, consistent across IDEs).
- If AI/external cannot run for any reason, fall back to IDE native formatter.
- IDE formatters can "succeed" while producing low-quality output (e.g., one-line JS).
- AI is constrained to our allow-list and validated by SafetyPolicy; it selects best stable tool.
- If native formatting is unavailable or fails, **AI recommends** a safe allow-listed external formatter.
- The plugin installs & runs the external formatter using trusted strategies.
- Refreshes file system.

## Build & Run

Run

```bash
export OPENAI_API_KEY="$YOUR_KEY"
gradlew.bat spotlessApply
gradlew runIde
```

Build & Verify

```bash
gradlew runPluginVerifier
gradlew buildPlugin
```

## Modules

- `platform/` IntelliJ UI, actions, settings, tool window
- `domain/` orchestration + models + ports
- `intellij-adapter/` IntelliJ implementations (formatting, VFS, installers)
- `ai/` OpenAI Responses API client + strict JSON parsing + caching
- `formatters/` allow-list registry + safety validation

## Strategy

- AI can only select from the allow-list if a project isn't tied to any formatters.
- Strategy & args validated against tool definition.
- Binary downloads require pinned official URL + SHA-256 checksum (`BinaryPins.kt`), empty by default.

License
[license](LICENSE.md)