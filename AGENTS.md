# AGENTS.md

## What this is

IntelliJ IDEA plugin (JetBrains Marketplace) that streams OpenRouter AI responses directly into the editor. Kotlin, single-module Gradle project. Plugin ID: `com.github.roscrl.inlineaichat`.

## Build & verify

```bash
# Build plugin zip
./gradlew buildPlugin

# Run tests (IntelliJ test framework, uses BasePlatformTestCase)
./gradlew check

# Verify plugin structure against IntelliJ Platform
./gradlew verifyPlugin

# Full CI pipeline order: buildPlugin -> check -> verifyPlugin
```

Requires **Java 21** (jvmToolchain in build.gradle.kts), though CI uses Java 17 for setup (Gradle resolves toolchain separately). If builds fail locally, check `JAVA_HOME` matches a JDK 21 install.

## Project structure

All source under `src/main/kotlin/com/github/roscrl/inlineaichat/`:

- `actions/StreamTextAction.kt` — core action: reads editor content, POSTs to OpenRouter SSE endpoint, streams response back into document. This is the main file.
- `actions/QuickSettingsAction.kt` — opens settings dialog
- `settings/InlineAIChatSettingsState.kt` — persistent settings (API key, model, system prompt)
- `settings/InlineAIChatSettingsConfigurable.kt` — settings UI panel
- `config/ModelConfig.kt` — model list configuration
- `notifications/NotificationUtil.kt` — balloon notifications

Plugin manifest: `src/main/resources/META-INF/plugin.xml`

## Key quirks

- **Plugin description** is extracted from `README.md` between `<!-- Plugin description -->` markers. Edit those markers, not the XML in plugin.xml.
- **Changelog** uses `keepachangelog` format in `CHANGELOG.md`. The Gradle `patchChangelog` task updates `[Unreleased]` section.
- **Publishing** requires env vars: `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`, `PUBLISH_TOKEN`. Version determines release channel (pre-release label after `-`).
- **Kotlin serialization plugin** is applied manually in build.gradle.kts (not via version catalog). Version `2.1.0` — keep in sync with Kotlin version if upgrading.
- **OkHttp SSE** is used for streaming (`okhttp-sse` dependency). The 500-minute timeouts are intentional for long streams.
- **Configuration cache is disabled** (`org.gradle.configuration-cache = false` in gradle.properties).

## Testing

Tests extend `BasePlatformTestCase` (IntelliJ light platform test fixture). Test data goes in `src/test/testData/`. The test infrastructure spins up a headless IntelliJ instance — tests are slow to start but don't need external services.
