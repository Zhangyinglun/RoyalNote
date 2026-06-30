# OpenRouter 导入功能设计

## 概述

在起居注 app 中新增"导入旧录"功能。用户将之前的大段文字记录粘贴到导入页面，点击导入按钮后，app 调用 OpenRouter 的 DeepSeek V4 Pro 模型解析文本，将解析结果批量写入本地数据库。

## 用户决策记录

- **解析粒度**: 拆成多条记录，每个事件一条 NoteRecord
- **时间戳**: AI 从文本中提取，支持合理推断（时间头继承、缺时间用 00:00）
- **moodTag**: 由模型根据事件内容推断，不限于文本中明确写出的情绪词
- **API key**: 写死在项目代码中（用户已知晓反编译风险）
- **导航**: 引入 NavHost 导航库
- **HTTP 方案**: OkHttp 直接调 REST API（OpenRouter 无官方 Kotlin SDK）

## 架构

```
MainActivity
  └─ NavHost (routes: "main", "import")
       ├─ MainScreen (现有 RoyalNoteApp + TopAppBar 带导入按钮)
       └─ ImportScreen (文本输入框 + 导入按钮)
            └─ ImportViewModel
                 ├─ OpenRouterService (OkHttp 调 OpenRouter API)
                 │    └─ DeepSeek V4 Pro (xhigh reasoning, JSON mode)
                 └─ NoteRepository (批量插入解析后的记录)
```

### 新增文件

| 文件 | 职责 |
|------|------|
| `network/RecordParser.kt` | `RecordParser` 接口（可测试的接缝） |
| `network/OpenRouterService.kt` | `RecordParser` 实现，OkHttp 调用 OpenRouter chat completions 端点 |
| `network/OpenRouterModels.kt` | 请求/响应 DTO（kotlinx.serialization） |
| `network/OpenRouterConfig.kt` | API key、模型 slug、端点 URL 常量 |
| `ui/ImportViewModel.kt` | 导入页面状态管理（文本、加载中、错误、成功） |
| `ui/ImportScreen.kt` | 导入页面 Compose UI |

### 修改文件

| 文件 | 变更 |
|------|------|
| `MainActivity.kt` | 加入 NavHost，提取 MainScreen composable，TopAppBar 带导入按钮 |
| `AndroidManifest.xml` | 加 `<uses-permission android:name="android.permission.INTERNET" />` |
| `gradle/libs.versions.toml` | 加 okhttp、kotlinx-serialization-json、navigation-compose 依赖和 serialization 插件 |
| `app/build.gradle.kts` | 加新依赖 + kotlin-serialization 插件 |
| `data/NoteRepository.kt` | 加 `importRecords(records: List<NoteRecord>)` 批量插入方法 |
| `ui/RecordTimelineViewModel.kt` | `RecordOperations` 接口加 `importRecords` 方法 |

## 依赖变更

### gradle/libs.versions.toml

```toml
[versions]
# 新增
okhttp = "5.0.0-alpha.16"
kotlinxSerialization = "1.9.0"
navigationCompose = "2.10.0-alpha05"

[libraries]
# 新增
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }

[plugins]
# 新增
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

### app/build.gradle.kts

- `plugins` 块加 `alias(libs.plugins.kotlin.serialization)`
- `dependencies` 块加 `implementation(libs.okhttp)`、`implementation(libs.kotlinx.serialization.json)`、`implementation(libs.navigation.compose)`

## 数据模型

### AI 返回的 JSON 格式

```json
{
  "records": [
    {
      "eventText": "上午开会讨论了新版本设计",
      "moodTag": "疲惫",
      "moodNote": "讨论时间太长了",
      "timestamp": "2026-06-20T10:30:00"
    },
    {
      "eventText": "下午写完了技术文档",
      "moodTag": "满足",
      "moodNote": null,
      "timestamp": "2026-06-20T15:00:00"
    }
  ]
}
```

字段说明：
- `eventText` (string, 必填): 事件描述
- `moodTag` (string | null): 从 `开心`、`满足`、`平静`、`疲惫`、`烦躁`、`低落`、`焦虑` 中选一个，或 null。由模型根据事件内容推断。
- `moodNote` (string | null): 心情备注，可 null
- `timestamp` (string): ISO 8601 格式（`YYYY-MM-DDTHH:mm:ss`），AI 从文本中提取

### 时间推断规则（写入系统提示词）

1. 某个时间点下有多条记录时，合理推断它们共享该时间
2. 只有日期没有具体时间的，用当日 00:00
3. 完全没有时间信息的，用当前时间

### Kotlin DTO（OpenRouterModels.kt）

```kotlin
@Serializable
data class ParsedRecords(
    val records: List<ParsedRecord>
)

@Serializable
data class ParsedRecord(
    val eventText: String,
    val moodTag: String? = null,
    val moodNote: String? = null,
    val timestamp: String
)

// OpenRouter API 请求体
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

// OpenRouter API 响应体
@Serializable
data class ChatCompletionResponse(
    val choices: List<Choice>,
)

