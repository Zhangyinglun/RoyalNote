# 底部导航与统一分析设置设计

## 概述

为“起居注”增加三个顶层模块：`主页`、`分析`、`设置`。主页保持现有录入、时间线和导入入口；分析页本期只提供空白页面；设置页统一管理 OpenRouter API Key、分析模型和推理强度。导入旧录不再拥有或读取独立模型配置，而是在每次请求开始时读取设置页保存的最新配置。

## 范围

### 本期包含

- 增加底部导航栏：`主页`、`分析`、`设置`。
- 保持主页已有内容、文案和行为不变。
- 增加空白分析页。
- 增加设置页，支持自动保存 OpenRouter API Key。
- 在设置页查询当前 API Key 的本月消费金额。
- 支持三种分析模型，并按模型展示可用 effort。
- 分别记忆每种模型上次选中的 effort。
- 让导入旧录统一使用设置页中的 API Key、模型和 effort。
- 删除现有 BuildConfig API Key 与固定模型配置来源。

### 本期不包含

- 分析图表或统计功能。
- 从 OpenRouter 动态拉取模型或 effort 元数据。
- 独立的 API Key 联网校验、OpenRouter 账户余额查询或模型可用性测试。
- OpenRouter 账户余额、充值总额或 Management Key 配置。
- 搜索、心情筛选、云同步、导出或多模块业务扩展。
- Navigation 3 迁移。

## 用户决策

- 采用现有 Navigation Compose 2，不迁移 Navigation 3。
- API Key 输入时自动保存，不设置单独的保存按钮。
- GPT 与 Gemini 使用 OpenRouter 的动态 Latest 别名；DeepSeek 使用固定 V4 Pro 模型。
- effort 默认值为 `high`。
- 只展示当前模型支持的 effort。
- 每个模型分别记忆上次选择的 effort。
- 使用固定的本地模型/effort 映射，不在运行时请求 OpenRouter 模型目录。
- 用当前普通 API Key 查询 `usage_monthly`；不要求或保存 Management Key。

## 模型目录

| 显示名称 | OpenRouter 模型 ID | 可选 effort | 初始值 |
|---|---|---|---|
| DeepSeek V4 Pro | `deepseek/deepseek-v4-pro` | `xhigh`, `high` | `high` |
| GPT Latest | `~openai/gpt-latest` | `max`, `xhigh`, `high`, `medium`, `low`, `none` | `high` |
| Gemini Pro Latest | `~google/gemini-pro-latest` | `high`, `medium`, `low` | `high` |

上述映射以 2026-07-12 OpenRouter 模型元数据为准。Latest 别名对应的实际模型会由 OpenRouter 更新，但本期客户端 effort 列表保持固定；若未来别名能力发生变化，再通过应用更新调整映射。

## 导航架构

沿用当前单 Activity、Compose 和 Navigation Compose 2 架构。

根导航包含四个路由：

- `home`：现有 `RoyalNoteApp` 主页。
- `analysis`：空白分析页。
- `settings`：统一配置页。
- `import`：现有导入旧录页，是从主页进入的子流程。

根层使用 Material 3 `Scaffold` 承载底部导航。`home`、`analysis`、`settings` 显示底栏；`import` 隐藏底栏。底栏切换使用单例顶层路由，避免重复压入相同页面。Activity 级的 `RecordTimelineViewModel` 和 `ImportViewModel` 保持现有生命周期，因此切换顶层模块不会清空主页表单或时间线状态。

从主页进入导入页后，系统返回和页面返回按钮都回到主页。主页顶部的应用标题、`导入`入口及当前业务内容保持不变。

## 设置数据模型

新增清晰分层的设置类型：

- `AnalysisModel`：定义显示名、OpenRouter 模型 ID、支持的 effort 和默认 effort。
- `AppSettings`：包含 API Key、当前模型以及每个模型各自保存的 effort。
- `SettingsRepository`：负责从本地加载配置、向 Compose 暴露 `StateFlow<AppSettings>`、验证值并自动持久化修改。
- `SettingsViewModel`：组合设置状态与用量查询状态，进入设置页时触发查询，并处理手动刷新、加载和错误反馈。
- `OpenRouterUsageService`：使用当前 API Key 请求 `/api/v1/key`，返回该 Key 的本月消费等只读用量数据。
- `OpenRouterSettingsProvider`：向网络层提供一次请求所需的不可变配置快照，避免网络层依赖 Compose 或设置页面状态。

默认配置为：

- API Key：空字符串。
- 当前模型：DeepSeek V4 Pro。
- DeepSeek V4 Pro effort：`high`。
- GPT Latest effort：`high`。
- Gemini Pro Latest effort：`high`。

