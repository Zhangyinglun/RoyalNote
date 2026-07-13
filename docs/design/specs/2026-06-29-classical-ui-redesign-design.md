# 起居注 UI 美化设计：墨韵书卷

**日期**：2026-06-29
**方案**：A. 墨韵书卷（古典雅致）
**范围**：主题层 + 组件升级（布局结构不变）

## 目标

将"起居注"从默认 Material 3 紫色模板升级为中式古典书卷风格，契合应用名的史册气质。只动 UI 层（theme 包 + MainActivity 组件），不改 ViewModel、Repository、Database、数据模型。

## 约束

- 保持现有布局结构（LazyColumn + Scaffold），不重构为多页面/导航
- 保留所有 UI 测试断言的文本节点："起居注"、"做了什么"、2 个"未输入"、事件正文、心情备注
- 不引入新依赖，只用 Material3 + Compose 基础 API
- minSdk 33，targetSdk 36

## 配色系统

### 浅色模式（宣纸基调）

| 角色 | 色值 | 用途 |
|------|------|------|
| background | `#FAF6EE` | 宣纸米黄，页面底色 |
| surface | `#FFFEF7` | 卡片底色，略亮于背景 |
| surfaceVariant | `#F0EBE0` | 次级表面 |
| primary | `#B91C1C` | 朱砂红，主按钮/强调 |
| onPrimary | `#FFFFFF` | 朱砂红上的文字 |
| secondary | `#5F8F7A` | 青瓷绿，次要操作 |
| tertiary | `#C9A227` | 赭黄，辅助强调 |
| onBackground | `#1C1B1F` | 墨色正文 |
| onSurface | `#1C1B1F` | 卡片上的文字 |
| onSurfaceVariant | `#5C5448` | 次级文字（时间戳/副标题） |
| outline | `#D6CFC0` | 米色边框/分隔线 |

### 深色模式（深墨基调）

| 角色 | 色值 | 用途 |
|------|------|------|
| background | `#1A1814` | 深墨底色 |
| surface | `#252320` | 卡片底色 |
| surfaceVariant | `#2F2B26` | 次级表面 |
| primary | `#E07A5C` | 暖朱（调亮保对比度） |
| onPrimary | `#1A1814` | 暖朱上的文字 |
| secondary | `#8FB9A3` | 青瓷浅 |
| tertiary | `#D4B556` | 暖金 |
| onBackground | `#EDE6D6` | 米色文字 |
| onSurface | `#EDE6D6` | 卡片上的文字 |
| onSurfaceVariant | `#B8AE9A` | 次级文字 |
| outline | `#3D3833` | 深色边框 |

### 心情 chip 七色（传统色映射）

| 心情 | 色值 | 色名 |
|------|------|------|
| 开心 | `#C73E3A` | 朱红 |
| 满足 | `#C9A227` | 赭黄 |
| 平静 | `#5F8F7A` | 青瓷 |
| 疲惫 | `#6B7280` | 黛灰 |
| 烦躁 | `#B7654A` | 赭红 |
| 低落 | `#3D5A6C` | 墨青 |
| 焦虑 | `#8B5A6B` | 紫赭 |

"未输入"（moodTag = null）用 onSurfaceVariant 默认色，不着传统色。

## 字体系统

全部使用 `FontFamily.Serif`（系统映射到 Noto Serif CJK 思源宋体），为中文阅读调大行高：

| 档位 | 字号 | 行高 | 字重 | 用途 |
|------|------|------|------|------|
| headlineMedium | 28sp | 36sp | Bold | 应用标题"起居注" |
| titleLarge | 22sp | 28sp | SemiBold | TopAppBar 标题（如使用） |
| titleMedium | 18sp | 24sp | SemiBold | 区块标题（编辑器/日期头） |
| bodyLarge | 16sp | 26sp | Normal | 事件正文 |
| bodyMedium | 14sp | 22sp | Normal | 心情备注/副标题 |
| labelLarge | 14sp | 20sp | Medium | 心情标签/按钮文字 |
| labelMedium | 12sp | 18sp | Medium | 时间戳 |

## 组件升级

### 标题区（LazyColumn 首项）

- "起居注"用 `headlineMedium` + `FontWeight.Bold` + `primary`（朱砂色）
- 副标题"记下此刻做了什么，和当时的心情。"用 `bodyMedium` + `onSurfaceVariant`
- 标题下方加朱砂细分隔线：`Box(Modifier.width(40.dp).height(2.dp).background(primary))`

