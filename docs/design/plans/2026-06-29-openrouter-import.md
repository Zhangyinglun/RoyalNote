# OpenRouter 导入功能实现计划


**Goal:** 在起居注 app 中新增"导入旧录"功能，用户粘贴大段文字后调用 DeepSeek V4 Pro 解析为多条记录并写入数据库。

**Architecture:** OkHttp 直调 OpenRouter chat completions API → kotlinx.serialization 解析 JSON → NavHost 导航到导入页面 → ImportViewModel 管理状态 → NoteRepository 批量插入。

**Tech Stack:** OkHttp 5.0.0-alpha.16, kotlinx-serialization-json 1.9.0, navigation-compose 2.10.0-alpha05, DeepSeek V4 Pro (xhigh reasoning, json_object mode)

## Global Constraints

- AGP 9.2.1, Gradle 9.4.1, Kotlin 2.2.10, KSP 2.2.10-2.0.2, Compose BOM 2026.02.01
- compileSdk 36.1, minSdk 33, targetSdk 36
- Java 11 source/target compat
- Room 2.8.4 (KSP, not kapt), exportSchema=false, version=1
- `settings.gradle.kts` sets `RepositoriesMode.FAIL_ON_PROJECT_REPOS`
- Windows 环境，用 `.\gradlew.bat` 构建
- 用户面向 app 名: 起居注
- 心情标签固定: 开心, 满足, 平静, 疲惫, 烦躁, 低落, 焦虑
- 墨香设计系统: Serif 字体, 青铜色 primary, 8dp 圆角, AgedPaper 背景
- API key 写在 local.properties 中，通过 BuildConfig 注入
- OpenRouter model slug: `deepseek/deepseek-v4-pro`
- 现有测试中的中文字符串不能改: "未记今日之事，不宜入录", "速录一则", "修订此则", "入录", "改毕入录", "作罢", "今日", "昨日", "修订", "抹去", "起居注"

---

## File Structure

```
app/src/main/java/com/example/royalnote/
├── MainActivity.kt                    (修改: NavHost + TopAppBar + 导入按钮)
├── data/
│   ├── NoteRecordDao.kt               (修改: 加 insertAll)
│   ├── NoteRepository.kt              (修改: 加 importRecords)
│   └── ...                            (其余不变)
├── network/                           (新建目录)
│   ├── OpenRouterConfig.kt            (新建: API key, URL, model 常量)
│   ├── OpenRouterModels.kt            (新建: 序列化 DTO)
│   ├── RecordParser.kt                (新建: 解析接口)
│   └── OpenRouterService.kt           (新建: OkHttp 实现)
├── ui/
│   ├── ImportScreen.kt                (新建: 导入页面 Compose UI)
│   ├── ImportViewModel.kt             (新建: 状态管理 + 工厂)
│   ├── RecordTimelineViewModel.kt     (修改: RecordOperations 加 importRecords)
│   └── ...
app/src/main/AndroidManifest.xml       (修改: 加 INTERNET 权限)
app/src/test/java/com/example/royalnote/ui/
├── ImportViewModelTest.kt             (新建: 导入逻辑测试)
└── RecordTimelineViewModelTest.kt     (修改: FakeRecordRepository 加 importRecords)
gradle/libs.versions.toml              (修改: 加依赖)
app/build.gradle.kts                   (修改: 加插件 + 依赖)
```

---

### Task 1: 分支 + 依赖 + Manifest

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: 无
- Produces: 项目可编译新依赖 (OkHttp, serialization, navigation)

- [ ] **Step 1: 创建 feature 分支**

```bash
cd C:\Users\zhang\AndroidStudioProjects\RoyalNote
git checkout -b feature/openrouter-import
```

- [ ] **Step 2: 更新 gradle/libs.versions.toml**

在 `[versions]` 块末尾加三行:

```toml
okhttp = "5.0.0-alpha.16"
kotlinxSerialization = "1.9.0"
navigationCompose = "2.10.0-alpha05"
```

在 `[libraries]` 块中 `kotlinx-coroutines-test` 行之后加三行:

```toml
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
```

在 `[plugins]` 块中 `ksp` 行之后加一行:

```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 3: 更新 app/build.gradle.kts**

在 `plugins` 块加 `alias(libs.plugins.kotlin.serialization)`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}
```

在 `dependencies` 块中 `implementation(libs.androidx.lifecycle.viewmodel.compose)` 行之后加三行:

```kotlin
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.navigation.compose)
```