@Serializable
data class Choice(
    val message: ChatMessage,
)
```

## 网络层

### OpenRouterConfig.kt

```kotlin
object OpenRouterConfig {
    val API_KEY: String get() = BuildConfig.OPENROUTER_API_KEY
    const val BASE_URL = "https://openrouter.ai/api/v1/chat/completions"
    const val MODEL = "deepseek/deepseek-v4-pro"
}
```

### RecordParser 接口（可测试的接缝）

```kotlin
interface RecordParser {
    suspend fun parseRecords(text: String): ParsedRecords
}
```

`ImportViewModel` 依赖 `RecordParser` 接口，不依赖具体实现。测试时注入 `FakeRecordParser`。

### OpenRouterService.kt

- 实现 `RecordParser` 接口
- 用 OkHttp `OkHttpClient` 发 POST 请求
- 请求头: `Authorization: Bearer <API_KEY>`、`Content-Type: application/json`
- 请求体: `ChatCompletionRequest` 序列化为 JSON
- 系统提示词: 指示 AI 将文本解析为多条记录，推断心情，提取/推断时间
- `response_format`: `{ "type": "json_object" }` 强制返回合法 JSON
- `reasoning`: `{ "effort": "xhigh", "exclude": true }` — max 推理，不返回推理过程
- 响应: 解析 `choices[0].message.content` 为 `ParsedRecords`
- 协程: `suspend fun parseRecords(text: String): ParsedRecords`，用 `withContext(Dispatchers.IO)`
- 网络错误抛 `IOException`，JSON 解析错误抛 `SerializationException`，API 错误抛 `IllegalStateException`

### 系统提示词（要点）

```
你是一个起居注解析助手。用户会粘贴之前的生活记录文字，你需要将其解析为结构化的 JSON 数据。

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

返回 JSON 格式：
{"records": [{"eventText": "...", "moodTag": "...", "moodNote": "...", "timestamp": "..."}]}
```

## 导入流程

1. 用户在导入页粘贴文本，点击"导入"按钮
2. `ImportViewModel` 验证文本非空
3. 调用 `OpenRouterService.parseRecords(text)` — 非流式，等待完整响应
4. 解析返回的 JSON 为 `ParsedRecords` DTO
5. 每条 `ParsedRecord` 转换为 `NoteRecord`:
   - `eventText` = parsed.eventText
   - `moodTag` = parsed.moodTag（验证在 7 种心情内，不在则 null）
   - `moodNote` = parsed.moodNote
   - `createdAt` = `updatedAt` = timestamp 转 epoch millis（解析失败用当前时间）
6. 调用 `repository.importRecords(list)` 批量插入
7. 成功 → 导航回主页，Snackbar 提示"已入录 N 则"
8. 失败 → Snackbar 显示错误信息，留在导入页

### ImportViewModel 状态

```kotlin
data class ImportUiState(
    val text: String = "",
    val isLoading: Boolean = false,
    val message: String? = null,
)
```

方法:
- `updateText(value: String)` — 更新文本，清 message
- `importRecords()` — 触发导入流程
- `clearMessage()` — 清 message

### RecordOperations 接口变更

```kotlin
interface RecordOperations {
    // 现有方法不变
    fun observeRecords(): Flow<List<NoteRecord>>
    suspend fun addRecord(eventText: String, moodTag: String?, moodNote: String?, nowMillis: Long)
    suspend fun updateRecord(record: NoteRecord)
    suspend fun deleteRecord(record: NoteRecord)
    // 新增
    suspend fun importRecords(records: List<NoteRecord>)
}
```

`NoteRepository.importRecords` 实现: 在 Room 事务中循环调用 `dao.insert(record)`。

## UI 变更

### 主页 TopAppBar

给现有 `Scaffold` 加 `topBar`:
- `TopAppBar`（Material3）
- 标题: "起居注"，Serif 字体
- 右侧 `Actions`: 一个 `IconButton`，图标用 `Icons.Default.FileUpload`（或 `Icons.AutoMirrored.Filled.Import`）
- 点击导航到 "import" 路由
- 墨香配色: 标题用 primary 色

### 导入页

- `TopAppBar`: 返回箭头（`NavigationIcon`），标题"导入旧录"
- `OutlinedTextField`: label"粘贴旧日记录"，`minLines = 8`，全宽
- `Button`: "导入"，导入中显示 `CircularProgressIndicator` + 禁用按钮
- `SnackbarHost`: 显示成功/错误消息
- 墨香设计系统: Serif 字体、青铜色按钮、AgedPaper 背景、8dp 圆角

### NavHost 设置

```kotlin
NavHost(navController, startDestination = "main") {
    composable("main") { MainScreen(...) }
    composable("import") { ImportScreen(...) }
}
```

## 错误处理

| 场景 | 提示文案 |
|------|----------|
| 文本为空 | "先粘贴旧日记录" |
| 网络请求失败 | "网络不通，稍后再试" |
| API 返回错误 | "解析未成，稍后再试" |
| JSON 解析失败 | "解析结果有误，稍后再试" |
| 成功 | "已入录 N 则" |

所有错误通过 `ImportUiState.message` 传递，在 `ImportScreen` 的 `SnackbarHost` 中显示。

## 测试策略

### JVM 单元测试: ImportViewModelTest

- `importSuccess` — fake service 返回 2 条记录，验证 repository.importRecords 被调用，状态变为成功
- `importEmptyText` — 空文本，验证不调用 service，显示"先粘贴旧日记录"
- `importNetworkError` — fake service 抛 IOException，验证显示"网络不通，稍后再试"
- `importJsonParseError` — fake service 返回非法 JSON，验证显示"解析结果有误，稍后再试"
- `importMoodTagValidation` — AI 返回不在 7 种心情内的 moodTag，验证被过滤为 null

### Fake 依赖

- `FakeRecordParser`: 实现 `RecordParser` 接口，可注入预设的 `ParsedRecords` 或异常
- `FakeRecordRepository`: 扩展现有 `FakeRecordRepository`，加 `importRecords` 实现

### 现有测试不受影响

- `RecordTimelineViewModelTest`: `FakeRecordRepository` 需要加 `importRecords` 空实现
- `RoyalNoteAppTest`: 不涉及导入功能，无需修改

## Git 分支

新建分支 `feature/openrouter-import`，所有改动在此分支上完成。
