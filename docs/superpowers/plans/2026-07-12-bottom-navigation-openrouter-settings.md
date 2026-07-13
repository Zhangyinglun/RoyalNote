# Bottom Navigation and OpenRouter Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `主页`、`分析`、`设置` bottom navigation, persist one shared OpenRouter API key/model/per-model effort configuration, make import use it, and show the current key's UTC-month `usage_monthly`.

**Architecture:** Keep the existing single-activity Navigation Compose 2 app. Add a pure settings domain and repository backed by private `SharedPreferences`, inject request snapshots into the import and usage clients, and isolate usage loading/error state in `SettingsViewModel`. Compose screens remain stateless and receive state/callbacks; the activity performs manual wiring exactly as the current app does.

**Tech Stack:** Kotlin 2.2.10, Android 36.1, minSdk 33, AGP 9.2.1, Compose BOM 2026.02.01, Material 3, Navigation Compose 2.9.8, Kotlin coroutines/StateFlow, OkHttp 5.0.0-alpha.16, kotlinx.serialization 1.9.0, JUnit 4, Compose UI tests.

## Global Constraints

- Preserve the user-facing title `起居注` and all existing Chinese text asserted by tests, including the mood labels and both `未输入` nodes.
- Preserve all current add/display/edit/delete, time-range, import, Room, and seed-data behavior.
- Do not add analysis charts, search, filters, cloud sync, export, manual time editing, or Navigation 3.
- Use only these models: `deepseek/deepseek-v4-pro`, `~openai/gpt-latest`, `~google/gemini-pro-latest`.
- Default every model to effort `high`; remember each model's effort separately.
- Allowed efforts are DeepSeek `xhigh/high`, GPT `max/xhigh/high/medium/low/none`, Gemini `high/medium/low`.
- API Key edits, model changes, and effort changes persist immediately; import reads a fresh immutable settings snapshot at request start.
- The usage card displays the current key's `usage_monthly` from `GET /api/v1/key`; it never labels that value as account balance or remaining credits.
- Never add or store a Management Key. Never log or display the saved API Key outside its password field.
- Keep `dynamicColor = false`, Serif typography, the existing Ink Fragrance palette, 8.dp cards, muted bronze accents, and no bright colors.
- The Git root is `C:\Users\zhang`, while the project is `C:\Users\zhang\AndroidStudioProjects\RoyalNote`.
- The working tree already contains unrelated user changes in several files touched by this plan. Before every edit, inspect the path-limited diff; at commit time use `git add -p` for pre-existing dirty files and stage only this feature's hunks. Never stage unrelated modifications.

## File Map

| File | Responsibility |
|---|---|
| `app/src/main/java/com/example/royalnote/settings/AnalysisModel.kt` | Fixed model catalog and supported effort matrix. |
| `app/src/main/java/com/example/royalnote/settings/SettingsRepository.kt` | Pure settings state, validation, persistence abstraction, SharedPreferences adapter, request snapshot provider. |
| `app/src/main/java/com/example/royalnote/network/OpenRouterService.kt` | Import request uses the latest settings snapshot. |
| `app/src/main/java/com/example/royalnote/network/OpenRouterUsageService.kt` | `/api/v1/key` request and `usage_monthly` parsing. |
| `app/src/main/java/com/example/royalnote/ui/SettingsViewModel.kt` | Combines saved settings with usage loading/success/error state. |
| `app/src/main/java/com/example/royalnote/ui/SettingsScreen.kt` | Ink Fragrance settings UI. |
| `app/src/main/java/com/example/royalnote/ui/AnalysisScreen.kt` | Blank analysis destination with title only. |
| `app/src/main/java/com/example/royalnote/ui/AppNavigation.kt` | Four routes and the three-item bottom navigation shell. |
| `app/src/main/java/com/example/royalnote/MainActivity.kt` | Manual dependency wiring and route content. |
| `app/src/main/res/drawable/ic_*.xml` | Code-native navigation, visibility, and refresh icons. |
| JVM and Compose test files named below | Test settings, requests, usage state, UI states, navigation, and regression behavior. |

---

### Task 1: Settings domain, validation, and persistence

**Files:**
- Create: `app/src/main/java/com/example/royalnote/settings/AnalysisModel.kt`
- Create: `app/src/main/java/com/example/royalnote/settings/SettingsRepository.kt`
- Create: `app/src/test/java/com/example/royalnote/settings/SettingsRepositoryTest.kt`
- Modify: `app/src/main/res/xml/data_extraction_rules.xml`

**Interfaces:**
- Consumes: no feature-specific interface.
- Produces: `ReasoningEffort`, `AnalysisModel`, `AppSettings`, `OpenRouterRequestSettings`, `OpenRouterSettingsProvider.currentSettings()`, `SettingsStorage`, `SharedPreferencesSettingsStorage`, and `SettingsRepository`.

- [ ] **Step 1: Write failing model-matrix and persistence tests**

Create `SettingsRepositoryTest.kt` with the following cases and fake storage:

```kotlin
package com.example.royalnote.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryTest {
    @Test
    fun defaultsUseDeepSeekAndHighForEveryModel() {
        val repository = SettingsRepository(FakeSettingsStorage())

        assertEquals(AnalysisModel.DEEPSEEK_V4_PRO, repository.settings.value.selectedModel)
        AnalysisModel.entries.forEach { model ->
            assertEquals(ReasoningEffort.HIGH, repository.settings.value.effortFor(model))
        }
    }

    @Test
    fun modelCatalogExposesOnlySupportedEfforts() {
        assertEquals(
            listOf(ReasoningEffort.XHIGH, ReasoningEffort.HIGH),
            AnalysisModel.DEEPSEEK_V4_PRO.supportedEfforts,
        )
        assertEquals(
            listOf(
                ReasoningEffort.MAX,
                ReasoningEffort.XHIGH,
                ReasoningEffort.HIGH,
                ReasoningEffort.MEDIUM,
                ReasoningEffort.LOW,
                ReasoningEffort.NONE,
            ),
            AnalysisModel.GPT_LATEST.supportedEfforts,
        )
        assertEquals(
            listOf(ReasoningEffort.HIGH, ReasoningEffort.MEDIUM, ReasoningEffort.LOW),
            AnalysisModel.GEMINI_PRO_LATEST.supportedEfforts,
        )
    }

    @Test
    fun changesPersistAndReloadWithPerModelEffortMemory() {
        val storage = FakeSettingsStorage()
        val repository = SettingsRepository(storage)

        repository.updateApiKey(" sk-or-v1-test ")
        repository.selectModel(AnalysisModel.GPT_LATEST)
        repository.selectEffort(AnalysisModel.GPT_LATEST, ReasoningEffort.MAX)
        repository.selectEffort(AnalysisModel.DEEPSEEK_V4_PRO, ReasoningEffort.XHIGH)

        val reloaded = SettingsRepository(storage)
        assertEquals(" sk-or-v1-test ", reloaded.settings.value.apiKey)
        assertEquals(AnalysisModel.GPT_LATEST, reloaded.settings.value.selectedModel)
        assertEquals(ReasoningEffort.MAX, reloaded.settings.value.effortFor(AnalysisModel.GPT_LATEST))
        assertEquals(ReasoningEffort.XHIGH, reloaded.settings.value.effortFor(AnalysisModel.DEEPSEEK_V4_PRO))
    }

    @Test
    fun unsupportedEffortIsIgnoredAndInvalidStoredValuesFallBack() {
        val storage = FakeSettingsStorage(
            mutableMapOf(
                "selected_model" to "removed-model",
                "effort_deepseek-v4-pro" to "low",
                "effort_gpt-latest" to "removed-effort",
            )
        )
        val repository = SettingsRepository(storage)

        assertEquals(AnalysisModel.DEEPSEEK_V4_PRO, repository.settings.value.selectedModel)
        assertEquals(ReasoningEffort.HIGH, repository.settings.value.effortFor(AnalysisModel.DEEPSEEK_V4_PRO))
        assertEquals(ReasoningEffort.HIGH, repository.settings.value.effortFor(AnalysisModel.GPT_LATEST))

        repository.selectEffort(AnalysisModel.DEEPSEEK_V4_PRO, ReasoningEffort.LOW)
        assertEquals(ReasoningEffort.HIGH, repository.settings.value.effortFor(AnalysisModel.DEEPSEEK_V4_PRO))
    }

    @Test
    fun currentSettingsReturnsTrimmedImmutableRequestSnapshot() {
        val repository = SettingsRepository(FakeSettingsStorage())
        repository.updateApiKey("  sk-or-v1-test  ")
        repository.selectModel(AnalysisModel.GEMINI_PRO_LATEST)
        repository.selectEffort(AnalysisModel.GEMINI_PRO_LATEST, ReasoningEffort.LOW)

        assertEquals(
            OpenRouterRequestSettings(
                apiKey = "sk-or-v1-test",
                modelId = "~google/gemini-pro-latest",
                effort = "low",
            ),
            repository.currentSettings(),
        )
    }
}

private class FakeSettingsStorage(
    private val values: MutableMap<String, String> = mutableMapOf(),
) : SettingsStorage {
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) {
        values[key] = value
    }
}
```