- [ ] **Step 4: 加 INTERNET 权限到 AndroidManifest.xml**

在 `<manifest>` 标签之后、`<application>` 标签之前加:

```xml
    <uses-permission android:name="android.permission.INTERNET" />
```

- [ ] **Step 5: 验证构建**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "build: 加 OkHttp/serialization/navigation 依赖 + INTERNET 权限"
```

---

### Task 2: 网络层 DTO + 配置 + 解析接口

**Files:**
- Create: `app/src/main/java/com/example/royalnote/network/OpenRouterModels.kt`
- Create: `app/src/main/java/com/example/royalnote/network/OpenRouterConfig.kt`
- Create: `app/src/main/java/com/example/royalnote/network/RecordParser.kt`

**Interfaces:**
- Consumes: kotlinx.serialization.annotations
- Produces: `ParsedRecords`, `ParsedRecord`, `RecordParser` 接口, `OpenRouterConfig` 常量

- [ ] **Step 1: 创建 network 目录和 OpenRouterModels.kt**

```kotlin
package com.example.royalnote.network

import kotlinx.serialization.Serializable

@Serializable
data class ParsedRecords(
    val records: List<ParsedRecord> = emptyList(),
)

@Serializable
data class ParsedRecord(
    val eventText: String,
    val moodTag: String? = null,
    val moodNote: String? = null,
    val timestamp: String,
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val response_format: ResponseFormat? = null,
    val reasoning: ReasoningConfig? = null,
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ResponseFormat(
    val type: String,
)

@Serializable
data class ReasoningConfig(
    val effort: String,
    val exclude: Boolean = true,
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<Choice> = emptyList(),
)

@Serializable
data class Choice(
    val message: ChatMessage,
)
```

- [ ] **Step 2: 创建 OpenRouterConfig.kt**

```kotlin
package com.example.royalnote.network

object OpenRouterConfig {
    val API_KEY: String get() = BuildConfig.OPENROUTER_API_KEY
    const val BASE_URL = "https://openrouter.ai/api/v1/chat/completions"
    const val MODEL = "deepseek/deepseek-v4-pro"
}
```

- [ ] **Step 3: 创建 RecordParser.kt**

```kotlin
package com.example.royalnote.network

interface RecordParser {
    suspend fun parseRecords(text: String): ParsedRecords
}
```

- [ ] **Step 4: 验证构建**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/royalnote/network/
git commit -m "feat(network): OpenRouter DTO, 配置常量, RecordParser 接口"
```

---

### Task 3: DAO 批量插入 + RecordOperations 接口 + Repository

**Files:**
- Modify: `app/src/main/java/com/example/royalnote/data/NoteRecordDao.kt`
- Modify: `app/src/main/java/com/example/royalnote/ui/RecordTimelineViewModel.kt:20-33` (RecordOperations 接口)
- Modify: `app/src/main/java/com/example/royalnote/data/NoteRepository.kt`
- Modify: `app/src/test/java/com/example/royalnote/ui/RecordTimelineViewModelTest.kt:224-246` (FakeRecordRepository)

**Interfaces:**
- Consumes: 无
- Produces: `RecordOperations.importRecords(List<NoteRecord>)`, `NoteRecordDao.insertAll(List<NoteRecord>)`

- [ ] **Step 1: NoteRecordDao 加 insertAll 方法**

在 `NoteRecordDao.kt` 的 `insert` 方法之后加:

```kotlin
    @Insert
    suspend fun insertAll(records: List<NoteRecord>)
```

- [ ] **Step 2: RecordOperations 接口加 importRecords 方法**

在 `RecordTimelineViewModel.kt` 的 `RecordOperations` 接口中，`deleteRecord` 之后加:

```kotlin
    suspend fun importRecords(records: List<NoteRecord>)
```

- [ ] **Step 3: NoteRepository 实现 importRecords**

在 `NoteRepository.kt` 的 `deleteRecord` 方法之后加:

```kotlin
    override suspend fun importRecords(records: List<NoteRecord>) {
        dao.insertAll(records)
    }
```

- [ ] **Step 4: FakeRecordRepository 加 importRecords 实现**

在 `RecordTimelineViewModelTest.kt` 的 `FakeRecordRepository` 类中，`deleteRecord` 方法之后加:

```kotlin
    override suspend fun importRecords(records: List<NoteRecord>) {
        val withIds = records.map { it.copy(id = nextId++) }
        records.value = records.value + withIds
    }
```

- [ ] **Step 5: 运行现有测试验证不破坏**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: 所有 9 个 RecordTimelineViewModelTest 测试 PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/royalnote/data/NoteRecordDao.kt app/src/main/java/com/example/royalnote/ui/RecordTimelineViewModel.kt app/src/main/java/com/example/royalnote/data/NoteRepository.kt app/src/test/java/com/example/royalnote/ui/RecordTimelineViewModelTest.kt
git commit -m "feat(data): 批量插入 importRecords + DAO insertAll"
```

---

### Task 4: OpenRouterService 实现

**Files:**
- Create: `app/src/main/java/com/example/royalnote/network/OpenRouterService.kt`

**Interfaces:**
- Consumes: `RecordParser` 接口, `OpenRouterConfig` 常量, `OpenRouterModels` DTO
- Produces: `OpenRouterService` 类 (实现 `RecordParser`)

- [ ] **Step 1: 创建 OpenRouterService.kt**

```kotlin
package com.example.royalnote.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class OpenRouterService : RecordParser {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun parseRecords(text: String): ParsedRecords = withContext(Dispatchers.IO) {
        val systemPrompt = """你是一个起居注解析助手。用户会粘贴之前的生活记录文字，你需要将其解析为结构化的 JSON 数据。

规则：
1. 将文本拆分为多条独立的事件记录，每个事件一条记录
2. eventText: 简洁描述事件内容
3. moodTag: 根据事件内容推断心情，从以下选项中选择：开心、满足、平静、疲惫、烦躁、低落、焦虑。无法推断时设为 null
4. moodNote: 如果文本中有情绪描述，提取为 moodNote；没有则设为 null
5. timestamp: ISO 8601 格式（YYYY-MM-DDTHH:mm:ss）
   - 从文本中提取时间信息
   - 某个时间点下有多条记录时，合理推断它们共享该时间
   - 只有日期没有具体时间的，用当日 00:00
   - 完全没有时间信息的，用当前时间

只返回 JSON，不要返回其他内容：
{"records": [{"eventText": "...", "moodTag": "...", "moodNote": null, "timestamp": "YYYY-MM-DDTHH:mm:ss"}]}"""

        val requestBody = ChatCompletionRequest(
            model = OpenRouterConfig.MODEL,
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = text),
            ),
            response_format = ResponseFormat(type = "json_object"),
            reasoning = ReasoningConfig(effort = "xhigh", exclude = true),
        )

        val bodyString = json.encodeToString(ChatCompletionRequest.serializer(), requestBody)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(OpenRouterConfig.BASE_URL)
            .addHeader("Authorization", "Bearer ${OpenRouterConfig.API_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(bodyString)
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw IOException("API error: ${response.code}")
        }

        val responseBody = response.body?.string()
            ?: throw IOException("Empty response body")

        val apiResponse = json.decodeFromString(ChatCompletionResponse.serializer(), responseBody)
        val content = apiResponse.choices.firstOrNull()?.message?.content
            ?: throw IOException("No content in response")

        json.decodeFromString(ParsedRecords.serializer(), content)
    }
}
```

- [ ] **Step 2: 验证构建**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/royalnote/network/OpenRouterService.kt
git commit -m "feat(network): OpenRouterService 实现 DeepSeek V4 Pro 调用"
```