加载本地数据时执行验证：未知模型回退到 DeepSeek V4 Pro；某个模型保存了不支持的 effort 时，仅将该模型回退到 `high`。这些回退值会成为后续读取和请求使用的有效配置。

## 持久化与敏感信息

设置使用应用私有 `SharedPreferences`。API Key、当前模型和三个模型的 effort 分开存储。API Key 每次输入变化即调用异步 `apply()` 保存；模型和 effort 每次选择后也立即保存。

API Key 在界面上默认以密码形式隐藏，并提供显示/隐藏切换。网络请求前只对读取到的 Key 去除首尾空白，不修改输入框或持久化的原始文本。

保存设置的 SharedPreferences 文件必须在 Android 数据提取规则中排除云端备份和设备迁移，避免 API Key 随系统备份转移。该方案保护一般应用私有数据和备份边界，但不承诺抵抗 root、调试器或已入侵设备上的本地读取。

现有 Gradle `local.properties` 中 `openrouter.api.key` 到 `BuildConfig.OPENROUTER_API_KEY` 的注入逻辑会删除。`OpenRouterConfig` 只保留端点和不含用户凭据的通用请求常量，不再提供固定 API Key 或固定模型。

## 设置页界面

设置页遵循“墨香”设计系统：系统 Serif 字体、陈纸背景、8dp 卡片圆角、低饱和青铜主色和左侧 3dp 装饰线。

页面包含：

1. 顶部栏标题 `设置`。
2. API Key 卡片：
   - 标题 `OpenRouter API Key`。
   - 单行密码输入框。
   - 显示/隐藏切换，并提供明确的无障碍描述。
   - 辅助说明表明输入会自动保存在本机。
3. 本月消费卡片：
   - 标题 `本月消费`。
   - 使用当前 API Key 请求 `GET https://openrouter.ai/api/v1/key`。
   - 成功时以美元格式显示响应的 `usage_monthly`，例如 `$12.34`。
   - 辅助说明 `当前 API Key · 按 UTC 月统计`。
   - 提供具有无障碍描述的刷新按钮。
   - 加载时保留卡片尺寸并显示进度；刷新失败时保留本次会话中最近一次成功数值。
   - API Key 为空时不请求网络，显示 `填写 API Key 后可查询`。
4. 分析模型卡片：
   - 标题 `分析模型`。
   - 三个单选模型项。
   - 当前模型下方用选择芯片展示该模型支持的 effort。
   - 模型或 effort 选择后立即更新并保存。
5. 弱提示文案：`导入旧录将使用此处配置`。

切换模型时 effort 芯片列表立即变化，并选中该模型上次保存的值。所有颜色复用现有主题，不引入明亮或高饱和色。

## 本月消费查询流程

本模块显示的是当前 API Key 在 OpenRouter 上的 `usage_monthly`，不是账户余额，也不是 Key 的剩余额度。OpenRouter credits 的基础货币为美元，因此界面使用 `$` 和两位小数展示。

查询流程：

1. 用户进入设置页时，`SettingsViewModel` 读取当前保存的 API Key。
2. Key 为空时进入未配置状态，不发起请求。
3. Key 非空时自动调用一次 `OpenRouterUsageService.getCurrentKeyUsage(apiKey)`。
4. Service 向 `/api/v1/key` 发起带 Bearer Token 的 GET 请求并解析 `data.usage_monthly`。
5. 成功后更新金额和本次会话的最近更新时间。
6. 用户可点击刷新按钮重复查询。

API Key 输入仍然逐字自动保存，但用量查询不得随每个字符变化而触发。用户修改 Key 后，旧 Key 的用量立即从界面清除，并显示可刷新状态；用户点击刷新或下次重新进入设置页时才使用新 Key 查询。这样可避免输入过程中产生大量无效认证请求。

最近一次成功结果只保存在 `SettingsViewModel` 的内存状态中，不写入 SharedPreferences。刷新失败时可继续展示本次会话内的旧金额，同时显示错误提示；应用重启后进入设置页会重新查询。

## 底部导航和页面界面

底部栏使用 Material 3 `NavigationBar` 与 `NavigationBarItem`，三个项目均包含简洁图标、稳定的中文文字节点和无障碍语义：

- `主页`
- `分析`
- `设置`

选中项使用主题 primary 青铜色，未选中项使用 `onSurfaceVariant`。底栏背景使用现有 surface 色，不引入新配色。

分析页只显示标题 `分析` 和空白内容区，不显示“敬请期待”等占位文案，确保本期不暗示尚未设计的功能。

## 导入配置数据流

导入调用链保持：`ImportScreen` → `ImportViewModel` → `RecordParser` → `OpenRouterService`。

变化发生在 `OpenRouterService`：

