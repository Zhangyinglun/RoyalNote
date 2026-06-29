# 原地修订记录实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 点击记录「修订」后，记录卡片原地展开为编辑模块；顶部仅负责新增，不再被编辑覆盖。

**Architecture:** 在 `RecordTimelineUiState` 中为新增和编辑各保留一份独立的表单字段。`RecordEditor` 改为接受显式参数而非整个 `uiState`。时间线渲染时，匹配 `editingRecord.id` 的卡片替换为编辑模块。

**Tech Stack:** Kotlin, Compose, Material 3, Room, ViewModel + StateFlow

## Global Constraints

- 应用名固定为 `起居注`，不增加搜索、筛选、手动时间编辑或云同步
- 情绪标签固定为 `开心、满足、平静、疲惫、烦躁、低落、焦虑`
- 正文为空时不允许保存，提示 `未记今日之事，不宜入录`
- 编辑保留原 `createdAt`，更新 `updatedAt`
- 墨香主题：铜色主色，古代低饱和情绪色，Serif 字体系列
- `dynamicColor = false`，不引入 Material You 动态色

---

### Task 1: 拆分表单状态——UiState 新增编辑字段

**Files:**
- Modify: `app/src/main/java/com/example/royalnote/ui/RecordTimelineViewModel.kt:35-44`
- Modify: `app/src/test/java/com/example/royalnote/ui/RecordTimelineViewModelTest.kt`

**Interfaces:**
- Consumes: 现有 `RecordTimelineUiState` 结构
- Produces: `RecordTimelineUiState` 新增 `editEventText: String`, `editSelectedMood: String?`, `editMoodNote: String`

- [ ] **Step 1: 在 UiState 中添加编辑字段**

```kotlin
data class RecordTimelineUiState(
    val eventText: String = "",
    val selectedMood: String? = null,
    val moodNote: String = "",
    val editEventText: String = "",
    val editSelectedMood: String? = null,
    val editMoodNote: String = "",
    val editingRecord: NoteRecord? = null,
    val timelineDays: List<TimelineDay> = emptyList(),
    val message: String? = null,
) {
    val isEditing: Boolean = editingRecord != null
}
```

- [ ] **Step 2: 运行现有测试验证兼容**

Run: `.\gradlew.bat testDebugUnitTest`

Expected: 所有现有测试仍然通过（新增字段有默认值，不影响已有行为）。

---

### Task 2: ViewModel 新增编辑表单方法和独立保存逻辑（TDD）

**Files:**
- Modify: `app/src/test/java/com/example/royalnote/ui/RecordTimelineViewModelTest.kt`
- Modify: `app/src/main/java/com/example/royalnote/ui/RecordTimelineViewModel.kt`

**Interfaces:**
- Consumes: Task 1 的 `RecordTimelineUiState` 新字段
- Produces: `updateEditEventText(String)`, `selectEditMood(String?)`, `updateEditMoodNote(String)` 三个新增方法；修改后的 `startEditing`, `save`, `cancelEditing`, `delete`

- [ ] **Step 1: 编写失败测试——startEditing 不覆盖新增草稿**

在 `RecordTimelineViewModelTest.kt` 中添加：

```kotlin
@Test
fun startEditingDoesNotOverwriteNewFormDraft() = runTest {
    val existing = NoteRecord(1, "散步", "疲惫", null, 1000L, 1000L)
    repository.records.value = listOf(existing)
    val viewModel = RecordTimelineViewModel(repository, clock)

    viewModel.updateEventText("写日记")
    viewModel.selectMood("平静")
    viewModel.updateMoodNote("今日安稳")
    viewModel.startEditing(existing)

    val state = viewModel.uiState.value
    assertEquals("写日记", state.eventText)
    assertEquals("平静", state.selectedMood)
    assertEquals("今日安稳", state.moodNote)

    assertEquals("散步", state.editEventText)
    assertEquals("疲惫", state.editSelectedMood)
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `.\gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.ui.RecordTimelineViewModelTest.startEditingDoesNotOverwriteNewFormDraft"`

Expected: FAIL — editEventText 或 editSelectedMood 不存在或值为空。

