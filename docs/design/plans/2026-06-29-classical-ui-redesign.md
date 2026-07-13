# 起居注 UI 美化实现计划：墨韵书卷


**Goal:** 将"起居注"从默认 Material 3 紫色模板升级为中式古典书卷风格（墨韵书卷方案）。

**Architecture:** 只动 UI 层：theme 包（Color/Theme/Type）定义品牌色与字体，MainActivity 消费主题升级组件样式。不改 ViewModel/Repository/Database/Data 模型。布局结构不变（LazyColumn + Scaffold）。

**Tech Stack:** Kotlin + Jetpack Compose + Material 3 + Room（不动）。minSdk 33，targetSdk 36。

## Global Constraints

- 不引入新依赖，只用 Material3 + Compose 基础 API
- 保留所有 UI 测试断言的文本节点："起居注"、"做了什么"、2 个"未输入"、"读了半小时书"、"心里安稳了一点"
- 不改 ViewModel/Repository/Database/NoteRecord 数据模型
- Windows 环境用 `gradlew.bat` 而非 `./gradlew`
- 字体用 FontFamily.Serif（系统映射 Noto Serif CJK），不内置字体文件
- 关闭动态颜色（dynamicColor = false），用固定品牌色

---

## File Structure

| 文件 | 职责 | 改动类型 |
|------|------|---------|
| `app/src/main/java/com/example/royalnote/ui/theme/Color.kt` | 中式传统色常量 + 心情七色 | 重写 |
| `app/src/main/java/com/example/royalnote/ui/theme/Theme.kt` | Light/Dark ColorScheme + 关闭动态颜色 | 重写 |
| `app/src/main/java/com/example/royalnote/ui/theme/Type.kt` | Serif Typography 全档位 | 重写 |
| `app/src/main/java/com/example/royalnote/MainActivity.kt` | 标题区/编辑器/记录卡片/时间线/空状态样式升级 | 修改 |

---

### Task 1: Color.kt — 中式传统色 + 心情七色

**Files:**
- Modify: `app/src/main/java/com/example/royalnote/ui/theme/Color.kt`

**Interfaces:**
- Produces: `InkBlack`, `RicePaper`, `RicePaperSurface`, `RicePaperVariant`, `Cinnabar`, `CinnabarOn`, `Celadon`, `Ochre`, `InkOnSurface`, `InkOnSurfaceVariant`, `RicePaperOutline`（浅色）；`DeepInk`, `DeepInkSurface`, `DeepInkVariant`, `WarmCinnabar`, `WarmCinnabarOn`, `CeladonLight`, `WarmGold`, `RiceText`, `RiceTextVariant`, `DeepInkOutline`（深色）；`MoodRed`, `MoodOchre`, `MoodCeladon`, `MoodGray`, `MoodBrick`, `MoodInkBlue`, `MoodPurple`（心情七色）

- [ ] **Step 1: 重写 Color.kt**

替换 `app/src/main/java/com/example/royalnote/ui/theme/Color.kt` 全部内容：

```kotlin
package com.example.royalnote.ui.theme

import androidx.compose.ui.graphics.Color

// 浅色模式（宣纸基调）
val InkBlack = Color(0xFF1C1B1F)
val RicePaper = Color(0xFFFAF6EE)
val RicePaperSurface = Color(0xFFFFFEF7)
val RicePaperVariant = Color(0xFFF0EBE0)
val Cinnabar = Color(0xFFB91C1C)
val CinnabarOn = Color(0xFFFFFFFF)
val Celadon = Color(0xFF5F8F7A)
val Ochre = Color(0xFFC9A227)
val InkOnSurface = Color(0xFF1C1B1F)
val InkOnSurfaceVariant = Color(0xFF5C5448)
val RicePaperOutline = Color(0xFFD6CFC0)

// 深色模式（深墨基调）
val DeepInk = Color(0xFF1A1814)
val DeepInkSurface = Color(0xFF252320)
val DeepInkVariant = Color(0xFF2F2B26)
val WarmCinnabar = Color(0xFFE07A5C)
val WarmCinnabarOn = Color(0xFF1A1814)
val CeladonLight = Color(0xFF8FB9A3)
val WarmGold = Color(0xFFD4B556)
val RiceText = Color(0xFFEDE6D6)
val RiceTextVariant = Color(0xFFB8AE9A)
val DeepInkOutline = Color(0xFF3D3833)

// 心情 chip 七色（传统色映射）
val MoodRed = Color(0xFFC73E3A)       // 开心 - 朱红
val MoodOchre = Color(0xFFC9A227)     // 满足 - 赭黄
val MoodCeladon = Color(0xFF5F8F7A)   // 平静 - 青瓷
val MoodGray = Color(0xFF6B7280)      // 疲惫 - 黛灰
val MoodBrick = Color(0xFFB7654A)     // 烦躁 - 赭红
val MoodInkBlue = Color(0xFF3D5A6C)   // 低落 - 墨青
val MoodPurple = Color(0xFF8B5A6B)    // 焦虑 - 紫赭
```

- [ ] **Step 2: 编译验证**

