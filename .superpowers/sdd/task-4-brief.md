### Task 4: MainActivity.kt — 标题区 + 编辑器卡片 + 心情 chip 色彩

**Files:**
- Modify: `app/src/main/java/com/example/royalnote/MainActivity.kt`

**Interfaces:**
- Consumes: Task 1 的心情色常量（MoodRed, MoodOchre, MoodCeladon, MoodGray, MoodBrick, MoodInkBlue, MoodPurple），Task 2 的主题，Task 3 的 Typography
- Produces: 升级后的 `RoyalNoteApp`、`RecordEditor`、`moodColor` 辅助函数

**Important context:** The current MainActivity.kt has these sections that need modification:
1. Import block (lines 1-56)
2. `RoyalNoteApp` composable (lines 87-147) — contains the title item and RecordEditor call
3. `RecordEditor` composable (lines 149-198)
4. `formatTime` function (lines 257-262) — moodColor helper goes after this

**CRITICAL: Preserve these exact text strings** (UI tests assert them):
- "起居注" — title text
- "做了什么" — OutlinedTextField label
- "未输入" — appears TWICE: once in RecordEditor's FilterChip, once in RecordCard's AssistChip (Task 5 handles RecordCard)
- "心情补充，可不填" — OutlinedTextField label
- "快速记录" / "编辑记录" — editor title
- "保存" / "保存修改" — button text
- "取消" — cancel button text

- [ ] **Step 1: 添加 moodColor 辅助函数和新增 import**

Add these imports to the import block at the top of `app/src/main/java/com/example/royalnote/MainActivity.kt`:

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

Add the `moodColor` helper function at the end of the file (after `formatTime`):

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

In `RoyalNoteApp`, replace the first `item { }` block (the one with "起居注" title) with:

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

Replace the `Card` call inside `RecordEditor` with:

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

- [ ] **Step 5: 运行单元测试**

Run: `gradlew.bat test`
Expected: 所有测试通过

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/royalnote/MainActivity.kt
git commit -m "feat(ui): 标题区朱砂色+分隔线，编辑器卡片左侧竖线，心情 chip 七色"
```