### RecordEditor 编辑器卡片

- `Card`：`surface` 底色 + 圆角 6dp + `border(1dp, outline)` + 左侧朱砂竖线
- 左侧竖线实现：`Modifier.drawBehind` 在卡片左侧画 3dp 宽 `primary` 竖线
- 标题"快速记录"/"编辑记录"用 `titleMedium` + SemiBold
- `OutlinedTextField` 保持 Material3 默认，label 由 Typography 全局生效 Serif
- 心情 `FilterChip`：选中时 `chipColors` 用对应传统色作背景 + 白色文字；未选中用默认
- 保存按钮：`Button`（primary 朱砂色）

### RecordCard 记录卡片

- 同样的卡片样式：左侧朱砂竖线 + 细边框 + 圆角 6dp
- 时间戳：`labelMedium` + `onSurfaceVariant`
- 事件正文：`bodyLarge`
- 心情标签：`AssistChip` 自定义 `chipColors`，用对应传统色作容器色 + 白色文字
- "未输入"（moodTag = null）的 AssistChip 用默认色
- 编辑/删除：`TextButton`

### 时间线视觉化

- `TimelineHeader` 改为 `Row`：左侧画圆点（8dp 直径，`primary` 色）+ 标题文字
- 每个 `RecordCard` 左侧留 20dp 缩进（`padding(start = 20.dp)`），对齐时间线
- 用 `Modifier.drawBehind` 在缩进区画 `outline` 色竖线，营造编年史册感

### 空状态

- 居中显示（`Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)`）
- 加朱砂短分隔线装饰（40dp 宽，2dp 高）
- 文字用 `bodyMedium` + `onSurfaceVariant`
- 上下留白加大（`padding(vertical = 24.dp)`）

### 删除对话框

- 保持 `AlertDialog`，标题/正文由 Typography 全局生效 Serif，无需额外改动

## 文件改动清单

| 文件 | 改动内容 |
|------|---------|
| `app/src/main/java/com/example/royalnote/ui/theme/Color.kt` | 替换 Purple/Pink 为中式传统色；新增 7 个心情色常量 |
| `app/src/main/java/com/example/royalnote/ui/theme/Theme.kt` | `dynamicColor` 默认改 `false`；补全 Light/DarkColorScheme 各角色 |
| `app/src/main/java/com/example/royalnote/ui/theme/Type.kt` | 补全 7 个 Typography 档位，全部用 `FontFamily.Serif`，调大中文行高 |
| `app/src/main/java/com/example/royalnote/MainActivity.kt` | 标题区加朱砂色+分隔线；编辑器/记录卡片加左侧竖线+边框；心情 chip 用传统色；时间线加圆点+竖线；空状态加装饰 |

**不改动**：`RecordTimelineViewModel.kt`、`NoteRepository.kt`、`RoyalNoteDatabase.kt`、`NoteRecordDao.kt`、`NoteRecord.kt`

## 测试影响

- `RecordTimelineViewModelTest`（单元测试）：不受影响，只测 ViewModel 逻辑
- `RoyalNoteAppTest`（UI 测试）：不受影响，所有断言的文本节点保留：
  - "起居注" - 标题保留
  - "做了什么" - 输入框 label 保留
  - 2 个"未输入" - 编辑器 FilterChip + 记录卡片 AssistChip 保留
  - "读了半小时书" - 事件正文保留
  - "心里安稳了一点" - 心情备注保留

## 实现要点

### 心情色映射函数

在 `Color.kt` 中定义 7 个心情色常量。在 `MainActivity.kt` 中新增辅助函数：

```kotlin
private fun moodColor(mood: String?): Color? {
    if (mood == null) return null
    return when (mood) {
        "开心" -> MoodRed
        "满足" -> MoodOchre
        "平静" -> MoodCeladon
        "疲惫" -> MoodGray
        "烦躁" -> MoodBrick
        "低落" -> MoodInkBlue
        "焦虑" -> MoodPurple
        else -> null
    }
}
```

### 左侧竖线实现

```kotlin
Modifier.drawBehind {
    drawRect(
        color = primary,
        topLeft = Offset.Zero,
        size = Size(width = 3.dp.toPx(), height = size.height),
    )
}
```

### 时间线圆点

```kotlin
Box(
    modifier = Modifier
        .size(8.dp)
        .clip(CircleShape)
        .background(primary)
)
```