Run: `gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/royalnote/ui/theme/Color.kt
git commit -m "feat(theme): 替换默认紫色为中式传统色 + 心情七色"
```

---

### Task 2: Theme.kt — 关闭动态颜色 + 补全 ColorScheme

**Files:**
- Modify: `app/src/main/java/com/example/royalnote/ui/theme/Theme.kt`

**Interfaces:**
- Consumes: Task 1 的所有颜色常量
- Produces: `RoyalNoteTheme` composable（签名不变，但 dynamicColor 默认值改为 false）

- [ ] **Step 1: 重写 Theme.kt**

替换 `app/src/main/java/com/example/royalnote/ui/theme/Theme.kt` 全部内容：

```kotlin
package com.example.royalnote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Cinnabar,
    onPrimary = CinnabarOn,
    secondary = Celadon,
    onSecondary = CinnabarOn,
    tertiary = Ochre,
    onTertiary = CinnabarOn,
    background = RicePaper,
    onBackground = InkBlack,
    surface = RicePaperSurface,
    onSurface = InkOnSurface,
    surfaceVariant = RicePaperVariant,
    onSurfaceVariant = InkOnSurfaceVariant,
    outline = RicePaperOutline,
)

private val DarkColorScheme = darkColorScheme(
    primary = WarmCinnabar,
    onPrimary = WarmCinnabarOn,
    secondary = CeladonLight,
    onSecondary = WarmCinnabarOn,
    tertiary = WarmGold,
    onTertiary = WarmCinnabarOn,
    background = DeepInk,
    onBackground = RiceText,
    surface = DeepInkSurface,
    onSurface = RiceText,
    surfaceVariant = DeepInkVariant,
    onSurfaceVariant = RiceTextVariant,
    outline = DeepInkOutline,
)

@Composable
fun RoyalNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

- [ ] **Step 2: 编译验证**

Run: `gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 运行单元测试确保不破坏**

Run: `gradlew.bat test`
Expected: 所有 RecordTimelineViewModelTest 测试通过

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/royalnote/ui/theme/Theme.kt
git commit -m "feat(theme): 关闭动态颜色，启用中式传统色 ColorScheme"
```

---

### Task 3: Type.kt — 补全 Serif Typography

**Files:**
- Modify: `app/src/main/java/com/example/royalnote/ui/theme/Type.kt`

**Interfaces:**
- Produces: `Typography` 对象（headlineMedium, titleLarge, titleMedium, bodyLarge, bodyMedium, labelLarge, labelMedium 全部用 FontFamily.Serif）

- [ ] **Step 1: 重写 Type.kt**

替换 `app/src/main/java/com/example/royalnote/ui/theme/Type.kt` 全部内容：

```kotlin
package com.example.royalnote.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.25.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp,
    ),
)
```

- [ ] **Step 2: 编译验证**

Run: `gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/royalnote/ui/theme/Type.kt
git commit -m "feat(theme): 补全 Serif Typography 全档位，优化中文行高"
```

---

### Task 4: MainActivity.kt — 标题区 + 编辑器卡片 + 心情 chip 色彩

**Files:**
- Modify: `app/src/main/java/com/example/royalnote/MainActivity.kt`

**Interfaces:**
- Consumes: Task 1 的心情色常量，Task 2 的主题，Task 3 的 Typography
- Produces: 升级后的 `RoyalNoteApp`、`RecordEditor`、`moodColor` 辅助函数

- [ ] **Step 1: 添加 moodColor 辅助函数和新增 import**

在 `MainActivity.kt` 文件顶部 import 区添加：

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.royalnote.ui.theme.MoodBrick
import com.example.royalnote.ui.theme.MoodCeladon
import com.example.royalnote.ui.theme.MoodGray
import com.example.royalnote.ui.theme.MoodInkBlue
import com.example.royalnote.ui.theme.MoodOchre
import com.example.royalnote.ui.theme.MoodPurple
import com.example.royalnote.ui.theme.MoodRed
```

在文件底部（`formatTime` 函数之后）添加 `moodColor` 辅助函数：

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

- [ ] **Step 2: 升级标题区（LazyColumn 首项）**

将 `RoyalNoteApp` 中标题区的 `item { }` 块替换为：

```kotlin
item {
    Spacer(Modifier.height(12.dp))
    Text(
        "起居注",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        "记下此刻做了什么，和当时的心情。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(2.dp)
            .background(MaterialTheme.colorScheme.primary)
    )
}
```

- [ ] **Step 3: 升级 RecordEditor 卡片样式**

将 `RecordEditor` 函数的 `Card` 调用替换为：

