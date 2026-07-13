# 起居注时间段记录 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让手动新增、编辑和 AI 批量导入都能保存开始与结束时间，并让时间线按开始时间排序、分组和展示。

**Architecture:** 在 `NoteRecord` 中加入必填的 `startedAt`、`endedAt`，保留 `createdAt`、`updatedAt` 作为记录元数据。ViewModel 持有新增和编辑表单的时间状态并执行校验，独立的时间格式与替换函数负责时区转换，Compose 组件只处理选择器和事件回调。

**Tech Stack:** Kotlin 2.2.10、Jetpack Compose Material 3（Compose BOM 2026.02.01）、Room 2.8.4、StateFlow、kotlinx-coroutines-test 1.9.0、JUnit 4、Compose UI Test。

## Global Constraints

- `minSdk = 33`，`targetSdk = 36`，`compileSdk = 36.1`。
- 不新增 Gradle 依赖；继续使用现有 Compose BOM 和 Room/KSP 配置。
- Room 数据库从版本 1 升到版本 2，并允许破坏性重建；应用尚未上线，不迁移开发数据。
- `startedAt`、`endedAt`、`createdAt`、`updatedAt` 均使用 Unix 毫秒时间戳，界面按设备当前时区转换。
- 结束时间允许等于开始时间，但不得早于开始时间。
- 跨日记录按 `startedAt` 所在日期分组，只出现一次。
- 时间线只展示活动起止时间，不展示 `createdAt` 或 `updatedAt`。
- 保持“墨香”配色、衬线字体、8dp 卡片圆角和现有中文文案风格。
- 不实现实时计时器、多时间段、时长统计、搜索或筛选。
- 未经用户明确要求，不执行 Git 提交；每个任务结束时只保留可审查的工作区变更。

---

## File Map

- Modify `app/src/main/java/com/example/royalnote/data/NoteRecord.kt`: 增加活动起止时间。
- Modify `app/src/main/java/com/example/royalnote/data/NoteRecordDao.kt`: 按开始时间和创建时间倒序查询。
- Modify `app/src/main/java/com/example/royalnote/data/RoyalNoteDatabase.kt`: 升级数据库并允许重建开发数据。
- Modify `app/src/main/java/com/example/royalnote/data/NoteRepository.kt`: 新增记录时写入四个时间字段。
- Modify `app/src/main/java/com/example/royalnote/SeedData.kt`: 为初始记录提供零分钟时间段。
- Modify `app/src/main/java/com/example/royalnote/ui/RecordTimelineViewModel.kt`: 管理起止时间、校验、编辑和日期分组。
- Create `app/src/main/java/com/example/royalnote/ui/TimeRangeFormatting.kt`: 集中处理日期替换、时间替换和展示格式。
- Create `app/src/main/java/com/example/royalnote/ui/TimeRangeFields.kt`: 提供两行时间选择 UI、日期选择器和时间选择器。
- Modify `app/src/main/java/com/example/royalnote/MainActivity.kt`: 接入时间状态、回调、选择器和卡片格式。
- Modify `app/src/main/java/com/example/royalnote/network/OpenRouterModels.kt`: 导入 DTO 改为起止时间。
- Modify `app/src/main/java/com/example/royalnote/network/OpenRouterService.kt`: 更新结构化输出提示词。
- Modify `app/src/main/java/com/example/royalnote/ui/ImportViewModel.kt`: 解析时间段并分离活动时间与导入元数据时间。
- Create `app/src/test/java/com/example/royalnote/data/NoteRepositoryTest.kt`: 验证 Repository 写入字段。
- Modify `app/src/test/java/com/example/royalnote/ui/RecordTimelineViewModelTest.kt`: 验证表单、校验、编辑和分组。
- Create `app/src/test/java/com/example/royalnote/ui/TimeRangeFormattingTest.kt`: 验证时区与格式化。
- Modify `app/src/test/java/com/example/royalnote/ui/ImportViewModelTest.kt`: 验证时间段、单时刻和失败兜底。
- Modify `app/src/androidTest/java/com/example/royalnote/RoyalNoteAppTest.kt`: 验证时间控件、选择器和卡片文案。