- [ ] **Step 2: Run the new test and verify RED**

Run:

```powershell
.\gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.settings.SettingsRepositoryTest"
```

Expected: compilation fails because the `settings` package types do not exist.

- [ ] **Step 3: Implement the fixed model catalog**

Create `AnalysisModel.kt`:

```kotlin
package com.example.royalnote.settings

enum class ReasoningEffort(val wireValue: String) {
    MAX("max"),
    XHIGH("xhigh"),
    HIGH("high"),
    MEDIUM("medium"),
    LOW("low"),
    NONE("none");

    companion object {
        fun fromWireValue(value: String?): ReasoningEffort? = entries.firstOrNull {
            it.wireValue == value
        }
    }
}

enum class AnalysisModel(
    val storageValue: String,
    val displayName: String,
    val openRouterId: String,
    val supportedEfforts: List<ReasoningEffort>,
) {
    DEEPSEEK_V4_PRO(
        storageValue = "deepseek-v4-pro",
        displayName = "DeepSeek V4 Pro",
        openRouterId = "deepseek/deepseek-v4-pro",
        supportedEfforts = listOf(ReasoningEffort.XHIGH, ReasoningEffort.HIGH),
    ),
    GPT_LATEST(
        storageValue = "gpt-latest",
        displayName = "GPT Latest",
        openRouterId = "~openai/gpt-latest",
        supportedEfforts = listOf(
            ReasoningEffort.MAX,
            ReasoningEffort.XHIGH,
            ReasoningEffort.HIGH,
            ReasoningEffort.MEDIUM,
            ReasoningEffort.LOW,
            ReasoningEffort.NONE,
        ),
    ),
    GEMINI_PRO_LATEST(
        storageValue = "gemini-pro-latest",
        displayName = "Gemini Pro Latest",
        openRouterId = "~google/gemini-pro-latest",
        supportedEfforts = listOf(
            ReasoningEffort.HIGH,
            ReasoningEffort.MEDIUM,
            ReasoningEffort.LOW,
        ),
    );

    companion object {
        fun fromStorageValue(value: String?): AnalysisModel? = entries.firstOrNull {
            it.storageValue == value
        }
    }
}
```

- [ ] **Step 4: Implement validated state and SharedPreferences persistence**

Create `SettingsRepository.kt`:

```kotlin
package com.example.royalnote.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val apiKey: String = "",
    val selectedModel: AnalysisModel = AnalysisModel.DEEPSEEK_V4_PRO,
    val efforts: Map<AnalysisModel, ReasoningEffort> = AnalysisModel.entries.associateWith {
        ReasoningEffort.HIGH
    },
) {
    fun effortFor(model: AnalysisModel): ReasoningEffort = efforts[model]
        ?.takeIf { it in model.supportedEfforts }
        ?: ReasoningEffort.HIGH

    val selectedEffort: ReasoningEffort get() = effortFor(selectedModel)
}

data class OpenRouterRequestSettings(
    val apiKey: String,
    val modelId: String,
    val effort: String,
)

fun interface OpenRouterSettingsProvider {
    fun currentSettings(): OpenRouterRequestSettings
}

interface SettingsStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

class SharedPreferencesSettingsStorage(context: Context) : SettingsStorage {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    companion object {
        const val FILE_NAME = "openrouter_settings"
    }
}

class SettingsRepository(
    private val storage: SettingsStorage,
) : OpenRouterSettingsProvider {
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun updateApiKey(value: String) {
        storage.putString(SettingsKeys.API_KEY, value)
        _settings.value = _settings.value.copy(apiKey = value)
    }

    fun selectModel(model: AnalysisModel) {
        storage.putString(SettingsKeys.SELECTED_MODEL, model.storageValue)
        _settings.value = _settings.value.copy(selectedModel = model)
    }

    fun selectEffort(model: AnalysisModel, effort: ReasoningEffort) {
        if (effort !in model.supportedEfforts) return
        storage.putString(SettingsKeys.effort(model), effort.wireValue)
        _settings.value = _settings.value.copy(
            efforts = _settings.value.efforts + (model to effort),
        )
    }

    override fun currentSettings(): OpenRouterRequestSettings {
        val current = _settings.value
        return OpenRouterRequestSettings(
            apiKey = current.apiKey.trim(),
            modelId = current.selectedModel.openRouterId,
            effort = current.selectedEffort.wireValue,
        )
    }

    private fun load(): AppSettings {
        val selectedModel = AnalysisModel.fromStorageValue(
            storage.getString(SettingsKeys.SELECTED_MODEL)
        ) ?: AnalysisModel.DEEPSEEK_V4_PRO
        val efforts = AnalysisModel.entries.associateWith { model ->
            ReasoningEffort.fromWireValue(storage.getString(SettingsKeys.effort(model)))
                ?.takeIf { it in model.supportedEfforts }
                ?: ReasoningEffort.HIGH
        }
        return AppSettings(
            apiKey = storage.getString(SettingsKeys.API_KEY).orEmpty(),
            selectedModel = selectedModel,
            efforts = efforts,
        )
    }
}

internal object SettingsKeys {
    const val API_KEY = "api_key"
    const val SELECTED_MODEL = "selected_model"
    fun effort(model: AnalysisModel): String = "effort_${model.storageValue}"
}
```

