# Plan: Custom OpenAI-Compatible API Endpoints

## Goal

Allow the plugin to work with any OpenAI-compatible endpoint (Ollama, Headroom proxy, LM Studio, vLLM, etc.) instead of only OpenRouter. Headroom integration is free: users point the base URL at `http://localhost:8787/api/v1` and get compression automatically.

## What changes

### 1. Settings state — add `apiBaseUrl`, keep `openRouterApiKey` optional

**File:** `src/main/kotlin/com/github/roscrl/inlineaichat/settings/InlineAIChatSettingsState.kt`

- Add `var apiBaseUrl: String = "https://openrouter.ai/api/v1"` — persisted, defaults to OpenRouter
- Keep `openRouterApiKey` as-is (rename not needed, it's serialized to XML — renaming breaks existing user configs)

### 2. StreamTextAction — build URL from base URL, skip auth when empty

**File:** `src/main/kotlin/com/github/roscrl/inlineaichat/actions/StreamTextAction.kt`

- Replace hardcoded `.url("https://openrouter.ai/api/v1/chat/completions")` with:
  ```kotlin
  .url("${settings.apiBaseUrl.trimEnd('/')}/chat/completions")
  ```
- Make `Authorization` header conditional — only add when `openRouterApiKey.isNotEmpty()`:
  ```kotlin
  .apply {
      if (settings.openRouterApiKey.isNotEmpty()) {
          addHeader("Authorization", "Bearer ${settings.openRouterApiKey}")
      }
  }
  ```
- Update the "API key not configured" message to be provider-aware (only show when base URL suggests OpenRouter or key is clearly needed)

### 3. Settings UI — add base URL field, relax API key requirement

**File:** `src/main/kotlin/com/github/roscrl/inlineaichat/settings/InlineAIChatSettingsConfigurable.kt`

- Add `apiBaseUrl` field (JBTextField) in a new "Provider" group above the existing "OpenRouter" group
- Rename "OpenRouter" group to "API Key" — it's now optional for local providers
- Update `isModified()`, `apply()`, `reset()` to include `apiBaseUrl`
- Add `getApiBaseUrl()` / `setApiBaseUrl()` accessors
- In `InlineAIChatSettingsComponent`: add `apiBaseUrlField` to the panel, add getter/setter
- Update the API key empty text to: "Optional for local providers (Ollama, Headroom)"
- Make the OpenRouter link conditional — only show when base URL contains "openrouter"

### 4. ManageModelsDialog — make OpenRouter links conditional

**File:** `src/main/kotlin/com/github/roscrl/inlineaichat/settings/InlineAIChatSettingsConfigurable.kt`

- In `ManageModelsDialog.createCenterPanel()`: check `apiBaseUrl` — if it contains "openrouter", show the OpenRouter models link; otherwise hide it or show a generic note

### 5. QuickSettingsAction — same conditional link treatment

**File:** `src/main/kotlin/com/github/roscrl/inlineaichat/actions/QuickSettingsAction.kt`

- In `createModelsPanel()`: make the "View available models on OpenRouter" link conditional on the current `apiBaseUrl`

### 6. Default models — add an Ollama-friendly default set

**File:** `src/main/resources/models.json`

- Keep existing OpenRouter defaults as-is
- No structural change needed — users add Ollama models via "Manage Models" or "Add Custom"

## Files touched (in order)

1. `InlineAIChatSettingsState.kt` — add `apiBaseUrl` field
2. `StreamTextAction.kt` — use `apiBaseUrl`, conditional auth header
3. `InlineAIChatSettingsConfigurable.kt` — UI changes (base URL field, conditional links)
4. `QuickSettingsAction.kt` — conditional OpenRouter link

## Verification

1. `./gradlew buildPlugin` — must succeed
2. `./gradlew check` — tests must pass
3. Manual: set base URL to `http://localhost:11434/v1`, add model `llama3`, verify request goes to Ollama
4. Manual: set base URL to `http://localhost:8787/api/v1` (Headroom proxy), verify compression works
5. Manual: leave base URL as OpenRouter default, verify existing behavior unchanged

## Risks

- **Existing user configs**: `openRouterApiKey` XML serialization must not break. We're only adding a new field (`apiBaseUrl`), not renaming existing ones.
- **Ollama model names**: Ollama uses plain names like `llama3`, not OpenRouter's `provider/model` format. The plugin already supports arbitrary model strings via "Add Custom" — no code change needed.
- **Ollama response format**: Ollama's OpenAI-compatible endpoint returns standard `choices[].delta.content` SSE format, which the plugin already handles. The `thinking` field from some models is not parsed, but that's a separate feature.