---

### Task 1: Extend The Room Record Schema

**Files:**
- Create: `app/src/test/java/com/example/royalnote/data/NoteRepositoryTest.kt`
- Modify: `app/src/main/java/com/example/royalnote/data/NoteRecord.kt`
- Modify: `app/src/main/java/com/example/royalnote/data/NoteRecordDao.kt`
- Modify: `app/src/main/java/com/example/royalnote/data/RoyalNoteDatabase.kt`
- Modify: `app/src/main/java/com/example/royalnote/data/NoteRepository.kt`
- Modify: `app/src/main/java/com/example/royalnote/ui/RecordTimelineViewModel.kt`
- Modify: `app/src/main/java/com/example/royalnote/SeedData.kt`
- Modify: all existing `NoteRecord(...)` fixtures so the project compiles with the new required fields

**Interfaces:**
- Produces: `NoteRecord.startedAt: Long`, `NoteRecord.endedAt: Long`.
- Produces: `RecordOperations.addRecord(eventText, moodTag, moodNote, startedAt, endedAt, nowMillis)`.
- Produces: DAO ordering contract `startedAt DESC, createdAt DESC`.

- [ ] **Step 1: Write a failing Repository test**

Create a small fake DAO that captures the inserted entity and assert that activity time and metadata time remain separate:

```kotlin
@Test
fun addRecordStoresActivityRangeAndMetadataTime() = runTest {
    val dao = CapturingNoteRecordDao()
    val repository = NoteRepository(dao)

    repository.addRecord(
        eventText = "整理书桌",
        moodTag = "平静",
        moodNote = null,
        startedAt = 1_000L,
        endedAt = 4_000L,
        nowMillis = 9_000L,
    )

    assertEquals(1_000L, dao.inserted?.startedAt)
    assertEquals(4_000L, dao.inserted?.endedAt)
    assertEquals(9_000L, dao.inserted?.createdAt)
    assertEquals(9_000L, dao.inserted?.updatedAt)
}
```

Add this fake below the test class:

```kotlin
private class CapturingNoteRecordDao : NoteRecordDao {
    var inserted: NoteRecord? = null

    override fun observeRecords(): Flow<List<NoteRecord>> = flowOf(emptyList())

    override suspend fun insert(record: NoteRecord) {
        inserted = record
    }

    override suspend fun insertAll(records: List<NoteRecord>) = Unit

    override suspend fun update(record: NoteRecord) = Unit

    override suspend fun delete(record: NoteRecord) = Unit
}
```

- [ ] **Step 2: Run the focused test and confirm the red state**

Run: `.\gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.data.NoteRepositoryTest"`

Expected: compilation fails because `startedAt`, `endedAt`, and the expanded `addRecord` signature do not exist.

- [ ] **Step 3: Add the required entity fields and Repository signature**

Use this entity shape:

```kotlin
@Entity(tableName = "note_records")
data class NoteRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventText: String,
    val moodTag: String?,
    val moodNote: String?,
    val startedAt: Long,
    val endedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
)
```

Change `RecordOperations.addRecord` and `NoteRepository.addRecord` to accept `startedAt` and `endedAt` before `nowMillis`. The Repository method body is:

```kotlin
override suspend fun addRecord(
    eventText: String,
    moodTag: String?,
    moodNote: String?,
    startedAt: Long,
    endedAt: Long,
    nowMillis: Long,
) {
    dao.insert(
        NoteRecord(
            eventText = eventText,
            moodTag = moodTag,
            moodNote = moodNote,
            startedAt = startedAt,
            endedAt = endedAt,
            createdAt = nowMillis,
            updatedAt = nowMillis,
        )
    )
}
```

- [ ] **Step 4: Update Room and seed behavior**

Change the DAO query to:

```kotlin
@Query("SELECT * FROM note_records ORDER BY startedAt DESC, createdAt DESC")
fun observeRecords(): Flow<List<NoteRecord>>
```