- [ ] **Step 5: Exclude the preferences file from extraction rules**

Change both rule blocks in `data_extraction_rules.xml` to include the shared-preference exclusion:

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="openrouter_settings.xml" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="openrouter_settings.xml" />
    </device-transfer>
</data-extraction-rules>
```

- [ ] **Step 6: Run the focused test and verify GREEN**

Run the Step 2 command again. Expected: all five `SettingsRepositoryTest` tests pass.

- [ ] **Step 7: Commit only Task 1 changes**

```powershell
git -C C:\Users\zhang add -- AndroidStudioProjects/RoyalNote/app/src/main/java/com/example/royalnote/settings/AnalysisModel.kt AndroidStudioProjects/RoyalNote/app/src/main/java/com/example/royalnote/settings/SettingsRepository.kt AndroidStudioProjects/RoyalNote/app/src/test/java/com/example/royalnote/settings/SettingsRepositoryTest.kt
git -C C:\Users\zhang add -p -- AndroidStudioProjects/RoyalNote/app/src/main/res/xml/data_extraction_rules.xml
git -C C:\Users\zhang diff --cached --check
git -C C:\Users\zhang commit -m "feat: persist shared OpenRouter settings"
```

---

### Task 2: Make import consume the shared request settings

**Files:**
- Modify: `app/src/main/java/com/example/royalnote/network/OpenRouterConfig.kt`
- Modify: `app/src/main/java/com/example/royalnote/network/OpenRouterService.kt`
- Modify: `app/src/main/java/com/example/royalnote/ui/ImportViewModel.kt`
- Modify: `app/src/main/java/com/example/royalnote/MainActivity.kt`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/test/java/com/example/royalnote/network/OpenRouterConfigTest.kt`
- Modify: `app/src/test/java/com/example/royalnote/network/OpenRouterServiceTest.kt`
- Modify: `app/src/test/java/com/example/royalnote/ui/ImportViewModelTest.kt`

**Interfaces:**
- Consumes: `OpenRouterSettingsProvider.currentSettings(): OpenRouterRequestSettings` from Task 1.
- Produces: `MissingOpenRouterApiKeyException`, `buildChatCompletionRequest`, and the injected `OpenRouterService` constructor.

- [ ] **Step 1: Replace the fixed-config test with selected-config tests**

Replace the old DeepSeek Flash assertion in `OpenRouterConfigTest` with:

```kotlin
@Test
fun importRequestUsesSelectedModelEffortAndLongOutputBudget() {
    val request = buildChatCompletionRequest(
        text = "long import text",
        systemPrompt = "system",
        settings = OpenRouterRequestSettings(
            apiKey = "secret",
            modelId = "~openai/gpt-latest",
            effort = "xhigh",
        ),
    )

    val encoded = Json.encodeToString(ChatCompletionRequest.serializer(), request)
    assertEquals("~openai/gpt-latest", request.model)
    assertEquals("xhigh", request.reasoning?.effort)
    assertEquals(OpenRouterConfig.MAX_OUTPUT_TOKENS, request.max_tokens)
    assertTrue(encoded.contains("\"max_tokens\":${OpenRouterConfig.MAX_OUTPUT_TOKENS}"))
}
```

Add to `OpenRouterServiceTest`:

```kotlin
@Test
fun blankConfiguredKeyFailsBeforeNetwork() = runTest {
    val service = OpenRouterService(
        settingsProvider = OpenRouterSettingsProvider {
            OpenRouterRequestSettings("", "deepseek/deepseek-v4-pro", "high")
        },
    )

    assertFailsWith<MissingOpenRouterApiKeyException> {
        service.parseRecords("昨天读书")
    }
}
```

Add the needed imports for `runTest` and `assertFailsWith`.

Add to `ImportViewModelTest`:

```kotlin
@Test
fun missingApiKeyShowsSettingsMessage() = runTest {
    parser.exception = MissingOpenRouterApiKeyException()
    val viewModel = ImportViewModel(parser, repository, clock)

    viewModel.updateText("一些文字")
    viewModel.importRecords()
    advanceUntilIdle()

    assertEquals("请先在设置中填写 OpenRouter API Key", viewModel.uiState.value.message)
    assertFalse(viewModel.uiState.value.isLoading)
}
```

- [ ] **Step 2: Run the three focused test classes and verify RED**

```powershell
.\gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.network.OpenRouterConfigTest" --tests "com.example.royalnote.network.OpenRouterServiceTest" --tests "com.example.royalnote.ui.ImportViewModelTest"
```

Expected: compilation fails for the missing request builder and missing-key exception.

- [ ] **Step 3: Remove build-time credentials and fixed model constants**

Make `OpenRouterConfig.kt` exactly:

```kotlin
package com.example.royalnote.network

object OpenRouterConfig {
    const val CHAT_COMPLETIONS_URL = "https://openrouter.ai/api/v1/chat/completions"
    const val CURRENT_KEY_URL = "https://openrouter.ai/api/v1/key"
    const val MAX_OUTPUT_TOKENS = 32_768
}
```

In `app/build.gradle.kts`, delete `import java.util.Properties`, the `localProperties`/`openRouterApiKey` block, `buildConfigField("String", "OPENROUTER_API_KEY", "\"$openRouterApiKey\"")`, and `buildFeatures.buildConfig = true`. Keep `buildFeatures { compose = true }`.

- [ ] **Step 4: Refactor the import service around an immutable settings snapshot**

Keep `buildSystemPrompt` unchanged. Change the service constructor and request creation to this shape:

```kotlin
class MissingOpenRouterApiKeyException : IllegalStateException("OpenRouter API key is missing")

class OpenRouterService(
    private val settingsProvider: OpenRouterSettingsProvider,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val client: Call.Factory = defaultOpenRouterClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val chatUrl: String = OpenRouterConfig.CHAT_COMPLETIONS_URL,
) : RecordParser {
    override suspend fun parseRecords(text: String): ParsedRecords = withContext(Dispatchers.IO) {
        val settings = settingsProvider.currentSettings()
        if (settings.apiKey.isBlank()) throw@withContext MissingOpenRouterApiKeyException()
        val requestBody = buildChatCompletionRequest(
            text = text,
            systemPrompt = buildSystemPrompt(clock),
            settings = settings,
        )
        val body = json.encodeToString(ChatCompletionRequest.serializer(), requestBody)
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(chatUrl)
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("API error: ${response.code}")
            val responseBody = response.body.string()
            val apiResponse = json.decodeFromString(ChatCompletionResponse.serializer(), responseBody)
            val content = apiResponse.choices.firstOrNull()?.message?.content
                ?: throw IOException("No content in response")
            json.decodeFromString(ParsedRecords.serializer(), content)
        }
    }
}

internal fun buildChatCompletionRequest(
    text: String,
    systemPrompt: String,
    settings: OpenRouterRequestSettings,
): ChatCompletionRequest = ChatCompletionRequest(
    model = settings.modelId,
    messages = listOf(
        ChatMessage(role = "system", content = systemPrompt),
        ChatMessage(role = "user", content = text),
    ),
    response_format = ResponseFormat(type = "json_object"),
    reasoning = ReasoningConfig(effort = settings.effort, exclude = true),
    max_tokens = OpenRouterConfig.MAX_OUTPUT_TOKENS,
)

private fun defaultOpenRouterClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .callTimeout(180, TimeUnit.SECONDS)
    .build()
```