1. `parseRecords` 开始时向 `OpenRouterSettingsProvider` 获取配置快照。
2. 验证去除首尾空格后的 API Key 非空。
3. 使用快照中的模型 ID 构建 `ChatCompletionRequest.model`。
4. 使用该模型保存的 effort 构建 `ReasoningConfig`，并继续设置 `exclude = true`。
5. 使用快照中的 API Key 构建 Authorization 请求头。
6. 后续响应解析和记录写入流程保持现状。

这样可以保证用户在设置页的最新修改从下一次导入请求开始生效，也避免进行中的请求因设置变化而使用一半新、一半旧的参数。

## 错误处理

- API Key 为空：不发起 HTTP 请求，向导入页面显示 `请先在设置中填写 OpenRouter API Key`。
- 用量查询时 API Key 为空：不发起 HTTP 请求，卡片显示 `填写 API Key 后可查询`。
- `/api/v1/key` 返回 401：用量卡片提示 `API Key 无效，请检查设置`。
- 用量查询网络失败或其他服务错误：提示 `用量查询失败，请稍后再试`；若有本次会话的旧结果则继续显示。
- 导入请求网络失败：沿用 `网络不通，稍后再试`。
- OpenRouter 非成功响应或解析异常：沿用 `解析未成，稍后再试`。
- 数据库保存失败：沿用 `记录已解析，但保存失败，请稍后再试`。
- 本地模型或 effort 数据无效：静默回退到有效默认值，不导致设置页或导入流程崩溃。

缺少 API Key 使用独立异常类型或等价的可识别错误，使 `ImportViewModel` 能与普通网络错误区分。任何错误都不得把 API Key 写入日志或用户可见提示。

## 测试策略

### JVM 单元测试

- `SettingsRepository` 初次加载得到 DeepSeek V4 Pro 和三个 `high` 默认值。
- API Key 输入后自动持久化，新建 repository 后能重新加载。
- 当前模型选择持久化。
- DeepSeek、GPT、Gemini 各自的 effort 分别持久化，切换模型不会覆盖其他模型的值。
- 未知模型与不受支持 effort 会回退到规定默认值。
- 每个 `AnalysisModel` 只暴露设计表中的 effort。
- `OpenRouterService` 使用设置快照中的 API Key、模型和 effort 构造请求。
- 设置变化后，下一次请求读取新配置。
- API Key 为空时不会执行 HTTP 请求，并返回可识别的缺少配置错误。
- `ImportViewModel` 将缺少 Key 映射为 `请先在设置中填写 OpenRouter API Key`。
- `OpenRouterUsageService` 使用当前 Key 请求 `/api/v1/key` 并正确解析 `usage_monthly`。
- 设置页首次进入时只在 Key 非空的情况下自动查询一次。
- 空 Key 不查询；401、网络失败和成功响应分别进入正确状态。
- 修改 API Key 后清除旧 Key 的用量，且不会随每个输入字符自动请求。
- 手动刷新使用最新保存的 API Key；刷新失败时保留本次会话最近一次成功金额。

### Compose UI 测试

- 底栏显示 `主页`、`分析`、`设置`，并可切换三个顶层模块。
- 回到主页后现有 `起居注`、`导入`和业务文案仍可访问。
- 分析页仅显示标题，没有新增业务控件。
- 设置页显示 API Key 输入框、三个模型和当前模型支持的 effort。
- 本月消费卡片正确展示未配置、加载、成功和失败状态，刷新按钮可用。
- 切换模型后 effort 选项和选中状态正确变化。
- API Key 默认隐藏，显示/隐藏操作具有正确语义。
- 保持现有 UI 测试依赖的中文节点，包括心情标签和 `未输入`。

### 验证命令

从项目目录执行：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

检测到连接的设备或模拟器后再执行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## 完成标准

- 应用启动后默认进入主页并显示三个底部导航项。
- 主页现有录入、编辑、删除、时间线与导入行为保持可用。
- 分析页为空白模块。
- 设置页的 API Key、模型和每模型 effort 输入或选择后立即持久化。
- 设置页能用当前普通 API Key 查询并显示本 UTC 月消费金额，支持自动查询与手动刷新。
- 重启应用后配置保持。
- 导入请求只使用设置页的统一配置，不再读取 BuildConfig Key 或固定模型。
- 未配置 API Key 时有明确中文提示且不会发起网络请求。
- 新增和现有自动化测试通过，lint 与 debug 构建成功。

## HTML 交互审阅稿

项目根目录的 `bottom-navigation-settings-preview.html` 是本设计的独立交互审阅页。它模拟 390 × 844 手机界面，支持三个底部模块切换、API Key 显示/隐藏与自动保存反馈、本月消费自动查询和手动刷新、模型切换、effort 过滤及每模型选择记忆。页面中的 `$12.34` 是固定演示数据；该页面只用于设计审阅，不连接 OpenRouter，也不保存真实凭据。