Set `@Database(..., version = 2, ...)` and append `.fallbackToDestructiveMigration(true)` to the `Room.databaseBuilder(...)` chain. In `SeedData`, pass the same current millisecond value as `startedAt`, `endedAt`, and `nowMillis`.

- [ ] **Step 5: Update constructor fixtures without changing their test intent**

Convert positional `NoteRecord(...)` calls to named arguments. For existing point-in-time fixtures, assign the old occurrence time to both `startedAt` and `endedAt`; keep existing `createdAt` and `updatedAt` assertions unchanged. Update fake `RecordOperations` implementations to accept the expanded `addRecord` signature.

- [ ] **Step 6: Run the data test and compile all test sources**

Run: `.\gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.data.NoteRepositoryTest"`

Expected: PASS.

Run: `.\gradlew.bat testDebugUnitTest`

Expected: all existing JVM tests compile and pass before behavior changes begin.

---

### Task 2: Add Time Range State And Validation

**Files:**
- Modify: `app/src/test/java/com/example/royalnote/ui/RecordTimelineViewModelTest.kt`
- Modify: `app/src/main/java/com/example/royalnote/ui/RecordTimelineViewModel.kt`

**Interfaces:**
- Consumes: expanded `RecordOperations.addRecord(...)` from Task 1.
- Produces: `startedAt`, `endedAt`, `editStartedAt`, `editEndedAt` in `RecordTimelineUiState`.
- Produces: `updateStartedAt`, `updateEndedAt`, `updateEditStartedAt`, `updateEditEndedAt`.

- [ ] **Step 1: Add failing tests for initialization, adjustment, validation, save, edit, reset, and grouping**

Add focused tests with the existing fixed `Clock`:

```kotlin
@Test
fun newFormStartsAsZeroMinuteRangeAtCurrentTime() {
    val state = RecordTimelineViewModel(repository, clock).uiState.value
    assertEquals(clock.millis(), state.startedAt)
    assertEquals(clock.millis(), state.endedAt)
}

@Test
fun movingStartPastEndMovesEndToStart() {
    val viewModel = RecordTimelineViewModel(repository, clock)
    viewModel.updateStartedAt(clock.millis() + 60_000L)
    assertEquals(clock.millis() + 60_000L, viewModel.uiState.value.endedAt)
}

@Test
fun saveRejectsEndBeforeStart() = runTest {
    val viewModel = RecordTimelineViewModel(repository, clock)
    viewModel.updateEventText("夜读")
    viewModel.updateEndedAt(clock.millis() - 60_000L)
    viewModel.save()
    advanceUntilIdle()
    assertEquals("结束时间不可早于开始时间", viewModel.uiState.value.message)
    assertTrue(repository.records.value.isEmpty())
}
```

Extend `saveAddsRecordAndClearsForm` to assert saved `startedAt`/`endedAt` and that both new-form values reset to `clock.millis()`. Extend edit tests to assert time range backfill, updated activity time, unchanged `createdAt`, changed `updatedAt`, and untouched top-form times. Change the grouping test so `createdAt` values are deliberately unrelated and labels are determined only by `startedAt`; include a cross-day record under its start date.

- [ ] **Step 2: Run the ViewModel tests and confirm failure**

Run: `.\gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.ui.RecordTimelineViewModelTest"`

Expected: compilation fails on the new state fields and update methods.

- [ ] **Step 3: Initialize and update time state in the ViewModel**

Add non-null new-form fields and nullable edit fields:

```kotlin
data class RecordTimelineUiState(
    val eventText: String = "",
    val selectedMood: String? = null,
    val moodNote: String = "",
    val startedAt: Long = 0L,
    val endedAt: Long = 0L,
    val editEventText: String = "",
    val editSelectedMood: String? = null,
    val editMoodNote: String = "",
    val editStartedAt: Long? = null,
    val editEndedAt: Long? = null,
    val editingRecord: NoteRecord? = null,
    val timelineDays: List<TimelineDay> = emptyList(),
    val message: String? = null,
)
```