- [ ] **Step 3: 添加三个编辑表单更新方法**

在 `RecordTimelineViewModel.kt` 中添加：

```kotlin
fun updateEditEventText(value: String) {
    formState.update { it.copy(editEventText = value, message = null) }
}

fun selectEditMood(value: String?) {
    formState.update { it.copy(editSelectedMood = value, message = null) }
}

fun updateEditMoodNote(value: String) {
    formState.update { it.copy(editMoodNote = value, message = null) }
}
```

- [ ] **Step 4: 修改 startEditing 只填充编辑字段**

```kotlin
fun startEditing(record: NoteRecord) {
    formState.update { state ->
        state.copy(
            editEventText = record.eventText,
            editSelectedMood = record.moodTag,
            editMoodNote = record.moodNote.orEmpty(),
            editingRecord = record,
        )
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `.\gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.ui.RecordTimelineViewModelTest.startEditingDoesNotOverwriteNewFormDraft"`

Expected: PASS

- [ ] **Step 6: 编写失败测试——编辑保存使用 edit 字段，成功后只清编辑状态**

```kotlin
@Test
fun saveEditUsesEditFieldsAndClearsOnlyEditState() = runTest {
    val existing = NoteRecord(1, "散步", "疲惫", null, 1000L, 1000L)
    repository.records.value = listOf(existing)
    val viewModel = RecordTimelineViewModel(repository, clock)

    // 顶部有新增草稿
    viewModel.updateEventText("写日记")
    viewModel.updateMoodNote("今日安稳")

    viewModel.startEditing(existing)
    viewModel.updateEditEventText("散步二十分钟")
    viewModel.selectEditMood(null)
    viewModel.updateEditMoodNote("比出门前轻松")
    viewModel.save()
    advanceUntilIdle()

    val updated = repository.records.value.single()
    assertEquals(1L, updated.id)
    assertEquals("散步二十分钟", updated.eventText)
    assertNull(updated.moodTag)
    assertEquals("比出门前轻松", updated.moodNote)
    assertEquals(1000L, updated.createdAt)
    assertEquals(clock.millis(), updated.updatedAt)

    // 编辑状态已清
    assertFalse(viewModel.uiState.value.isEditing)
    assertEquals("", viewModel.uiState.value.editEventText)
    assertNull(viewModel.uiState.value.editSelectedMood)
    assertEquals("", viewModel.uiState.value.editMoodNote)

    // 新增草稿保留
    assertEquals("写日记", viewModel.uiState.value.eventText)
    assertEquals("今日安稳", viewModel.uiState.value.moodNote)
}
```

- [ ] **Step 7: 运行测试并确认失败**

Run: `.\gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.ui.RecordTimelineViewModelTest.saveEditUsesEditFieldsAndClearsOnlyEditState"`

Expected: FAIL — save 仍然使用 eventText/selectedMood 字段而非 editEventText/editSelectedMood。

- [ ] **Step 8: 修改 save 使用编辑字段**

```kotlin
fun save() {
    val state = formState.value
    val isEditing = state.editingRecord != null
    val eventText = (if (isEditing) state.editEventText else state.eventText).trim()
    val selectedMood = if (isEditing) state.editSelectedMood else state.selectedMood
    val rawMoodNote = if (isEditing) state.editMoodNote else state.moodNote

    if (eventText.isEmpty()) {
        formState.update { it.copy(message = "未记今日之事，不宜入录") }
        return
    }

    viewModelScope.launch {
        runCatching {
            val moodNote = rawMoodNote.trim().ifEmpty { null }
            val now = clock.millis()
            val editing = state.editingRecord
            if (editing == null) {
                repository.addRecord(eventText, selectedMood, moodNote, now)
            } else {
                repository.updateRecord(
                    editing.copy(
                        eventText = eventText,
                        moodTag = selectedMood,
                        moodNote = moodNote,
                        updatedAt = now,
                    )
                )
            }
        }.onSuccess {
            if (isEditing) {
                formState.update { it.copy(editEventText = "", editSelectedMood = null, editMoodNote = "", editingRecord = null) }
            } else {
                formState.update { it.copy(eventText = "", selectedMood = null, moodNote = "") }
            }
        }.onFailure {
            formState.update { it.copy(message = "入录未成，烦请再试") }
        }
    }
}
```