---

### Task 5: ImportViewModel + 测试 (TDD)

**Files:**
- Create: `app/src/test/java/com/example/royalnote/ui/ImportViewModelTest.kt`
- Create: `app/src/main/java/com/example/royalnote/ui/ImportViewModel.kt`

**Interfaces:**
- Consumes: `RecordParser` 接口, `RecordOperations` 接口, `ParsedRecords`/`ParsedRecord` DTO, `NoteRecord` entity
- Produces: `ImportViewModel`, `ImportUiState`, `ImportViewModelFactory`

- [ ] **Step 1: 写 ImportViewModelTest（先失败）**

```kotlin
package com.example.royalnote.ui

import com.example.royalnote.data.NoteRecord
import com.example.royalnote.network.ParsedRecord
import com.example.royalnote.network.ParsedRecords
import com.example.royalnote.network.RecordParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var parser: FakeRecordParser
    private lateinit var repository: FakeImportRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        parser = FakeRecordParser()
        repository = FakeImportRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun importEmptyTextShowsMessage() = runTest {
        val viewModel = ImportViewModel(parser, repository)

        viewModel.updateText("   ")
        viewModel.importRecords()
        advanceUntilIdle()

        assertEquals("先粘贴旧日记录", viewModel.uiState.value.message)
        assertFalse(parser.wasCalled)
    }

    @Test
    fun importSuccessInsertsRecordsAndShowsCount() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("开会", "疲惫", "太长了", "2026-06-20T10:30:00"),
            ParsedRecord("写文档", "满足", null, "2026-06-20T15:00:00"),
        ))
        val viewModel = ImportViewModel(parser, repository)

        viewModel.updateText("今天开了个会，然后写了文档")
        viewModel.importRecords()
        advanceUntilIdle()

        assertEquals(2, repository.importedRecords.size)
        assertEquals("已入录 2 则", viewModel.uiState.value.message)
        assertTrue(viewModel.uiState.value.isSuccess)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun importNetworkErrorShowsMessage() = runTest {
        parser.exception = IOException("network error")
        val viewModel = ImportViewModel(parser, repository)

        viewModel.updateText("一些文字")
        viewModel.importRecords()
        advanceUntilIdle()

        assertEquals("网络不通，稍后再试", viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun importParseErrorShowsMessage() = runTest {
        parser.exception = RuntimeException("parse error")
        val viewModel = ImportViewModel(parser, repository)

        viewModel.updateText("一些文字")
        viewModel.importRecords()
        advanceUntilIdle()

        assertEquals("解析未成，稍后再试", viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun importFiltersInvalidMoodTag() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("事件A", "乱七八糟", null, "2026-06-20T10:00:00"),
        ))
        val viewModel = ImportViewModel(parser, repository)

        viewModel.updateText("事件A")
        viewModel.importRecords()
        advanceUntilIdle()

        val record = repository.importedRecords.single()
        assertNull(record.moodTag)
    }

    @Test
    fun importTimestampParseFailureFallsBackToNow() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("事件", null, null, "not-a-date"),
        ))
        val viewModel = ImportViewModel(parser, repository)

        viewModel.updateText("事件")
        viewModel.importRecords()
        advanceUntilIdle()

        val record = repository.importedRecords.single()
        assertTrue(record.createdAt > 0)
    }

    @Test
    fun updateTextClearsMessage() = runTest {
        val viewModel = ImportViewModel(parser, repository)

        viewModel.updateText("   ")
        viewModel.importRecords()
        advanceUntilIdle()
        assertEquals("先粘贴旧日记录", viewModel.uiState.value.message)

        viewModel.updateText("一些文字")
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun resetStateClearsEverything() = runTest {
        parser.result = ParsedRecords(listOf(
            ParsedRecord("事件", null, null, "2026-06-20T10:00:00"),
        ))
        val viewModel = ImportViewModel(parser, repository)

        viewModel.updateText("旧文字")
        viewModel.importRecords()
        advanceUntilIdle()

        viewModel.resetState()

        assertEquals("", viewModel.uiState.value.text)
        assertNull(viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isSuccess)
        assertFalse(viewModel.uiState.value.isLoading)
    }
}

private class FakeRecordParser : RecordParser {
    var result: ParsedRecords = ParsedRecords(emptyList())
    var exception: Throwable? = null
    var wasCalled = false

    override suspend fun parseRecords(text: String): ParsedRecords {
        wasCalled = true
        exception?.let { throw it }
        return result
    }
}

private class FakeImportRepository : RecordOperations {
    val importedRecords = mutableListOf<NoteRecord>()
    private val records = MutableStateFlow<List<NoteRecord>>(emptyList())

    override fun observeRecords(): Flow<List<NoteRecord>> = records

    override suspend fun addRecord(
        eventText: String,
        moodTag: String?,
        moodNote: String?,
        nowMillis: Long,
    ) {
        records.value = records.value + NoteRecord(0, eventText, moodTag, moodNote, nowMillis, nowMillis)
    }

    override suspend fun updateRecord(record: NoteRecord) {
        records.value = records.value.map { if (it.id == record.id) record else it }
    }

    override suspend fun deleteRecord(record: NoteRecord) {
        records.value = records.value.filterNot { it.id == record.id }
    }

    override suspend fun importRecords(records: List<NoteRecord>) {
        importedRecords.addAll(records)
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.example.royalnote.ui.ImportViewModelTest"`
Expected: FAIL — `ImportViewModel` 类不存在