Initialize `formState` with one `clock.millis()` value for both new-form fields, then add these methods:

```kotlin
private val formState = MutableStateFlow(
    clock.millis().let { now ->
        RecordTimelineUiState(startedAt = now, endedAt = now)
    }
)

fun updateStartedAt(value: Long) {
    formState.update { state ->
        state.copy(startedAt = value, endedAt = maxOf(state.endedAt, value), message = null)
    }
}

fun updateEndedAt(value: Long) {
    formState.update { it.copy(endedAt = value, message = null) }
}

fun updateEditStartedAt(value: Long) {
    formState.update { state ->
        state.copy(
            editStartedAt = value,
            editEndedAt = maxOf(state.editEndedAt ?: value, value),
            message = null,
        )
    }
}

fun updateEditEndedAt(value: Long) {
    formState.update { it.copy(editEndedAt = value, message = null) }
}
```

- [ ] **Step 4: Add save validation and correct reset behavior**

In `save()`, select the active form's range. For edit fields, fall back to `editingRecord.startedAt`/`endedAt` rather than throwing:

```kotlin
val editing = state.editingRecord
val startedAt = if (editing == null) state.startedAt else state.editStartedAt ?: editing.startedAt
val endedAt = if (editing == null) state.endedAt else state.editEndedAt ?: editing.endedAt

if (endedAt < startedAt) {
    formState.update { it.copy(message = "结束时间不可早于开始时间") }
    return
}
```

Validate blank text before this range check.

Pass the activity range into `addRecord`. On edit, copy `startedAt`, `endedAt`, and `updatedAt`, preserving `createdAt`. Use these reset shapes in the success branch:

```kotlin
if (isEditing) {
    formState.update {
        it.copy(
            editEventText = "",
            editSelectedMood = null,
            editMoodNote = "",
            editStartedAt = null,
            editEndedAt = null,
            editingRecord = null,
        )
    }
} else {
    val resetAt = clock.millis()
    formState.update {
        it.copy(
            eventText = "",
            selectedMood = null,
            moodNote = "",
            startedAt = resetAt,
            endedAt = resetAt,
        )
    }
}
```

`startEditing` copies both activity times into the nullable edit fields. `cancelEditing` and deleting the active edit clear both fields to `null` along with the existing edit fields.

- [ ] **Step 5: Group records by activity start**

Change `toTimelineDays` from `record.createdAt` to `record.startedAt`. Do not duplicate cross-day records into the end date.

- [ ] **Step 6: Run focused and full JVM tests**

Run: `.\gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.ui.RecordTimelineViewModelTest"`

Expected: PASS.

Run: `.\gradlew.bat testDebugUnitTest`

Expected: PASS.

---

### Task 3: Add Pure Time Conversion And Formatting

**Files:**
- Create: `app/src/test/java/com/example/royalnote/ui/TimeRangeFormattingTest.kt`
- Create: `app/src/main/java/com/example/royalnote/ui/TimeRangeFormatting.kt`

**Interfaces:**
- Produces: `toDatePickerMillis(millis, zoneId): Long`.
- Produces: `replaceDate(millis, selectedUtcDateMillis, zoneId): Long`.
- Produces: `replaceTime(millis, hour, minute, zoneId): Long`.
- Produces: `formatEditorDate`, `formatEditorTime`, `formatRecordTimeRange`.

- [ ] **Step 1: Write failing timezone and formatting tests**

Use `ZoneId.of("Asia/Shanghai")` and explicit `Instant` values. Cover local-date to UTC-date-picker conversion, replacing a date without changing local time, replacing time with seconds/nanos cleared, same-day range, zero-minute range, and cross-day range:

