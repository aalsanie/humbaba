# Changelog
- Known bugs: go, c, cpp, shell, cmd formatters are not reliable

## [Unreleased] 

## [1.0.1]
### Added
- FormatterMaster, engine, and strategy
  - OpenAI-based formatter recommendation.
  - Native IDE formatter used only as a fallback.
  - Deterministic behavior when AI is unavailable.
  - Strict JSON schema validation for AI responses.
  - AI recommendations restricted to  white listed formatter definitions.
  - Detect low quality output.
- SafetyPolicy
  - Approved install strategies only.
  - Strict argument whitelists.
-  Installer & ConsentPrompt
    - Cross-platform installer support (Windows, macOS, Linux)
    - No arbitrary command execution.
    - User consent is required before auto installing external formatters.
    - Auto installing can be enabled.
    - Managed formatter cache directory.
- Runners
  - Native executables - script execution via interpreter
  - Safe directory handling per file
- Registry
  - `prettier` (JS/TS/JSON/CSS/HTML/Markdown/YAML)
  - `black` (Python)
  - `ruff-format` (Python)
  - `gofmt` (Go)
  - `clang-format` (C/C++)
  - `shfmt` (Shell)
  - `stylua` (Lua)
- Context & Actions
  - Enabled only for writable, non-binary files.
  - Background execution with progress reporting.
- Settings & Configurations
  - Secure OpenAI API key handling via passwordSave or as an env variable.
  - Installation highly configurable e.g. network access toggle
-Errors & reporting
  - step-by-step formatting logs in UI notifications.
  - Accurate success/failure reporting for batch formatting.
  - Fallbacks reporting and explanation.

### Fixed
-  `npm` installs using `--prefix` to avoid global state.
-  `python` formatter installation via isolated virtual environments
-  `Go` formatter installation using controlled `GOBIN`
-  Replaced deprecated kotlinOptions with compilerOptions