- [ ] **Step 9: 运行测试确认通过**

Run: `.\gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.ui.RecordTimelineViewModelTest.saveEditUsesEditFieldsAndClearsOnlyEditState"`

Expected: PASS

- [ ] **Step 10: 编写失败测试——cancelEditing 只清编辑状态**

```kotlin
@Test
fun cancelEditingClearsOnlyEditState() = runTest {
    val existing = NoteRecord(1, "散步", "疲惫", null, 1000L, 1000L)
    repository.records.value = listOf(existing)
    val viewModel = RecordTimelineViewModel(repository, clock)

    viewModel.updateEventText("写日记")
    viewModel.updateMoodNote("今日安稳")
    viewModel.startEditing(existing)
    viewModel.updateEditEventText("散步二十分钟")
    viewModel.cancelEditing()

    assertFalse(viewModel.uiState.value.isEditing)
    assertEquals("", viewModel.uiState.value.editEventText)
    assertNull(viewModel.uiState.value.editSelectedMood)
    assertEquals("", viewModel.uiState.value.editMoodNote)

    assertEquals("写日记", viewModel.uiState.value.eventText)
    assertEquals("今日安稳", viewModel.uiState.value.moodNote)
}
```

- [ ] **Step 11: 运行测试并确认失败**

Run: `.\gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.ui.RecordTimelineViewModelTest.cancelEditingClearsOnlyEditState"`

Expected: FAIL — cancelEditing 清空整个 formState，包括新增字段。

- [ ] **Step 12: 修改 cancelEditing 和 delete**

```kotlin
fun cancelEditing() {
    formState.update { it.copy(editEventText = "", editSelectedMood = null, editMoodNote = "", editingRecord = null) }
}

fun delete(record: NoteRecord) {
    viewModelScope.launch {
        runCatching { repository.deleteRecord(record) }
            .onSuccess {
                formState.update { state ->
                    if (state.editingRecord?.id == record.id) {
                        state.copy(editEventText = "", editSelectedMood = null, editMoodNote = "", editingRecord = null)
                    } else state
                }
            }
            .onFailure {
                formState.update { state -> state.copy(message = "抹去未成，烦请再试") }
            }
    }
}
```

- [ ] **Step 13: 运行测试确认通过**

Run: `.\gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.ui.RecordTimelineViewModelTest.cancelEditingClearsOnlyEditState"`

Expected: PASS

- [ ] **Step 14: 更新旧测试 editUpdatesRecordWithoutChangingCreatedAt**

旧测试使用了 `updateEventText`/`selectMood`/`updateMoodNote` 做编辑操作，现在编辑应使用 `updateEditEventText`/`selectEditMood`/`updateEditMoodNote`。将其改为：

```kotlin
@Test
fun editUpdatesRecordWithoutChangingCreatedAt() = runTest {
    val existing = NoteRecord(1, "散步", "疲惫", null, 1000L, 1000L)
    repository.records.value = listOf(existing)
    val viewModel = RecordTimelineViewModel(repository, clock)

    viewModel.startEditing(existing)
    viewModel.updateEditEventText("散步二十分钟")
    viewModel.selectEditMood(null)
    viewModel.updateEditMoodNote("比出门前轻松")
    viewModel.save()
    advanceUntilIdle()

    val updated = repository.records.value.single()
    assertEquals(1L, updated.id)
    assertEquals("散步二十分钟", updated.eventText)
    assertNull(updated.moodTag)
    assertEquals("比出门前轻松", updated.moodNote)
    assertEquals(1000L, updated.createdAt)
    assertEquals(clock.millis(), updated.updatedAt)
    assertFalse(viewModel.uiState.value.isEditing)
}
```

- [ ] **Step 15: 运行全量单元测试**

Run: `.\gradlew.bat testDebugUnitTest`

Expected: 所有测试 PASS

---

### Task 3: 重构 RecordEditor 为显式参数

**Files:**
- Modify: `app/src/main/java/com/example/royalnote/MainActivity.kt:206-279`