```kotlin
@Test
fun formatsSameDayAndCrossDayRanges() {
    val zone = ZoneId.of("Asia/Shanghai")
    val start = Instant.parse("2026-07-11T13:30:00Z").toEpochMilli()
    val sameDayEnd = Instant.parse("2026-07-11T15:00:00Z").toEpochMilli()
    val crossDayEnd = Instant.parse("2026-07-11T16:15:00Z").toEpochMilli()

    assertEquals("21:30–23:00", formatRecordTimeRange(start, sameDayEnd, zone))
    assertEquals("07-11 21:30–07-12 00:15", formatRecordTimeRange(start, crossDayEnd, zone))
    assertEquals("21:30–21:30", formatRecordTimeRange(start, start, zone))
}
```

- [ ] **Step 2: Run the focused test and confirm missing functions**

Run: `.\gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.ui.TimeRangeFormattingTest"`

Expected: compilation fails because the formatting functions do not exist.

- [ ] **Step 3: Implement the pure helpers**

Use `Instant`, `ZoneId`, `ZoneOffset.UTC`, and `DateTimeFormatter`. DatePicker values represent the selected calendar date at UTC midnight, so `replaceDate` reads that date in UTC and combines it with the original local time in the device zone. Implement the file as:

```kotlin
private val editorDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val crossDayFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

internal fun toDatePickerMillis(millis: Long, zoneId: ZoneId): Long {
    val localDate = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
    return localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

internal fun replaceDate(millis: Long, selectedUtcDateMillis: Long, zoneId: ZoneId): Long {
    val selectedDate = Instant.ofEpochMilli(selectedUtcDateMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
    val currentTime = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalTime()
    return selectedDate.atTime(currentTime).atZone(zoneId).toInstant().toEpochMilli()
}

internal fun replaceTime(millis: Long, hour: Int, minute: Int, zoneId: ZoneId): Long {
    return Instant.ofEpochMilli(millis)
        .atZone(zoneId)
        .withHour(hour)
        .withMinute(minute)
        .withSecond(0)
        .withNano(0)
        .toInstant()
        .toEpochMilli()
}

internal fun formatEditorDate(millis: Long, zoneId: ZoneId): String =
    Instant.ofEpochMilli(millis).atZone(zoneId).format(editorDateFormatter)

internal fun formatEditorTime(millis: Long, zoneId: ZoneId): String =
    Instant.ofEpochMilli(millis).atZone(zoneId).format(timeFormatter)

internal fun formatRecordTimeRange(startedAt: Long, endedAt: Long, zoneId: ZoneId): String {
    val start = Instant.ofEpochMilli(startedAt).atZone(zoneId)
    val end = Instant.ofEpochMilli(endedAt).atZone(zoneId)
    return if (start.toLocalDate() == end.toLocalDate()) {
        "${start.format(timeFormatter)}–${end.format(timeFormatter)}"
    } else {
        "${start.format(crossDayFormatter)}–${end.format(crossDayFormatter)}"
    }
}
```

- [ ] **Step 4: Run the focused test**

Run: `.\gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.ui.TimeRangeFormattingTest"`

Expected: PASS.

---

### Task 4: Build And Wire The Compose Time Pickers

**Files:**
- Create: `app/src/main/java/com/example/royalnote/ui/TimeRangeFields.kt`
- Modify: `app/src/main/java/com/example/royalnote/MainActivity.kt`
- Modify: `app/src/androidTest/java/com/example/royalnote/RoyalNoteAppTest.kt`

**Interfaces:**
- Consumes: time state/update methods from Task 2.
- Consumes: conversion and formatting helpers from Task 3.
- Produces: `TimeRangeFields(startedAt, endedAt, onStartedAtChange, onEndedAtChange)`.

- [ ] **Step 1: Add failing Compose tests for controls, dialogs, and card text**

Wire no-op time callbacks into existing `RoyalNoteApp` test calls, then add tests that use stable test tags:

```kotlin
@Test
fun timeRangeControlsOpenDateAndTimePickers() {
    composeRule.setContent {
        RoyalNoteTheme {
            TimeRangeFields(
                startedAt = 1_789_225_800_000L,
                endedAt = 1_789_229_400_000L,
                onStartedAtChange = {},
                onEndedAtChange = {},
            )
        }
    }

    composeRule.onNodeWithTag("startDateButton").performClick()
    composeRule.onNodeWithTag("datePicker").assertIsDisplayed()
    composeRule.onNodeWithText("取消").performClick()
    composeRule.onNodeWithTag("endTimeButton").performClick()
    composeRule.onNodeWithTag("timePicker").assertIsDisplayed()
}
```

Add a card-format test with one same-day record and one cross-day record, asserting `09:00–10:30` and `07-11 23:30–07-12 00:30` are displayed. Update the inline-edit test so editing retains the time range.

- [ ] **Step 2: Run the instrumentation test and confirm failure**

Run: `.\gradlew.bat app:compileDebugAndroidTestKotlin`

Expected: compilation fails because `TimeRangeFields` and the new callbacks do not exist.

- [ ] **Step 3: Implement the reusable two-row time field**

Create an internal composable with two rows and four outlined buttons:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeRangeFields(
    startedAt: Long,
    endedAt: Long,
    onStartedAtChange: (Long) -> Unit,
    onEndedAtChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    var dateTarget by remember { mutableStateOf<TimeTarget?>(null) }
    var timeTarget by remember { mutableStateOf<TimeTarget?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("起止时间", style = MaterialTheme.typography.labelLarge)
        TimeSelectionRow("始于", startedAt, "start", { dateTarget = TimeTarget.START }, { timeTarget = TimeTarget.START }, zoneId)
        TimeSelectionRow("终于", endedAt, "end", { dateTarget = TimeTarget.END }, { timeTarget = TimeTarget.END }, zoneId)
    }

    dateTarget?.let { target ->
        DateSelectionDialog(
            initialMillis = if (target == TimeTarget.START) startedAt else endedAt,
            zoneId = zoneId,
            onDismiss = { dateTarget = null },
            onConfirm = { selected ->
                if (target == TimeTarget.START) onStartedAtChange(selected) else onEndedAtChange(selected)
                dateTarget = null
            },
        )
    }

    timeTarget?.let { target ->
        TimeSelectionDialog(
            initialMillis = if (target == TimeTarget.START) startedAt else endedAt,
            zoneId = zoneId,
            onDismiss = { timeTarget = null },
            onConfirm = { selected ->
                if (target == TimeTarget.START) onStartedAtChange(selected) else onEndedAtChange(selected)
                timeTarget = null
            },
        )
    }
}
```

Add the row and target type in the same file:

```kotlin
private enum class TimeTarget { START, END }

@Composable
private fun TimeSelectionRow(
    label: String,
    millis: Long,
    prefix: String,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    zoneId: ZoneId,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, modifier = Modifier.width(40.dp), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(
            onClick = onDateClick,
            modifier = Modifier.weight(1f).testTag("${prefix}DateButton"),
        ) {
            Text(formatEditorDate(millis, zoneId))
        }
        OutlinedButton(
            onClick = onTimeClick,
            modifier = Modifier.width(96.dp).testTag("${prefix}TimeButton"),
        ) {
            Text(formatEditorTime(millis, zoneId))
        }
    }
}
```

- [ ] **Step 4: Implement Material 3 dialogs using official picker state APIs**

Implement both dialogs in the same file. Confirm only when a date exists; dismiss and cancel do not call the change callback:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectionDialog(
    initialMillis: Long,
    zoneId: ZoneId,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = toDatePickerMillis(initialMillis, zoneId),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let {
                        onConfirm(replaceDate(initialMillis, it, zoneId))
                    }
                },
                enabled = state.selectedDateMillis != null,
            ) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    ) {
        DatePicker(state = state, modifier = Modifier.testTag("datePicker"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSelectionDialog(
    initialMillis: Long,
    zoneId: ZoneId,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val initial = Instant.ofEpochMilli(initialMillis).atZone(zoneId)
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(replaceTime(initialMillis, state.hour, state.minute, zoneId)) },
            ) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state, modifier = Modifier.testTag("timePicker"))
            }
        },
    )
}
```