Import `okhttp3.Call`, `OpenRouterRequestSettings`, and `OpenRouterSettingsProvider`.

- [ ] **Step 5: Map the missing-key error in ImportViewModel**

Inside the parser `try/catch`, insert this catch before `IOException`:

```kotlin
} catch (e: MissingOpenRouterApiKeyException) {
    updateState(operationGeneration) {
        it.copy(message = "请先在设置中填写 OpenRouter API Key")
    }
    return@launch
```

- [ ] **Step 6: Wire the repository into the existing activity without changing navigation yet**

Immediately after `repository` creation in `MainActivity`, add:

```kotlin
val settingsRepository = remember {
    SettingsRepository(SharedPreferencesSettingsStorage(context.applicationContext))
}
val parser = remember { OpenRouterService(settingsRepository) }
```

Delete the old `val parser = remember { OpenRouterService() }` and add settings imports.

- [ ] **Step 7: Run the focused tests and verify GREEN**

Run the Step 2 command. Expected: all tests in the three classes pass, including the new missing-key behavior.

- [ ] **Step 8: Commit only Task 2 feature hunks**

Use `git add -p` for every listed file because all are already dirty in the user's worktree:

```powershell
git -C C:\Users\zhang add -p -- AndroidStudioProjects/RoyalNote/app/build.gradle.kts AndroidStudioProjects/RoyalNote/app/src/main/java/com/example/royalnote/MainActivity.kt AndroidStudioProjects/RoyalNote/app/src/main/java/com/example/royalnote/network/OpenRouterConfig.kt AndroidStudioProjects/RoyalNote/app/src/main/java/com/example/royalnote/network/OpenRouterService.kt AndroidStudioProjects/RoyalNote/app/src/main/java/com/example/royalnote/ui/ImportViewModel.kt AndroidStudioProjects/RoyalNote/app/src/test/java/com/example/royalnote/network/OpenRouterConfigTest.kt AndroidStudioProjects/RoyalNote/app/src/test/java/com/example/royalnote/network/OpenRouterServiceTest.kt AndroidStudioProjects/RoyalNote/app/src/test/java/com/example/royalnote/ui/ImportViewModelTest.kt
git -C C:\Users\zhang diff --cached --check
git -C C:\Users\zhang commit -m "feat: use shared settings for OpenRouter import"
```

---

### Task 3: Monthly usage client and SettingsViewModel

**Files:**
- Create: `app/src/main/java/com/example/royalnote/network/OpenRouterUsageService.kt`
- Create: `app/src/main/java/com/example/royalnote/ui/SettingsViewModel.kt`
- Create: `app/src/test/java/com/example/royalnote/network/OpenRouterUsageServiceTest.kt`
- Create: `app/src/test/java/com/example/royalnote/ui/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository.settings`, `updateApiKey`, `selectModel`, and `selectEffort`.
- Produces: `OpenRouterUsageProvider.monthlyUsage(apiKey)`, `MonthlyUsage`, `InvalidOpenRouterApiKeyException`, `UsageUiState`, `SettingsUiState`, `SettingsViewModel`, and `SettingsViewModelFactory`.

- [ ] **Step 1: Write failing client request/response tests**

Use an injected `Call.Factory` fake that captures the request and returns a constructed OkHttp `Response`. Cover these assertions in `OpenRouterUsageServiceTest`:

```kotlin
@Test
fun monthlyUsageUsesBearerGetAndParsesUsageMonthly() = runTest {
    val calls = FakeCallFactory(code = 200, body = """{"data":{"usage_monthly":12.34}}""")
    val service = OpenRouterUsageService(calls)

    assertEquals(MonthlyUsage(12.34), service.monthlyUsage("sk-or-v1-test"))
    assertEquals("GET", calls.request.method)
    assertEquals(OpenRouterConfig.CURRENT_KEY_URL, calls.request.url.toString())
    assertEquals("Bearer sk-or-v1-test", calls.request.header("Authorization"))
}

@Test
fun unauthorizedResponseThrowsDistinctError() = runTest {
    val service = OpenRouterUsageService(FakeCallFactory(401, "{}"))
    assertFailsWith<InvalidOpenRouterApiKeyException> {
        service.monthlyUsage("bad-key")
    }
}

@Test
fun serverFailureThrowsIOException() = runTest {
    val service = OpenRouterUsageService(FakeCallFactory(500, "{}"))
    assertFailsWith<IOException> {
        service.monthlyUsage("sk-or-v1-test")
    }
}
```

Use this complete fake below the tests:

```kotlin
private class FakeCallFactory(
    private val code: Int,
    private val body: String,
) : Call.Factory {
    lateinit var request: Request

    override fun newCall(request: Request): Call {
        this.request = request
        return FakeCall(request, code, body)
    }
}

private class FakeCall(
    private val capturedRequest: Request,
    private val code: Int,
    private val body: String,
) : Call {
    private var executed = false
    private var canceled = false

    override fun request(): Request = capturedRequest
    override fun execute(): Response {
        executed = true
        return response()
    }
    override fun enqueue(responseCallback: Callback) {
        executed = true
        responseCallback.onResponse(this, response())
    }
    override fun cancel() { canceled = true }
    override fun isExecuted(): Boolean = executed
    override fun isCanceled(): Boolean = canceled
    override fun timeout(): Timeout = Timeout.NONE
    override fun clone(): Call = FakeCall(capturedRequest, code, body)

    private fun response(): Response = Response.Builder()
        .request(capturedRequest)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()
}
```

Import `okhttp3.Call`, `Callback`, `Protocol`, `Request`, `Response`, the media/body companion extensions, and `okio.Timeout`.

- [ ] **Step 2: Write failing SettingsViewModel state tests**

Create a fake usage provider and cover all required transitions:

```kotlin
@Test
fun screenVisibleWithKeyLoadsMonthlyUsage() = runTest {
    repository.updateApiKey("sk-or-v1-test")
    usage.result = MonthlyUsage(12.34)
    val viewModel = SettingsViewModel(repository, usage, clock)

    viewModel.onScreenVisible()
    advanceUntilIdle()

    assertEquals(1, usage.calls)
    assertEquals(UsageUiState.Success(12.34, clock.millis()), viewModel.uiState.value.usage)
}

@Test
fun blankKeyDoesNotCallService() = runTest {
    val viewModel = SettingsViewModel(repository, usage, clock)
    viewModel.onScreenVisible()
    advanceUntilIdle()

    assertEquals(0, usage.calls)
    assertEquals(UsageUiState.MissingKey, viewModel.uiState.value.usage)
}

@Test
fun editingKeyClearsOldUsageWithoutQueryingEveryCharacter() = runTest {
    repository.updateApiKey("old-key")
    usage.result = MonthlyUsage(8.0)
    val viewModel = SettingsViewModel(repository, usage, clock)
    viewModel.refreshUsage()
    advanceUntilIdle()

    viewModel.updateApiKey("new-key")
    advanceUntilIdle()

    assertEquals(1, usage.calls)
    assertEquals(UsageUiState.Ready, viewModel.uiState.value.usage)
}

@Test
fun failedRefreshKeepsLastSuccessfulAmount() = runTest {
    repository.updateApiKey("sk-or-v1-test")
    usage.result = MonthlyUsage(8.0)
    val viewModel = SettingsViewModel(repository, usage, clock)
    viewModel.refreshUsage()
    advanceUntilIdle()

    usage.error = IOException("offline")
    viewModel.refreshUsage()
    advanceUntilIdle()

    assertEquals(
        UsageUiState.Error("用量查询失败，请稍后再试", 8.0, clock.millis()),
        viewModel.uiState.value.usage,
    )
}
```

Also test 401 maps to `API Key 无效，请检查设置` and model/effort callbacks delegate to the repository.

- [ ] **Step 3: Run both new classes and verify RED**

```powershell
.\gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.network.OpenRouterUsageServiceTest" --tests "com.example.royalnote.ui.SettingsViewModelTest"
```

Expected: compilation fails because usage and settings-view-model types do not exist.

- [ ] **Step 4: Implement the usage client**

Create `OpenRouterUsageService.kt`:

```kotlin
package com.example.royalnote.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class MonthlyUsage(val amountUsd: Double)

fun interface OpenRouterUsageProvider {
    suspend fun monthlyUsage(apiKey: String): MonthlyUsage
}

class InvalidOpenRouterApiKeyException : IOException("OpenRouter API key is invalid")

class OpenRouterUsageService(
    private val client: Call.Factory = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val endpoint: String = OpenRouterConfig.CURRENT_KEY_URL,
) : OpenRouterUsageProvider {
    override suspend fun monthlyUsage(apiKey: String): MonthlyUsage = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401) throw@withContext InvalidOpenRouterApiKeyException()
            if (!response.isSuccessful) throw@withContext IOException("Usage API error: ${response.code}")
            val decoded = json.decodeFromString(CurrentKeyResponse.serializer(), response.body.string())
            MonthlyUsage(decoded.data.usageMonthly)
        }
    }
}

@Serializable
private data class CurrentKeyResponse(val data: CurrentKeyData)

@Serializable
private data class CurrentKeyData(
    @SerialName("usage_monthly") val usageMonthly: Double,
)
```

- [ ] **Step 5: Implement SettingsViewModel**

Create `SettingsViewModel.kt` with a combined settings/usage `StateFlow`:

```kotlin
package com.example.royalnote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.royalnote.network.InvalidOpenRouterApiKeyException
import com.example.royalnote.network.OpenRouterUsageProvider
import com.example.royalnote.settings.AnalysisModel
import com.example.royalnote.settings.AppSettings
import com.example.royalnote.settings.ReasoningEffort
import com.example.royalnote.settings.SettingsRepository
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UsageUiState {
    data object Ready : UsageUiState
    data object MissingKey : UsageUiState
    data class Loading(val previousAmount: Double? = null) : UsageUiState
    data class Success(val amount: Double, val updatedAtMillis: Long) : UsageUiState
    data class Error(
        val message: String,
        val previousAmount: Double? = null,
        val previousUpdatedAtMillis: Long? = null,
    ) : UsageUiState
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val usage: UsageUiState = UsageUiState.MissingKey,
)

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val usageProvider: OpenRouterUsageProvider,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val usageState = MutableStateFlow<UsageUiState>(
        if (repository.settings.value.apiKey.isBlank()) UsageUiState.MissingKey else UsageUiState.Ready
    )
    val uiState = combine(repository.settings, usageState) { settings, usage ->
        SettingsUiState(settings, usage)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(repository.settings.value, usageState.value),
    )
    private var usageJob: Job? = null

    fun updateApiKey(value: String) {
        usageJob?.cancel()
        repository.updateApiKey(value)
        usageState.value = if (value.isBlank()) UsageUiState.MissingKey else UsageUiState.Ready
    }

    fun selectModel(model: AnalysisModel) = repository.selectModel(model)
    fun selectEffort(model: AnalysisModel, effort: ReasoningEffort) = repository.selectEffort(model, effort)
    fun onScreenVisible() = refreshUsage()

    fun refreshUsage() {
        if (usageJob?.isActive == true) return
        val key = repository.settings.value.apiKey.trim()
        if (key.isBlank()) {
            usageState.value = UsageUiState.MissingKey
            return
        }
        val previous = previousSuccess()
        usageState.value = UsageUiState.Loading(previous?.first)
        usageJob = viewModelScope.launch {
            try {
                val usage = usageProvider.monthlyUsage(key)
                usageState.value = UsageUiState.Success(usage.amountUsd, clock.millis())
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidOpenRouterApiKeyException) {
                usageState.value = UsageUiState.Error(
                    "API Key 无效，请检查设置",
                    previous?.first,
                    previous?.second,
                )
            } catch (e: Exception) {
                usageState.value = UsageUiState.Error(
                    "用量查询失败，请稍后再试",
                    previous?.first,
                    previous?.second,
                )
            }
        }
    }

    private fun previousSuccess(): Pair<Double, Long>? = when (val state = usageState.value) {
        is UsageUiState.Success -> state.amount to state.updatedAtMillis
        is UsageUiState.Error -> state.previousAmount?.let { it to (state.previousUpdatedAtMillis ?: 0L) }
        else -> null
    }
}

class SettingsViewModelFactory(
    private val repository: SettingsRepository,
    private val usageProvider: OpenRouterUsageProvider,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(repository, usageProvider) as T
}
```

- [ ] **Step 6: Run Task 3 tests and verify GREEN**

Run the Step 3 command. Expected: all usage client and view-model tests pass.

- [ ] **Step 7: Commit Task 3**

```powershell
git -C C:\Users\zhang add -- AndroidStudioProjects/RoyalNote/app/src/main/java/com/example/royalnote/network/OpenRouterUsageService.kt AndroidStudioProjects/RoyalNote/app/src/main/java/com/example/royalnote/ui/SettingsViewModel.kt AndroidStudioProjects/RoyalNote/app/src/test/java/com/example/royalnote/network/OpenRouterUsageServiceTest.kt AndroidStudioProjects/RoyalNote/app/src/test/java/com/example/royalnote/ui/SettingsViewModelTest.kt
git -C C:\Users\zhang diff --cached --check
git -C C:\Users\zhang commit -m "feat: query OpenRouter monthly key usage"
```

---

### Task 4: Settings and analysis Compose screens

**Files:**
- Create: `app/src/main/java/com/example/royalnote/ui/SettingsScreen.kt`
- Create: `app/src/main/java/com/example/royalnote/ui/AnalysisScreen.kt`
- Create: `app/src/main/res/drawable/ic_visibility_24.xml`
- Create: `app/src/main/res/drawable/ic_visibility_off_24.xml`
- Create: `app/src/main/res/drawable/ic_refresh_24.xml`
- Modify: `app/src/androidTest/java/com/example/royalnote/RoyalNoteAppTest.kt`