**Interfaces:**
- Consumes: 当前 `RecordEditor` 签名
- Produces: `RecordEditor` 改为接受 `eventText`, `selectedMood`, `moodNote`, `title`, `saveLabel`, `showCancel` 等显式参数

- [ ] **Step 1: 重构 RecordEditor 签名和内部使用**

将 `RecordEditor` 从：

```kotlin
@Composable
private fun RecordEditor(
    uiState: RecordTimelineUiState,
    onEventTextChange: (String) -> Unit,
    onMoodSelected: (String?) -> Unit,
    onMoodNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
)
```

改为：

```kotlin
@Composable
private fun RecordEditor(
    eventText: String,
    selectedMood: String?,
    moodNote: String,
    title: String,
    saveLabel: String,
    showCancel: Boolean,
    onEventTextChange: (String) -> Unit,
    onMoodSelected: (String?) -> Unit,
    onMoodNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
)
```

函数体内替换所有引用：
- `uiState.eventText` → `eventText`
- `uiState.selectedMood` → `selectedMood`
- `uiState.moodNote` → `moodNote`
- `if (uiState.isEditing) "修订此则" else "速录一则"` → `title`
- `if (uiState.isEditing) "改毕入录" else "入录"` → `saveLabel`
- `if (uiState.isEditing) { TextButton(...) }` → `if (showCancel) { TextButton(...) }`

- [ ] **Step 2: 更新顶部调用**

顶部 RecordEditor 调用改为：

```kotlin
item {
    RecordEditor(
        eventText = uiState.eventText,
        selectedMood = uiState.selectedMood,
        moodNote = uiState.moodNote,
        title = "速录一则",
        saveLabel = "入录",
        showCancel = false,
        onEventTextChange = onEventTextChange,
        onMoodSelected = onMoodSelected,
        onMoodNoteChange = onMoodNoteChange,
        onSave = onSave,
        onCancelEdit = {},
    )
}
```

- [ ] **Step 3: 编辑时不显示顶部编辑器**

顶部编辑器只有在非编辑态时才显示（或永远显示）：

当前代码中 `RecordEditor` 放在独立 `item {}` 里，始终渲染。改为始终保持渲染，只给 `title = "速录一则"`, `saveLabel = "入录"`, `showCancel = false`。

- [ ] **Step 4: 构建验证**

Run: `.\gradlew.bat assembleDebug`

Expected: BUILD SUCCESSFUL

---

### Task 4: 时间线原地编辑 + 新回调（TDD）

**Files:**
- Modify: `app/src/main/java/com/example/royalnote/MainActivity.kt:98-107, 114-201`
- Modify: `app/src/androidTest/java/com/example/royalnote/ui/RoyalNoteAppTest.kt`

**Interfaces:**
- Consumes: Task 2 的 ViewModel edit 方法，Task 3 的 RecordEditor 新签名
- Produces: `RoyalNoteApp` 新增 `onEditEventTextChange`, `onEditMoodSelected`, `onEditMoodNoteChange` 三个回调；时间线在匹配 `editingRecord.id` 时渲染 `RecordEditor`

- [ ] **Step 1: 编写 Compose UI 测试——编辑态时顶部仍为「速录一则」，时间线显示「修订此则」**

在 `RoyalNoteAppTest.kt` 中添加：