- [ ] **Step 5: Hoist callbacks through `RoyalNoteApp` and `RecordEditor`**

Add these callbacks beside existing text/mood callbacks:

```kotlin
onStartedAtChange: (Long) -> Unit,
onEndedAtChange: (Long) -> Unit,
onEditStartedAtChange: (Long) -> Unit,
onEditEndedAtChange: (Long) -> Unit,
```

Wire them from `MainActivity` to the four ViewModel methods. Pass new-form values to the top editor and edit-form values to the inline editor, falling back to the editing record's range if nullable state has not emitted yet. Place `TimeRangeFields` directly below “今日何为” and above “心绪”.

- [ ] **Step 6: Replace card metadata time with activity range**

Replace `formatTime(record.createdAt)` with:

```kotlin
formatRecordTimeRange(record.startedAt, record.endedAt, ZoneId.systemDefault())
```

Remove the obsolete private `formatTime` function. Update preview records and UI-test fixtures with named `startedAt`/`endedAt` values.

- [ ] **Step 7: Compile and run UI tests on a connected emulator**

Run: `.\gradlew.bat app:compileDebugAndroidTestKotlin`

Expected: PASS.

Run: `.\gradlew.bat connectedDebugAndroidTest`

Expected: PASS when an emulator is connected. If no device is available, record that limitation and do not claim instrumentation success.

---

### Task 5: Import Parsed Time Ranges

**Files:**
- Modify: `app/src/test/java/com/example/royalnote/ui/ImportViewModelTest.kt`
- Modify: `app/src/main/java/com/example/royalnote/network/OpenRouterModels.kt`
- Modify: `app/src/main/java/com/example/royalnote/network/OpenRouterService.kt`
- Modify: `app/src/main/java/com/example/royalnote/ui/ImportViewModel.kt`

**Interfaces:**
- Produces: `ParsedRecord.startedAt: String`, `ParsedRecord.endedAt: String`.
- Produces: deterministic `ImportViewModel(..., clock: Clock = Clock.systemDefaultZone())`.

- [ ] **Step 1: Rewrite import tests around ranges and a fixed clock**

Construct `ImportViewModel` with a fixed `Clock`. Replace old `timestamp` fixtures with explicit start/end strings and add these assertions:

```kotlin
@Test
fun importStoresRangeAndUsesImportTimeForMetadata() = runTest {
    parser.result = ParsedRecords(listOf(
        ParsedRecord("开会", "疲惫", null, "2026-06-20T10:30:00", "2026-06-20T11:45:00"),
    ))
    val viewModel = ImportViewModel(parser, repository, clock)
    viewModel.updateText("10:30 到 11:45 开会")
    viewModel.importRecords()
    advanceUntilIdle()

    val record = repository.importedRecords.single()
    assertTrue(record.endedAt > record.startedAt)
    assertEquals(clock.millis(), record.createdAt)
    assertEquals(clock.millis(), record.updatedAt)
}
```

Add a single-time fixture whose start and end strings match and assert a zero-minute record. Change the malformed-time test to assert all four time fields equal `clock.millis()`. Add an end-before-start fixture and assert it also falls back to a zero-minute range at import time.

- [ ] **Step 2: Run import tests and confirm failure**

Run: `.\gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.ui.ImportViewModelTest"`

Expected: compilation fails because the DTO and ViewModel constructor still use one timestamp.

- [ ] **Step 3: Replace the parsed DTO and model prompt**

Use this DTO:

```kotlin
@Serializable
data class ParsedRecord(
    val eventText: String,
    val moodTag: String? = null,
    val moodNote: String? = null,
    val startedAt: String,
    val endedAt: String,
)
```

Update the OpenRouter system prompt to require ISO local date-time strings for both fields. State explicitly: extract both ends of a range; duplicate a single time into both fields; use `00:00` for date-only text; use the current time for both fields when no time exists. Update the JSON example to include both keys.

- [ ] **Step 4: Parse activity time separately from import metadata time**