- [ ] **Step 3: 写 ImportViewModel.kt**

```kotlin
package com.example.royalnote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.royalnote.data.MoodLabels
import com.example.royalnote.data.NoteRecord
import com.example.royalnote.network.ParsedRecord
import com.example.royalnote.network.RecordParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

data class ImportUiState(
    val text: String = "",
    val isLoading: Boolean = false,
    val message: String? = null,
    val isSuccess: Boolean = false,
)

class ImportViewModel(
    private val parser: RecordParser,
    private val repository: RecordOperations,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun updateText(value: String) {
        _uiState.value = _uiState.value.copy(text = value, message = null)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun resetState() {
        _uiState.value = ImportUiState()
    }

    fun importRecords() {
        val currentText = _uiState.value.text
        if (currentText.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "先粘贴旧日记录")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, message = null)

        viewModelScope.launch {
            try {
                val parsed = parser.parseRecords(currentText)
                val records = parsed.records.map { it.toNoteRecord() }
                repository.importRecords(records)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSuccess = true,
                    message = "已入录 ${records.size} 则",
                )
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "网络不通，稍后再试",
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "解析未成，稍后再试",
                )
            }
        }
    }

    private fun ParsedRecord.toNoteRecord(): NoteRecord {
        val millis = parseTimestamp(timestamp)
        val validMoods = MoodLabels.ALL.toSet()
        return NoteRecord(
            eventText = eventText,
            moodTag = moodTag?.takeIf { it in validMoods },
            moodNote = moodNote?.takeIf { it.isNotBlank() },
            createdAt = millis,
            updatedAt = millis,
        )
    }

    private fun parseTimestamp(timestamp: String): Long {
        return try {
            LocalDateTime.parse(timestamp)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (e: DateTimeParseException) {
            try {
                LocalDate.parse(timestamp)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (e: DateTimeParseException) {
                Instant.now().toEpochMilli()
            }
        }
    }
}

class ImportViewModelFactory(
    private val parser: RecordParser,
    private val repository: RecordOperations,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ImportViewModel(parser, repository) as T
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.example.royalnote.ui.ImportViewModelTest"`
Expected: 所有 8 个测试 PASS