**Interfaces:**
- Consumes: `SettingsUiState`, `UsageUiState`, `AnalysisModel`, and `ReasoningEffort`.
- Produces: `SettingsScreen`, `AnalysisScreen`, and exact text/semantics for UI tests.

- [ ] **Step 1: Write failing Compose tests for settings states and interactions**

Add tests that render `SettingsScreen` directly with mutable state:

```kotlin
@Test
fun settingsScreenFiltersEffortsAndReportsSelections() {
    var state by mutableStateOf(SettingsUiState())
    composeRule.setContent {
        RoyalNoteTheme {
            SettingsScreen(
                uiState = state,
                onApiKeyChange = { state = state.copy(settings = state.settings.copy(apiKey = it)) },
                onToggleKeyVisibility = {},
                keyVisible = false,
                onModelSelected = { state = state.copy(settings = state.settings.copy(selectedModel = it)) },
                onEffortSelected = { model, effort ->
                    state = state.copy(settings = state.settings.copy(efforts = state.settings.efforts + (model to effort)))
                },
                onRefreshUsage = {},
                onVisible = {},
            )
        }
    }

    composeRule.onNodeWithText("DeepSeek V4 Pro").assertIsDisplayed()
    composeRule.onNodeWithText("xhigh").assertIsDisplayed()
    composeRule.onNodeWithText("max").assertDoesNotExist()
    composeRule.onNodeWithText("GPT Latest").performClick()
    composeRule.onNodeWithText("max").assertIsDisplayed()
    composeRule.onNodeWithText("none").assertIsDisplayed()
}

@Test
fun usageCardShowsMissingLoadingSuccessAndErrorStates() {
    lateinit var setUsage: (UsageUiState) -> Unit
    composeRule.setContent {
        var usage by remember { mutableStateOf<UsageUiState>(UsageUiState.MissingKey) }
        setUsage = { usage = it }
        RoyalNoteTheme {
            SettingsScreen(
                uiState = SettingsUiState(usage = usage),
                keyVisible = false,
                onApiKeyChange = {},
                onToggleKeyVisibility = {},
                onModelSelected = {},
                onEffortSelected = { _, _ -> },
                onRefreshUsage = {},
                onVisible = {},
            )
        }
    }

    composeRule.onNodeWithText("填写 API Key 后可查询").assertIsDisplayed()
    composeRule.runOnUiThread { setUsage(UsageUiState.Loading()) }
    composeRule.onNodeWithTag("usageLoading").assertIsDisplayed()
    composeRule.runOnUiThread { setUsage(UsageUiState.Success(12.34, 1L)) }
    composeRule.onNodeWithText("\$12.34").assertIsDisplayed()
    composeRule.runOnUiThread {
        setUsage(UsageUiState.Error("用量查询失败，请稍后再试", 8.0, 1L))
    }
    composeRule.onNodeWithText("\$8.00").assertIsDisplayed()
    composeRule.onNodeWithText("用量查询失败，请稍后再试").assertIsDisplayed()
}

@Test
fun apiKeyIsPasswordByDefaultAndVisibilityActionIsAccessible() {
    composeRule.setContent {
        var visible by remember { mutableStateOf(false) }
        RoyalNoteTheme {
            SettingsScreen(
                uiState = SettingsUiState(
                    settings = AppSettings(apiKey = "sk-or-v1-secret"),
                ),
                keyVisible = visible,
                onApiKeyChange = {},
                onToggleKeyVisibility = { visible = !visible },
                onModelSelected = {},
                onEffortSelected = { _, _ -> },
                onRefreshUsage = {},
                onVisible = {},
            )
        }
    }

    composeRule.onNodeWithContentDescription("显示 API Key").assertIsDisplayed().performClick()
    composeRule.onNodeWithContentDescription("隐藏 API Key").assertIsDisplayed()
}
```

- [ ] **Step 2: Run the new instrumentation methods and verify RED**

With a connected emulator:

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.royalnote.RoyalNoteAppTest
```

Expected: compilation fails because `SettingsScreen` and `AnalysisScreen` do not exist. If no device is connected, run `assembleDebugAndroidTest` and require compilation failure for the same missing symbols.

- [ ] **Step 3: Add the three vector resources**

Use 24×24 vector drawables with `android:tint="?attr/colorControlNormal"`:

```xml
<!-- ic_visibility_24.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="@android:color/transparent" android:strokeColor="#FF000000" android:strokeWidth="1.8" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M2.8,12C2.8,12 6.2,7 12,7C17.8,7 21.2,12 21.2,12C21.2,12 17.8,17 12,17C6.2,17 2.8,12 2.8,12M12,9.7A2.3,2.3 0,1 0,12,14.3A2.3,2.3 0,1 0,12,9.7" />
</vector>
```

```xml
<!-- ic_visibility_off_24.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="@android:color/transparent" android:strokeColor="#FF000000" android:strokeWidth="1.8" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M3,3L21,21M10.6,7.2C11.1,7.1 11.5,7 12,7C17.8,7 21.2,12 21.2,12C20.4,13.2 19.5,14.2 18.4,15M13.7,14.3C13.2,14.7 12.6,15 12,15C10.3,15 9,13.7 9,12C9,11.4 9.2,10.8 9.7,10.3M6.2,6.7C4.1,8.2 2.8,12 2.8,12C2.8,12 6.2,17 12,17C13.2,17 14.3,16.8 15.3,16.4" />
</vector>
```

```xml
<!-- ic_refresh_24.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="@android:color/transparent" android:strokeColor="#FF000000" android:strokeWidth="1.8" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M20,7L20,12L15,12M19,12C19,8.1 15.9,5 12,5C8.1,5 5,8.1 5,12C5,15.9 8.1,19 12,19C14.2,19 16.2,18 17.5,16.5" />
</vector>
```

- [ ] **Step 4: Implement the blank analysis screen**

Create `AnalysisScreen.kt` exactly as follows:

```kotlin
package com.example.royalnote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("分析") }) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding))
    }
}
```

- [ ] **Step 5: Implement the settings screen from the accepted HTML**

Use this public signature:

```kotlin
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    keyVisible: Boolean,
    onApiKeyChange: (String) -> Unit,
    onToggleKeyVisibility: () -> Unit,
    onModelSelected: (AnalysisModel) -> Unit,
    onEffortSelected: (AnalysisModel, ReasoningEffort) -> Unit,
    onRefreshUsage: () -> Unit,
    onVisible: () -> Unit,
)
```

Implementation requirements:

```kotlin
LaunchedEffect(Unit) { onVisible() }