Inject `Clock` into `ImportViewModel`:

```kotlin
class ImportViewModel(
    private val parser: RecordParser,
    private val repository: RecordOperations,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel()
```

Capture `val importedAt = clock.millis()` once per import batch, pass it to every conversion, and use these helpers. If either string is invalid or the parsed end is earlier than the parsed start, both activity fields fall back to `importedAt`:

```kotlin
private fun ParsedRecord.toNoteRecord(importedAt: Long): NoteRecord {
    val range = parseRange(startedAt, endedAt, importedAt)
    return NoteRecord(
        eventText = eventText,
        moodTag = moodTag?.takeIf { it in MoodLabels.ALL },
        moodNote = moodNote?.takeIf { it.isNotBlank() },
        startedAt = range.first,
        endedAt = range.second,
        createdAt = importedAt,
        updatedAt = importedAt,
    )
}

private fun parseRange(start: String, end: String, fallback: Long): Pair<Long, Long> {
    val startedAt = parseTimestampOrNull(start)
    val endedAt = parseTimestampOrNull(end)
    return if (startedAt == null || endedAt == null || endedAt < startedAt) {
        fallback to fallback
    } else {
        startedAt to endedAt
    }
}

private fun parseTimestampOrNull(timestamp: String): Long? {
    return runCatching {
        LocalDateTime.parse(timestamp)
            .atZone(clock.zone)
            .toInstant()
            .toEpochMilli()
    }.recoverCatching {
        LocalDate.parse(timestamp)
            .atStartOfDay(clock.zone)
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}
```

Map the parsed list with the single batch timestamp:

```kotlin
val importedAt = clock.millis()
val records = parsed.records.map { it.toNoteRecord(importedAt) }
```

Keep `CancellationException` handling and existing user-facing network/parse messages unchanged.

- [ ] **Step 5: Run focused and full JVM tests**

Run: `.\gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.ui.ImportViewModelTest"`

Expected: PASS.

Run: `.\gradlew.bat testDebugUnitTest`

Expected: PASS.

---

### Task 6: Full Verification And Manual Acceptance

**Files:**
- Review all files listed in the File Map; only fix defects found by verification.

**Interfaces:**
- Consumes: completed data, state, formatting, UI, and import tasks.
- Produces: verified debug APK and recorded test outcomes.

- [ ] **Step 1: Run JVM tests**

Run: `.\gradlew.bat testDebugUnitTest`

Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 2: Run Android Lint**

Run: `.\gradlew.bat lintDebug`

Expected: BUILD SUCCESSFUL with no new errors.

- [ ] **Step 3: Build the debug APK**

Run: `.\gradlew.bat assembleDebug`

Expected: BUILD SUCCESSFUL and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 4: Run connected UI tests**

Check the device first with `C:\Users\zhang\AppData\Local\Android\Sdk\platform-tools\adb.exe devices`, then run `.\gradlew.bat connectedDebugAndroidTest`.

Expected: all Compose UI tests pass. If no device is connected, report UI tests as not run.

- [ ] **Step 5: Manually verify the accepted flows**

Install or launch the debug app and check:

- New form initially shows the same start/end minute.
- Same-day range saves and displays `HH:mm–HH:mm`.
- Equal start/end saves as a zero-minute record.
- Moving start after end moves end to the new start.
- Choosing an end before start shows `结束时间不可早于开始时间` and preserves input.
- Cross-day range displays both dates and appears only under the start date.
- Inline edit backfills and saves both times without altering the top draft.
- AI import handles an explicit range, a single time, and malformed time as specified.
- Restarting the app retains newly created records after the one-time destructive schema reset.

- [ ] **Step 6: Review the final diff**

Run path-limited `git status --short -- AndroidStudioProjects/RoyalNote` and `git diff -- AndroidStudioProjects/RoyalNote` from `C:\Users\zhang`.

Expected: only the planned source, test, spec, and plan files changed; no credentials, build outputs, or `.superpowers` session files are included.