```kotlin
Card(
    modifier = Modifier
        .fillMaxWidth()
        .border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline,
            shape = RoundedCornerShape(6.dp),
        )
        .drawBehind {
            drawRect(
                color = MaterialTheme.colorScheme.primary,
                topLeft = Offset.Zero,
                size = Size(width = 3.dp.toPx(), height = size.height),
            )
        },
    shape = RoundedCornerShape(6.dp),
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (uiState.isEditing) "编辑记录" else "快速记录",
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedTextField(
            value = uiState.eventText,
            onValueChange = onEventTextChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("做了什么") },
            minLines = 2,
        )
        Text("心情", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiState.selectedMood == null,
                onClick = { onMoodSelected(null) },
                label = { Text("未输入") },
            )
            MoodLabels.ALL.forEach { mood ->
                val color = moodColor(mood)
                FilterChip(
                    selected = uiState.selectedMood == mood,
                    onClick = { onMoodSelected(mood) },
                    label = { Text(mood) },
                    colors = if (color != null) FilterChipDefaults.filterChipColors(
                        selectedContainerColor = color,
                        selectedLabelColor = Color.White,
                    ) else FilterChipDefaults.filterChipColors(),
                )
            }
        }
        OutlinedTextField(
            value = uiState.moodNote,
            onValueChange = onMoodNoteChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("心情补充，可不填") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave) { Text(if (uiState.isEditing) "保存修改" else "保存") }
            if (uiState.isEditing) {
                TextButton(onClick = onCancelEdit) { Text("取消") }
            }
        }
    }
}
```

- [ ] **Step 4: 编译验证**

Run: `gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 运行 UI 测试确保文本节点保留**

Run: `gradlew.bat connectedAndroidTest`（需要连接设备/模拟器）
Expected: `RoyalNoteAppTest.showsEditorAndTimelineRecord` 通过

如果无设备可用，至少运行单元测试：
Run: `gradlew.bat test`
Expected: 所有测试通过

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/royalnote/MainActivity.kt
git commit -m "feat(ui): 标题区朱砂色+分隔线，编辑器卡片左侧竖线，心情 chip 七色"
```

---

### Task 5: MainActivity.kt — 记录卡片 + 时间线视觉化 + 空状态

**Files:**
- Modify: `app/src/main/java/com/example/royalnote/MainActivity.kt`

**Interfaces:**
- Consumes: Task 4 的 `moodColor` 辅助函数
- Produces: 升级后的 `TimelineHeader`、`RecordCard`、`EmptyTimeline`

- [ ] **Step 1: 升级 TimelineHeader 为时间线节点**

将 `TimelineHeader` 函数替换为：

```kotlin
@Composable
private fun TimelineHeader(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                )
        )
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
```

- [ ] **Step 2: 升级 RecordCard 卡片样式 + 心情 AssistChip 色彩**

将 `RecordCard` 函数替换为：

```kotlin
@Composable
private fun RecordCard(
    record: NoteRecord,
    onEdit: (NoteRecord) -> Unit,
    onDelete: (NoteRecord) -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val moodColorValue = moodColor(record.moodTag)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(6.dp),
            )
            .drawBehind {
                drawRect(
                    color = MaterialTheme.colorScheme.primary,
                    topLeft = Offset.Zero,
                    size = Size(width = 3.dp.toPx(), height = size.height),
                )
            },
        shape = RoundedCornerShape(6.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                formatTime(record.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(record.eventText, style = MaterialTheme.typography.bodyLarge)
            AssistChip(
                onClick = {},
                label = { Text(record.moodTag ?: "未输入") },
                colors = if (moodColorValue != null) AssistChipDefaults.assistChipColors(
                    containerColor = moodColorValue,
                    labelColor = Color.White,
                ) else AssistChipDefaults.assistChipColors(),
            )
            if (!record.moodNote.isNullOrBlank()) {
                Text(record.moodNote, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onEdit(record) }) { Text("编辑") }
                TextButton(onClick = { showDeleteDialog = true }) { Text("删除") }
            }
        }
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除这条记录？") },
            text = { Text("删除后不能在应用内恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete(record)
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            },
        )
    }
}
```

- [ ] **Step 3: 升级 EmptyTimeline 空状态**

将 `EmptyTimeline` 函数替换为：

```kotlin
@Composable
private fun EmptyTimeline() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "还没有记录。先写下此刻做了什么。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

- [ ] **Step 4: 编译验证**

Run: `gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 运行 UI 测试确保文本节点保留**

Run: `gradlew.bat connectedAndroidTest`（需要连接设备/模拟器）
Expected: `RoyalNoteAppTest.showsEditorAndTimelineRecord` 通过

关键断言：
- "起居注" 显示
- "做了什么" 显示
- 2 个 "未输入"（编辑器 FilterChip + 记录卡片 AssistChip）
- "读了半小时书" 显示
- "心里安稳了一点" 显示

如果无设备可用，至少运行单元测试：
Run: `gradlew.bat test`
Expected: 所有测试通过

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/royalnote/MainActivity.kt
git commit -m "feat(ui): 记录卡片左侧竖线+心情 chip 色彩，时间线圆点节点，空状态装饰"
```

---

## 验收标准

1. `gradlew.bat assembleDebug` 编译通过
2. `gradlew.bat test` 单元测试全通过
3. `gradlew.bat connectedAndroidTest` UI 测试通过（如有设备）
4. 浅色模式：宣纸米黄底色 + 朱砂强调 + 七色心情 chip
5. 深色模式：深墨底色 + 暖朱强调
6. 所有文字用 Serif 字体
7. 卡片左侧有朱砂竖线 + 细边框
8. 时间线有圆点节点