Scaffold(topBar = { TopAppBar(title = { Text("设置") }) }) { padding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 18.dp),
    ) {
        item {
            ApiKeyCard(
                apiKey = uiState.settings.apiKey,
                keyVisible = keyVisible,
                onApiKeyChange = onApiKeyChange,
                onToggleKeyVisibility = onToggleKeyVisibility,
            )
        }
        item { UsageCard(uiState.usage, onRefreshUsage) }
        item {
            ModelCard(
                settings = uiState.settings,
                onModelSelected = onModelSelected,
                onEffortSelected = onEffortSelected,
            )
        }
        item {
            Text(
                "导入旧录将使用此处配置",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

Each card uses `RoundedCornerShape(8.dp)`, a 1.dp outline border, and the existing `drawWithContent` 3.dp primary left bar at alpha 0.7. The API field uses `PasswordVisualTransformation()` unless `keyVisible`, and an `IconButton` with exact content descriptions `显示 API Key`/`隐藏 API Key`. Model rows use `RadioButton`; effort values use `FilterChip` with the raw wire values. Usage states render:

- `MissingKey`: amount `—`, helper `填写 API Key 后可查询`.
- `Ready`: amount `—`, helper `点击刷新查询新 Key 的用量`.
- `Loading`: retain prior amount when present and add `CircularProgressIndicator(Modifier.testTag("usageLoading"))`.
- `Success`: `NumberFormat.getCurrencyInstance(Locale.US).format(amount)` and helper `当前 API Key · 按 UTC 月统计`.
- `Error`: retain prior amount when present and show its exact error message in `MoodBrick` or `MaterialTheme.colorScheme.error` without introducing a bright red.

The refresh `IconButton` always has content description `刷新本月消费`; disable it only while loading.

- [ ] **Step 6: Compile/run the Compose tests and verify GREEN**

Run Step 2. Expected: all existing and new Compose UI tests pass, or `assembleDebugAndroidTest` succeeds when no device exists.

- [ ] **Step 7: Commit Task 4 while preserving existing UI-test changes**

```powershell
git -C C:\Users\zhang add -- AndroidStudioProjects/RoyalNote/app/src/main/java/com/example/royalnote/ui/SettingsScreen.kt AndroidStudioProjects/RoyalNote/app/src/main/java/com/example/royalnote/ui/AnalysisScreen.kt AndroidStudioProjects/RoyalNote/app/src/main/res/drawable/ic_visibility_24.xml AndroidStudioProjects/RoyalNote/app/src/main/res/drawable/ic_visibility_off_24.xml AndroidStudioProjects/RoyalNote/app/src/main/res/drawable/ic_refresh_24.xml
git -C C:\Users\zhang add -p -- AndroidStudioProjects/RoyalNote/app/src/androidTest/java/com/example/royalnote/RoyalNoteAppTest.kt
git -C C:\Users\zhang diff --cached --check
git -C C:\Users\zhang commit -m "feat: add Ink Fragrance settings screens"
```

---

### Task 5: Bottom navigation, activity wiring, and end-to-end regression

**Files:**
- Create: `app/src/main/java/com/example/royalnote/ui/AppNavigation.kt`
- Create: `app/src/main/res/drawable/ic_home_24.xml`
- Create: `app/src/main/res/drawable/ic_analysis_24.xml`
- Create: `app/src/main/res/drawable/ic_settings_24.xml`
- Modify: `app/src/main/java/com/example/royalnote/MainActivity.kt`
- Modify: `app/src/androidTest/java/com/example/royalnote/RoyalNoteAppTest.kt`

**Interfaces:**
- Consumes: `RoyalNoteApp`, `ImportScreen`, `AnalysisScreen`, `SettingsScreen`, all three view models, and the settings/usage services.
- Produces: `RoyalNoteNavigation`, routes `home/analysis/settings/import`, bottom navigation semantics, and complete app wiring.

- [ ] **Step 1: Write a failing bottom-navigation Compose test**

Add a test that renders `RoyalNoteNavigation` with lightweight route content:

```kotlin
@Test
fun bottomNavigationSwitchesTopLevelScreensAndHidesOnImport() {
    composeRule.setContent {
        RoyalNoteTheme {
            RoyalNoteNavigation(
                homeContent = { onImport ->
                    Column {
                        Text("首页内容")
                        Button(onClick = onImport) { Text("测试导入") }
                    }
                },
                analysisContent = { Text("分析内容") },
                settingsContent = { Text("设置内容") },
                importContent = { onBack -> Button(onClick = onBack) { Text("返回主页") } },
            )
        }
    }

    composeRule.onNodeWithText("主页").assertIsDisplayed()
    composeRule.onNodeWithText("分析").performClick()
    composeRule.onNodeWithText("分析内容").assertIsDisplayed()
    composeRule.onNodeWithText("设置").performClick()
    composeRule.onNodeWithText("设置内容").assertIsDisplayed()
    composeRule.onNodeWithText("主页").performClick()
    composeRule.onNodeWithText("测试导入").performClick()
    composeRule.onNodeWithText("返回主页").assertIsDisplayed()
    composeRule.onNodeWithText("主页").assertDoesNotExist()
    composeRule.onNodeWithText("分析").assertDoesNotExist()
    composeRule.onNodeWithText("设置").assertDoesNotExist()
    composeRule.onNodeWithText("返回主页").performClick()
    composeRule.onNodeWithText("首页内容").assertIsDisplayed()
}
```

- [ ] **Step 2: Run/compile the UI test and verify RED**

Run the Task 4 instrumentation command. Expected: compilation fails because `RoyalNoteNavigation` does not exist.

- [ ] **Step 3: Add bottom-navigation vector resources**

Create these 24×24 outline vectors, using the same vector attributes as Task 4:

```xml
<!-- ic_home_24.xml path -->
<path android:fillColor="@android:color/transparent" android:strokeColor="#FF000000" android:strokeWidth="1.8" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M3.5,10.5L12,3.5L20.5,10.5M5.5,9.5L5.5,19.5L18.5,19.5L18.5,9.5M9.5,19.5L9.5,13.5L14.5,13.5L14.5,19.5" />
```

```xml
<!-- ic_analysis_24.xml path -->
<path android:fillColor="@android:color/transparent" android:strokeColor="#FF000000" android:strokeWidth="1.8" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M5,19L5,11M12,19L12,5M19,19L19,8M3,19.5L21,19.5" />
```

```xml
<!-- ic_settings_24.xml path -->
<path android:fillColor="@android:color/transparent" android:strokeColor="#FF000000" android:strokeWidth="1.5" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="M12,9A3,3 0,1 0,12,15A3,3 0,1 0,12,9M19.3,13.5C19.5,12.5 19.5,11.5 19.3,10.5L21.3,9L19.3,5.6L16.8,6.6C16,5.9 15.1,5.4 14.2,5.1L13.8,2L10,2L9.6,5.1C8.7,5.4 7.8,5.9 7,6.6L4.5,5.6L2.5,9L4.5,10.5C4.3,11.5 4.3,12.5 4.5,13.5L2.5,15L4.5,18.4L7,17.4C7.8,18.1 8.7,18.6 9.6,18.9L10,22L13.8,22L14.2,18.9C15.1,18.6 16,18.1 16.8,17.4L19.3,18.4L21.3,15L19.3,13.5" />
```

- [ ] **Step 4: Implement the Navigation Compose 2 shell**

Create `AppNavigation.kt` with this API and behavior:

```kotlin
internal object AppRoutes {
    const val HOME = "home"
    const val ANALYSIS = "analysis"
    const val SETTINGS = "settings"
    const val IMPORT = "import"
}

@Composable
fun RoyalNoteNavigation(
    homeContent: @Composable (onImport: () -> Unit) -> Unit,
    analysisContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit,
    importContent: @Composable (onBack: () -> Unit) -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val topLevelRoutes = setOf(AppRoutes.HOME, AppRoutes.ANALYSIS, AppRoutes.SETTINGS)
    Scaffold(
        bottomBar = {
            if (route in topLevelRoutes) {
                RoyalNoteBottomBar(route.orEmpty()) { target ->
                    navController.navigate(target) {
                        popUpTo(AppRoutes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppRoutes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(AppRoutes.HOME) {
                homeContent { navController.navigate(AppRoutes.IMPORT) }
            }
            composable(AppRoutes.ANALYSIS) { analysisContent() }
            composable(AppRoutes.SETTINGS) { settingsContent() }
            composable(AppRoutes.IMPORT) {
                importContent { navController.popBackStack() }
            }
        }
    }
}
```

`RoyalNoteBottomBar` uses three `NavigationBarItem`s with exact labels `主页`、`分析`、`设置`, painter resources for the new vectors, `selected = currentRoute == route`, and `NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))`.

- [ ] **Step 5: Replace MainActivity's inline NavHost with complete dependency wiring**

In the activity composition, add:

```kotlin
val usageService = remember { OpenRouterUsageService() }
val settingsViewModel: SettingsViewModel = viewModel(
    factory = SettingsViewModelFactory(settingsRepository, usageService),
)
val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
var keyVisible by rememberSaveable { mutableStateOf(false) }
```

Replace the inline `NavHost` with:

```kotlin
RoyalNoteNavigation(
    homeContent = { onImport ->
        RoyalNoteApp(
            uiState = uiState,
            onEventTextChange = timelineViewModel::updateEventText,
            onMoodSelected = timelineViewModel::selectMood,
            onMoodNoteChange = timelineViewModel::updateMoodNote,
            onStartedAtChange = timelineViewModel::updateStartedAt,
            onEndedAtChange = timelineViewModel::updateEndedAt,
            onEditEventTextChange = timelineViewModel::updateEditEventText,
            onEditMoodSelected = timelineViewModel::selectEditMood,
            onEditMoodNoteChange = timelineViewModel::updateEditMoodNote,
            onEditStartedAtChange = timelineViewModel::updateEditStartedAt,
            onEditEndedAtChange = timelineViewModel::updateEditEndedAt,
            onSave = timelineViewModel::save,
            onEdit = timelineViewModel::startEditing,
            onCancelEdit = timelineViewModel::cancelEditing,
            onDelete = timelineViewModel::delete,
            onMessageShown = timelineViewModel::clearMessage,
            onImportClick = onImport,
        )
    },
    analysisContent = { AnalysisScreen() },
    settingsContent = {
        SettingsScreen(
            uiState = settingsUiState,
            keyVisible = keyVisible,
            onApiKeyChange = settingsViewModel::updateApiKey,
            onToggleKeyVisibility = { keyVisible = !keyVisible },
            onModelSelected = settingsViewModel::selectModel,
            onEffortSelected = settingsViewModel::selectEffort,
            onRefreshUsage = settingsViewModel::refreshUsage,
            onVisible = settingsViewModel::onScreenVisible,
        )
    },
    importContent = { onBack ->
        ImportScreen(
            uiState = importUiState,
            onTextChange = importViewModel::updateText,
            onImportClick = importViewModel::importRecords,
            onBack = {
                importViewModel.resetState()
                onBack()
            },
            onMessageShown = importViewModel::clearMessage,
            onSuccessConfirmed = {
                importViewModel.dismissSuccessDialog()
                importViewModel.resetState()
                onBack()
            },
        )
    },
)
```

Remove obsolete Navigation imports from `MainActivity`, add `rememberSaveable`, screen/navigation/settings imports, and preserve every existing timeline callback exactly.

- [ ] **Step 6: Fix the navigation test and verify GREEN**

Run Task 4's instrumentation command. Expected: all Compose tests pass; the bottom bar is absent on `import`, and returning restores home content.

- [ ] **Step 7: Run the complete JVM suite**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` with no failing tests.

- [ ] **Step 8: Run lint and build**

```powershell
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

Expected: both commands exit 0. The known `android.disallowKotlinSourceSets=false` experimental-option warning may remain; no new lint errors are allowed.

- [ ] **Step 9: Run device UI tests and visual QA when an emulator is available**

```powershell
& "C:\Users\zhang\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices
.\gradlew.bat connectedDebugAndroidTest
```

Install and cold-start if needed:

```powershell
.\gradlew.bat installDebug
& "C:\Users\zhang\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 shell am start -W -S -n com.example.royalnote/.MainActivity
```

Capture screenshots of `主页`, blank `分析`, and `设置`. Compare `设置` against `bottom-navigation-settings-preview.html` for copy, card order, colors, type hierarchy, left accents, icons, selected model/effort, usage states, and bottom-bar spacing. Verify API Key remains masked after relaunch and do not include a real key in screenshots.

- [ ] **Step 10: Commit only Task 5 feature hunks**

```powershell
git -C C:\Users\zhang add -- AndroidStudioProjects/RoyalNote/app/src/main/java/com/example/royalnote/ui/AppNavigation.kt AndroidStudioProjects/RoyalNote/app/src/main/res/drawable/ic_home_24.xml AndroidStudioProjects/RoyalNote/app/src/main/res/drawable/ic_analysis_24.xml AndroidStudioProjects/RoyalNote/app/src/main/res/drawable/ic_settings_24.xml
git -C C:\Users\zhang add -p -- AndroidStudioProjects/RoyalNote/app/src/main/java/com/example/royalnote/MainActivity.kt AndroidStudioProjects/RoyalNote/app/src/androidTest/java/com/example/royalnote/RoyalNoteAppTest.kt
git -C C:\Users\zhang diff --cached --check
git -C C:\Users\zhang commit -m "feat: add bottom navigation and settings module"
```

## Final Verification Checklist

- [ ] Re-read `docs/superpowers/specs/2026-07-12-bottom-navigation-settings-design.md` and map every requirement to Tasks 1–5.
- [ ] Confirm no `BuildConfig.OPENROUTER_API_KEY`, `OpenRouterConfig.API_KEY`, or fixed `OpenRouterConfig.MODEL` reference remains with `rg -n "OPENROUTER_API_KEY|OpenRouterConfig\.(API_KEY|MODEL)" app`.
- [ ] Confirm `usage_monthly` is the only amount displayed and no UI says remaining balance.
- [ ] Confirm API Key values never appear in logs, test screenshots, exceptions, or Snackbar messages.
- [ ] Confirm `git -C C:\Users\zhang status --short -- AndroidStudioProjects/RoyalNote` still contains all unrelated user changes that existed before execution and no unrelated file was staged or committed.
- [ ] Record fresh exit-0 evidence for JVM tests, lint, assemble, and connected UI tests when a device is available before claiming completion.