```kotlin
@Test
fun showsInlineEditorWhenEditingAndTopStaysNewForm() {
    val record = NoteRecord(
        id = 1,
        eventText = "读了半小时书",
        moodTag = null,
        moodNote = "心里安稳了一点",
        createdAt = 1_787_777_700_000L,
        updatedAt = 1_787_777_700_000L,
    )
    composeRule.setContent {
        RoyalNoteTheme {
            RoyalNoteApp(
                uiState = RecordTimelineUiState(
                    editEventText = "修改后的内容",
                    editingRecord = record,
                    timelineDays = listOf(
                        TimelineDay(
                            label = "今日",
                            records = listOf(record),
                        ),
                    ),
                ),
                onEventTextChange = {},
                onMoodSelected = {},
                onMoodNoteChange = {},
                onEditEventTextChange = {},
                onEditMoodSelected = {},
                onEditMoodNoteChange = {},
                onSave = {},
                onEdit = {},
                onCancelEdit = {},
                onDelete = {},
                onMessageShown = {},
            )
        }
    }
    composeRule.onNodeWithText("速录一则").assertIsDisplayed()
    composeRule.onNodeWithText("修订此则").assertIsDisplayed()
    composeRule.onNodeWithText("改毕入录").assertIsDisplayed()
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `.\gradlew.bat connectedDebugAndroidTest`

Expected: FAIL — `RoyalNoteApp` 缺少 `onEditEventTextChange` 等参数编译错误。

- [ ] **Step 3: 修改 RoyalNoteApp 签名，添加编辑回调**

```kotlin
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
)
```

- [ ] **Step 4: 在时间线 items 中实现原地编辑**

将：

```kotlin
items(day.records, key = { it.id }) { record ->
    RecordCard(record = record, onEdit = onEdit, onDelete = onDelete)
}
```

改为：

```kotlin
items(day.records, key = { it.id }) { record ->
    if (uiState.editingRecord?.id == record.id) {
        RecordEditor(
            eventText = uiState.editEventText,
            selectedMood = uiState.editSelectedMood,
            moodNote = uiState.editMoodNote,
            title = "修订此则",
            saveLabel = "改毕入录",
            showCancel = true,
            onEventTextChange = onEditEventTextChange,
            onMoodSelected = onEditMoodSelected,
            onMoodNoteChange = onEditMoodNoteChange,
            onSave = onSave,
            onCancelEdit = onCancelEdit,
        )
    } else {
        RecordCard(record = record, onEdit = onEdit, onDelete = onDelete)
    }
}
```

- [ ] **Step 5: 更新 MainActivity 中 RoyalNoteApp 调用**

```kotlin
RoyalNoteApp(
    uiState = uiState,
    onEventTextChange = viewModel::updateEventText,
    onMoodSelected = viewModel::selectMood,
    onMoodNoteChange = viewModel::updateMoodNote,
    onEditEventTextChange = viewModel::updateEditEventText,
    onEditMoodSelected = viewModel::selectEditMood,
    onEditMoodNoteChange = viewModel::updateEditMoodNote,
    onSave = viewModel::save,
    onEdit = viewModel::startEditing,
    onCancelEdit = viewModel::cancelEditing,
    onDelete = viewModel::delete,
    onMessageShown = viewModel::clearMessage,
)
```

- [ ] **Step 6: 更新 Preview 函数**

```kotlin
@Preview(showBackground = true)
@Composable
private fun RoyalNotePreview() {
    RoyalNoteTheme {
        RoyalNoteApp(
            uiState = RecordTimelineUiState(
                eventText = "整理书桌",
                selectedMood = "平静",
                moodNote = "心里安稳了一点",
                timelineDays = listOf(
                    TimelineDay(
                        label = "今日",
                        records = listOf(
                            NoteRecord(
                                id = 1,
                                eventText = "读了半晌书",
                                moodTag = "满足",
                                moodNote = null,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis(),
                            )
                        ),
                    )
                ),
            ),
            onEventTextChange = {},
            onMoodSelected = {},
            onMoodNoteChange = {},
            onEditEventTextChange = {},
            onEditMoodSelected = {},
            onEditMoodNoteChange = {},
            onSave = {},
            onEdit = {},
            onCancelEdit = {},
            onDelete = {},
            onMessageShown = {},
        )
    }
}
```

- [ ] **Step 7: 更新 androidTest 中的现有测试**

将 `RoyalNoteAppTest.showsEditorAndTimelineRecord` 中的 `RoyalNoteApp` 调用补上新回调：

```kotlin
onEditEventTextChange = {},
onEditMoodSelected = {},
onEditMoodNoteChange = {},
```

- [ ] **Step 8: 构建验证**

Run: `.\gradlew.bat assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 9: 运行全量单元测试**

Run: `.\gradlew.bat testDebugUnitTest`

Expected: 所有测试 PASS

- [ ] **Step 10: 运行 Compose UI 测试**

Run: `.\gradlew.bat connectedDebugAndroidTest`

Expected: 所有测试 PASS