- [ ] **Step 5: 运行全部测试确认无破坏**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: 所有测试 PASS (RecordTimelineViewModelTest 9 个 + ImportViewModelTest 8 个)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/royalnote/ui/ImportViewModel.kt app/src/test/java/com/example/royalnote/ui/ImportViewModelTest.kt
git commit -m "feat(ui): ImportViewModel + 8 个单元测试"
```

---

### Task 6: ImportScreen UI

**Files:**
- Create: `app/src/main/java/com/example/royalnote/ui/ImportScreen.kt`

**Interfaces:**
- Consumes: `ImportUiState` 状态类
- Produces: `ImportScreen` composable

- [ ] **Step 1: 创建 ImportScreen.kt**

```kotlin
package com.example.royalnote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    uiState: ImportUiState,
    onTextChange: (String) -> Unit,
    onImportClick: () -> Unit,
    onBack: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入旧录", fontFamily = FontFamily.Serif) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.text,
                onValueChange = onTextChange,
                label = { Text("粘贴旧日记录") },
                minLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onImportClick,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("导入", fontFamily = FontFamily.Serif)
                }
            }
        }
    }
}
```

- [ ] **Step 2: 验证构建**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/royalnote/ui/ImportScreen.kt
git commit -m "feat(ui): ImportScreen 导入页面 Compose UI"
```

---

### Task 7: Navigation + TopAppBar + MainActivity 整合

**Files:**
- Modify: `app/src/main/java/com/example/royalnote/MainActivity.kt`

**Interfaces:**
- Consumes: `ImportScreen`, `ImportViewModel`, `ImportViewModelFactory`, `OpenRouterService`, NavHost
- Produces: 完整可运行的 app (主页 + 导入页导航)

- [ ] **Step 1: 修改 MainActivity — 加 import + NavHost + TopAppBar**

在 `MainActivity.kt` 中做以下修改:

**1a. 加新 import（在现有 import 块末尾、`java.time` 之前加）:**

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.font.FontFamily
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.royalnote.network.OpenRouterService
import com.example.royalnote.ui.ImportScreen
import com.example.royalnote.ui.ImportViewModel
import com.example.royalnote.ui.ImportViewModelFactory
```

**1b. 替换 `onCreate` 方法体（从 `setContent` 到方法结束）:**

将 `setContent { RoyalNoteTheme { ... } }` 整块替换为:

```kotlin
        setContent {
            RoyalNoteTheme {
                val context = LocalContext.current
                val database = remember { RoyalNoteDatabase.getInstance(context) }
                val repository = remember { NoteRepository(database.noteRecordDao()) }
                val parser = remember { OpenRouterService() }

                val timelineViewModel: RecordTimelineViewModel = viewModel(
                    factory = RecordTimelineViewModelFactory(repository),
                )
                val importViewModel: ImportViewModel = viewModel(
                    factory = ImportViewModelFactory(parser, repository),
                )

                LaunchedEffect(Unit) {
                    SeedData.seedIfEmpty(repository)
                }

                val navController = rememberNavController()
                val uiState by timelineViewModel.uiState.collectAsStateWithLifecycle()
                val importUiState by importViewModel.uiState.collectAsStateWithLifecycle()

                NavHost(navController, startDestination = "main") {
                    composable("main") {
                        RoyalNoteApp(
                            uiState = uiState,
                            onEventTextChange = timelineViewModel::updateEventText,
                            onMoodSelected = timelineViewModel::selectMood,
                            onMoodNoteChange = timelineViewModel::updateMoodNote,
                            onEditEventTextChange = timelineViewModel::updateEditEventText,
                            onEditMoodSelected = timelineViewModel::selectEditMood,
                            onEditMoodNoteChange = timelineViewModel::updateEditMoodNote,
                            onSave = timelineViewModel::save,
                            onEdit = timelineViewModel::startEditing,
                            onCancelEdit = timelineViewModel::cancelEditing,
                            onDelete = timelineViewModel::delete,
                            onMessageShown = timelineViewModel::clearMessage,
                            onImportClick = { navController.navigate("import") },
                        )
                    }
                    composable("import") {
                        ImportScreen(
                            uiState = importUiState,
                            onTextChange = importViewModel::updateText,
                            onImportClick = importViewModel::importRecords,
                            onBack = {
                                importViewModel.resetState()
                                navController.popBackStack()
                            },
                            onMessageShown = importViewModel::clearMessage,
                        )
                    }
                }
            }
        }
```

**1c. 给 RoyalNoteApp 加 onImportClick 参数 + TopAppBar:**

在 `RoyalNoteApp` 函数签名末尾加 `onImportClick: () -> Unit,` 参数:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoyalNoteApp(
    uiState: RecordTimelineUiState,
    onEventTextChange: (String) -> Unit,
    onMoodSelected: (String?) -> Unit,
    onMoodNoteChange: (String) -> Unit,
    onEditEventTextChange: (String) -> Unit,
    onEditMoodSelected: (String?) -> Unit,
    onEditMoodNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onEdit: (NoteRecord) -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: (NoteRecord) -> Unit,
    onMessageShown: () -> Unit,
    onImportClick: () -> Unit,
) {
```

在 `Scaffold` 调用中加 `topBar`:

```kotlin
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("起居注", fontFamily = FontFamily.Serif) },
                actions = {
                    IconButton(onClick = onImportClick) {
                        Icon(Icons.Default.Add, contentDescription = "导入")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
```

注意: 加了 TopAppBar 后，LazyColumn 的第一个 item 中的 "起居注" 标题 Text 已移到 TopAppBar，但副标题和墨香下划线保留。将第一个 `item { ... }` 块替换为:

```kotlin
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    "录今日之事，存此刻之心。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    )
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                CircleShape,
                            )
                    )
                }
            }
```

**1d. 更新 RoyalNotePreview 函数:**

在 `RoyalNotePreview` 的 `RoyalNoteApp(...)` 调用末尾加:

```kotlin
            onImportClick = {},
```

- [ ] **Step 2: 验证构建**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 运行全部测试**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: 所有测试 PASS

- [ ] **Step 4: 运行 lint**

Run: `.\gradlew.bat lintDebug`
Expected: BUILD SUCCESSFUL，无 fatal 错误

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/royalnote/MainActivity.kt
git commit -m "feat(ui): NavHost 导航 + TopAppBar 导入按钮 + MainActivity 整合"
```

---

### Task 8: 最终验证

**Files:**
- 无新文件修改

- [ ] **Step 1: 运行全部 JVM 测试**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: 所有测试 PASS (RecordTimelineViewModelTest 9 个 + ImportViewModelTest 8 个 + ExampleUnitTest 1 个)

- [ ] **Step 2: 构建 debug APK**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL，APK 生成在 `app/build/outputs/apk/debug/`

- [ ] **Step 3: 运行 lint**

Run: `.\gradlew.bat lintDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 检查 git 状态**

Run: `git status --short`
Expected: working tree clean

- [ ] **Step 5: 查看 commit 历史**

Run: `git log --oneline -10`
Expected: 看到 7 个 feature commit + 1 个 docs commit
