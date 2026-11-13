# Repository Guidelines

## Project Structure & Module Organization

This Kotlin/JVM plugin displays PHPStan baseline entries in IntelliJ-based IDEs.

- `src/main/kotlin/dev/baselinehighlighter/`: `core/` parses NEON, resolves paths, and matches entries; `index/` maintains project state; `editor/` provides markers and navigation; `settings/` manages sources.
- `php/jetbrains/` and `php/openphp/` contain optional PHP adapters behind `PhpDeclarationProvider`. Keep vendor PSI dependencies inside these integrations.
- `src/main/resources/`: plugin descriptors in `META-INF/`, icons, and English/Russian message bundles.
- `src/test/kotlin/`: core unit tests, IDE fixture tests, and a benchmark. Fixtures are created inline.
- `tools/prepare-local-sdk.py`: prepares a compatible local SDK view for Raketa. See `README.md` for setup and `VALIDATION.md` for recorded checks.

## Build, Test, and Development Commands

Use JDK 21 and the Gradle Wrapper; initial builds need network access for SDKs and dependencies.

- `./gradlew test buildPlugin`: run tests and produce the installable ZIP in `build/distributions/`.
- `./gradlew runIde`: launch a sandbox IDE.
- `./gradlew verifyPluginStructure verifyPluginProjectConfiguration`: validate packaging and configuration.
- `./gradlew verifyPlugin -PverificationIdePath=/absolute/path/to/PhpStorm`: check compatibility against an installed IDE.
- `./gradlew benchmarkBaselines -PbaselineDirectory=/path/to/baselines`: benchmark parsing without running PHPStan.

Default tests use JetBrains PHP. See `README.md` for SDK paths required by `-PphpBackend=openphp` and `-PphpBackend=none`.

## Coding Style & Naming Conventions

Follow existing Kotlin style: four-space indentation, `PascalCase` types/files, `camelCase` functions/properties, and lowercase packages under `dev.baselinehighlighter`. No standalone formatter or linter is configured. Put user-facing strings in both `BaselineBundle.properties` and `BaselineBundle_ru.properties`.

Preserve local, read-only baseline processing: do not invoke PHPStan or execute baseline message regexes.

## Testing Guidelines

Use JUnit 4 for core tests and IntelliJ `BasePlatformTestCase` for integration tests. Name classes `*Test`; follow existing backtick descriptions for annotated unit tests and `testBehavior...` methods for fixture tests. Cover parser edge cases, ownership matching, invalidation, and navigation as relevant. No coverage threshold is configured. Reports appear in `build/reports/tests/<backend>/index.html`. Check visible UI changes manually in a sandbox.

## Commit & Pull Request Guidelines

Git metadata is absent from this checkout, so historical commit conventions cannot be verified. Use concise, imperative commit subjects. PRs should describe behavior changes, link relevant issues, list checks and tested PHP backends, and include screenshots for UI changes. Keep generated SDKs, caches, and build outputs out of commits.
