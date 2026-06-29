### Task 5: MainActivity.kt — 记录卡片 + 时间线视觉化 + 空状态

**Files:**
- Modify: `app/src/main/java/com/example/royalnote/MainActivity.kt`

**Interfaces:**
- Consumes: Task 4 的 `moodColor` 辅助函数，Task 1 的心情色常量
- Produces: 升级后的 `TimelineHeader`、`RecordCard`、`EmptyTimeline`

**Important context:** Task 4 already added these imports that Task 5 will use:
- `CircleShape`, `Alignment`, `AssistChipDefaults`, `size`, `wrapContentWidth`
- All mood color constants
- `drawBehind`, `Offset`, `Size`, `border`, `background`, `Box`, `RoundedCornerShape`

Task 4 also added the `moodColor` helper function at the end of the file.

**CRITICAL: Preserve these exact text strings** (UI tests assert them):
- "未输入" — appears in RecordCard's AssistChip when moodTag is null (this is the SECOND "未输入" the test counts)
- "编辑", "删除" — RecordCard buttons
- "删除这条记录？", "删除后不能在应用内恢复。" — delete dialog
- "还没有记录。先写下此刻做了什么。" — empty state text

**Note on drawBehind:** Like Task 4, if you need to use `MaterialTheme.colorScheme` inside `drawBehind`, you MUST extract it to a local `val` first (e.g., `val primaryColor = MaterialTheme.colorScheme.primary`) because `drawBehind`'s lambda is not a @Composable context.

- [ ] **Step 1: 升级 TimelineHeader 为时间线节点**

Replace the `TimelineHeader` function with:

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

Replace the `RecordCard` function with:

```kotlin
@Composable
private fun RecordCard(
    record: NoteRecord,
    onEdit: (NoteRecord) -> Unit,
    onDelete: (NoteRecord) -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val moodColorValue = moodColor(record.moodTag)
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp)
            .border(
                width = 1.dp,
                color = outlineColor,
                shape = RoundedCornerShape(6.dp),
            )
            .drawBehind {
                drawRect(
                    color = primaryColor,
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
                color = onSurfaceVariantColor,
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

Replace the `EmptyTimeline` function with:

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

- [ ] **Step 5: 运行单元测试**

Run: `gradlew.bat test`
Expected: 所有测试通过

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/royalnote/MainActivity.kt
git commit -m "feat(ui): 记录卡片左侧竖线+心情 chip 色彩，时间线圆点节点，空状态装饰"
```
