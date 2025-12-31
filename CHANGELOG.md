# Changelog

## [Unreleased] 

## [1.1.0]
### Added
- Upgrade gradle to 8.13 and use `intellij.platform`
- Ensured formatter execution and file mutations are fully compliant with IntelliJ PSI/VFS lifecycle guarantees.
- Added a dry-run phase for “Format All Files” to compute coverage and eligibility without modifying files.
- Introduced a basic diff preview before applying repository-wide formatting.
- Users must explicitly confirm before any destructive changes are written.
### Fixed
- Hardened external formatter execution
- AI formatting is now strictly opt-in per run and disabled by default.
- Changed calculation to be reproducible.
- Improved separation between planning, execution, and write phases.
- Formatter selection and execution decisions are now explainable and auditable.
- Improved cancellation behavior and responsiveness during long-running format operations.

### Removed
-Removed all direct disk writes during formatting.

## [1.0.1]
### Added
- Deterministic and heuristic coverage scoring system
- AI coverage scoring system
- Humbaba hybrid coverage scoring system to apply proper formats in edge cases 
- Format coverage report generator 
- Coverage score = `(formatted_files_count / total_files_attempted) * 100`
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
- `npm` installs using `--prefix` to avoid global state.
- `python` formatter installation via isolated virtual environments
- `Go` formatter installation using controlled `GOBIN`
- Replaced deprecated kotlinOptions with compilerOptions
- Fixed bugs in : go, c, cpp, shell, cmd formatters are not being applied due to installation (implemented fallbacks)