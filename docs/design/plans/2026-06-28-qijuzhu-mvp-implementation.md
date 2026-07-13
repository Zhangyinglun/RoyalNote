# 起居注 MVP Implementation Plan


**Goal:** Build the first usable version of 起居注: create, display, edit, and delete local timeline records with optional mood data.

**Architecture:** Keep the app as a single-activity Jetpack Compose app. Add Room for the local SQLite database, a small repository around DAO operations, and a `ViewModel` that owns form state, edit state, validation, and grouped timeline data.

**Tech Stack:** Kotlin, Android Gradle Plugin 9.2.1, Jetpack Compose, Material3, Room, Kotlin Symbol Processing, JUnit.

## Global Constraints

- App name shown to the user must be `起居注`.
- First version supports only add, timeline display, edit, delete, and local SQLite persistence.
- Do not implement search, mood filtering, current status records, manual time editing, cloud sync, export, or multi-page navigation.
- Record fields are `id`, `eventText`, `moodTag`, `moodNote`, `createdAt`, and `updatedAt`.
- `eventText` is required; `moodTag` and `moodNote` are optional.
- Empty mood must be shown as `未输入` in the timeline.
- Mood labels are exactly `开心`, `满足`, `平静`, `疲惫`, `烦躁`, `低落`, `焦虑`.
- Timeline is grouped by date and sorted by `createdAt` descending.
- Editing a record must not change `createdAt`; it must update `updatedAt`.
- Do not commit unless the user explicitly asks for a commit.

---

## File Structure

- Modify `gradle/libs.versions.toml`: add Room, KSP, lifecycle ViewModel Compose, and coroutines test aliases.
- Modify `build.gradle.kts`: add the KSP plugin alias at the root.
- Modify `app/build.gradle.kts`: apply KSP and add Room/ViewModel dependencies.
- Modify `app/src/main/res/values/strings.xml`: change `app_name` to `起居注`.
- Create `app/src/main/java/com/example/royalnote/data/NoteRecord.kt`: Room entity and mood label constants.
- Create `app/src/main/java/com/example/royalnote/data/NoteRecordDao.kt`: DAO for observing, inserting, updating, and deleting records.
- Create `app/src/main/java/com/example/royalnote/data/RoyalNoteDatabase.kt`: Room database singleton.
- Create `app/src/main/java/com/example/royalnote/data/NoteRepository.kt`: repository wrapper over DAO.
- Create `app/src/main/java/com/example/royalnote/ui/RecordTimelineViewModel.kt`: form state, validation, edit mode, grouped records, and write operations.
- Create `app/src/test/java/com/example/royalnote/ui/RecordTimelineViewModelTest.kt`: JVM tests for validation, add, edit, delete, and grouping.
- Modify `app/src/main/java/com/example/royalnote/MainActivity.kt`: replace template greeting with the MVP Compose UI.

---

### Task 1: Dependencies, App Name, And Room Data Layer

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/com/example/royalnote/data/NoteRecord.kt`
- Create: `app/src/main/java/com/example/royalnote/data/NoteRecordDao.kt`
- Create: `app/src/main/java/com/example/royalnote/data/RoyalNoteDatabase.kt`
- Create: `app/src/main/java/com/example/royalnote/data/NoteRepository.kt`

**Interfaces:**
- Produces: `NoteRecord(id: Long, eventText: String, moodTag: String?, moodNote: String?, createdAt: Long, updatedAt: Long)`.
- Produces: `MoodLabels.ALL: List<String>`.
- Produces: `NoteRepository.observeRecords(): Flow<List<NoteRecord>>`.
- Produces: `NoteRepository.addRecord(eventText: String, moodTag: String?, moodNote: String?, nowMillis: Long)`.
- Produces: `NoteRepository.updateRecord(record: NoteRecord)`.
- Produces: `NoteRepository.deleteRecord(record: NoteRecord)`.

- [ ] **Step 1: Add dependency aliases**

Update `gradle/libs.versions.toml` with these entries while keeping existing aliases:

```toml
[versions]
agp = "9.2.1"
coreKtx = "1.10.1"
junit = "4.13.2"
junitVersion = "1.1.5"
espressoCore = "3.5.1"
lifecycleRuntimeKtx = "2.6.1"
activityCompose = "1.8.0"
kotlin = "2.2.10"
composeBom = "2026.02.01"
ksp = "2.2.10-2.0.2"
room = "2.8.4"
lifecycleViewmodelCompose = "2.8.7"
coroutines = "1.9.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 2: Wire KSP and dependencies**

Update root `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

Update `app/build.gradle.kts` plugins and dependencies:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

- [ ] **Step 3: Rename the app**

Update `app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">起居注</string>
</resources>
```

- [ ] **Step 4: Add the Room entity**

Create `app/src/main/java/com/example/royalnote/data/NoteRecord.kt`:

```kotlin
package com.example.royalnote.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "note_records")
data class NoteRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventText: String,
    val moodTag: String?,
    val moodNote: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

object MoodLabels {
    val ALL = listOf("开心", "满足", "平静", "疲惫", "烦躁", "低落", "焦虑")
}
```

- [ ] **Step 5: Add the DAO**

Create `app/src/main/java/com/example/royalnote/data/NoteRecordDao.kt`:

```kotlin
package com.example.royalnote.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteRecordDao {
    @Query("SELECT * FROM note_records ORDER BY createdAt DESC")
    fun observeRecords(): Flow<List<NoteRecord>>

    @Insert
    suspend fun insert(record: NoteRecord)

    @Update
    suspend fun update(record: NoteRecord)

    @Delete
    suspend fun delete(record: NoteRecord)
}
```

- [ ] **Step 6: Add the database singleton**

Create `app/src/main/java/com/example/royalnote/data/RoyalNoteDatabase.kt`:

```kotlin
package com.example.royalnote.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [NoteRecord::class], version = 1, exportSchema = true)
abstract class RoyalNoteDatabase : RoomDatabase() {
    abstract fun noteRecordDao(): NoteRecordDao

    companion object {
        @Volatile private var instance: RoyalNoteDatabase? = null

        fun getInstance(context: Context): RoyalNoteDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RoyalNoteDatabase::class.java,
                    "royal_note.db",
                ).build().also { instance = it }
            }
        }
    }
}
```

- [ ] **Step 7: Add the repository**

Create `app/src/main/java/com/example/royalnote/data/NoteRepository.kt`:

```kotlin
package com.example.royalnote.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(private val dao: NoteRecordDao) {
    fun observeRecords(): Flow<List<NoteRecord>> = dao.observeRecords()

    suspend fun addRecord(
        eventText: String,
        moodTag: String?,
        moodNote: String?,
        nowMillis: Long,
    ) {
        dao.insert(
            NoteRecord(
                eventText = eventText,
                moodTag = moodTag,
                moodNote = moodNote,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            )
        )
    }

    suspend fun updateRecord(record: NoteRecord) {
        dao.update(record)
    }

    suspend fun deleteRecord(record: NoteRecord) {
        dao.delete(record)
    }
}
```

- [ ] **Step 8: Verify code generation compiles**

Run: `./gradlew.bat app:compileDebugKotlin`

Expected: build succeeds. If KSP cannot resolve Room compiler, confirm the KSP version matches Kotlin `2.2.10`.

---

### Task 2: ViewModel State, Validation, And Timeline Grouping

**Files:**
- Create: `app/src/main/java/com/example/royalnote/ui/RecordTimelineViewModel.kt`
- Create: `app/src/test/java/com/example/royalnote/ui/RecordTimelineViewModelTest.kt`

**Interfaces:**
- Consumes: `NoteRepository` from Task 1.
- Produces: `RecordTimelineViewModel` with `uiState: StateFlow<RecordTimelineUiState>`.
- Produces: `updateEventText(String)`, `selectMood(String?)`, `updateMoodNote(String)`, `save()`, `startEditing(NoteRecord)`, `cancelEditing()`, `delete(NoteRecord)`, `clearMessage()`.
- Produces: `TimelineDay(label: String, records: List<NoteRecord>)`.

- [ ] **Step 1: Write ViewModel tests first**

Create `app/src/test/java/com/example/royalnote/ui/RecordTimelineViewModelTest.kt`:

```kotlin
package com.example.royalnote.ui

import com.example.royalnote.data.NoteRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class RecordTimelineViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeRecordRepository
    private lateinit var clock: Clock

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeRecordRepository()
        clock = Clock.fixed(Instant.parse("2026-06-28T10:15:30Z"), ZoneId.of("Asia/Shanghai"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun saveRejectsBlankEventText() = runTest {
        val viewModel = RecordTimelineViewModel(repository, clock)

        viewModel.updateEventText("   ")
        viewModel.save()
        advanceUntilIdle()

        assertEquals("先写下做了什么", viewModel.uiState.value.message)
        assertTrue(repository.records.value.isEmpty())
    }

    @Test
    fun saveAddsRecordAndClearsForm() = runTest {
        val viewModel = RecordTimelineViewModel(repository, clock)

        viewModel.updateEventText("整理书桌")
        viewModel.selectMood("平静")
        viewModel.updateMoodNote("心里安稳了一点")
        viewModel.save()
        advanceUntilIdle()

        val record = repository.records.value.single()
        assertEquals("整理书桌", record.eventText)
        assertEquals("平静", record.moodTag)
        assertEquals("心里安稳了一点", record.moodNote)
        assertEquals(clock.millis(), record.createdAt)
        assertEquals("", viewModel.uiState.value.eventText)
        assertNull(viewModel.uiState.value.selectedMood)
        assertEquals("", viewModel.uiState.value.moodNote)
    }

    @Test
    fun editUpdatesRecordWithoutChangingCreatedAt() = runTest {
        val existing = NoteRecord(1, "散步", "疲惫", null, 1000L, 1000L)
        repository.records.value = listOf(existing)
        val viewModel = RecordTimelineViewModel(repository, clock)

        viewModel.startEditing(existing)
        viewModel.updateEventText("散步二十分钟")
        viewModel.selectMood(null)
        viewModel.updateMoodNote("比出门前轻松")
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

    @Test
    fun deleteRemovesRecord() = runTest {
        val record = NoteRecord(1, "喝水", null, null, 1000L, 1000L)
        repository.records.value = listOf(record)
        val viewModel = RecordTimelineViewModel(repository, clock)

        viewModel.delete(record)
        advanceUntilIdle()

        assertTrue(repository.records.value.isEmpty())
    }

    @Test
    fun groupsRecordsByTodayYesterdayAndDate() = runTest {
        repository.records.value = listOf(
            NoteRecord(1, "今天", null, null, Instant.parse("2026-06-28T01:00:00Z").toEpochMilli(), 1L),
            NoteRecord(2, "昨天", null, null, Instant.parse("2026-06-27T01:00:00Z").toEpochMilli(), 1L),
            NoteRecord(3, "更早", null, null, Instant.parse("2026-06-24T01:00:00Z").toEpochMilli(), 1L),
        )
        val viewModel = RecordTimelineViewModel(repository, clock)
        advanceUntilIdle()

        val labels = viewModel.uiState.first().timelineDays.map { it.label }
        assertEquals(listOf("今天", "昨天", "2026-06-24"), labels)
    }
}

private class FakeRecordRepository : RecordOperations {
    val records = MutableStateFlow<List<NoteRecord>>(emptyList())
    private var nextId = 1L

    override fun observeRecords(): Flow<List<NoteRecord>> = records

    override suspend fun addRecord(eventText: String, moodTag: String?, moodNote: String?, nowMillis: Long) {
        records.value = records.value + NoteRecord(nextId++, eventText, moodTag, moodNote, nowMillis, nowMillis)
    }

    override suspend fun updateRecord(record: NoteRecord) {
        records.value = records.value.map { if (it.id == record.id) record else it }
    }

    override suspend fun deleteRecord(record: NoteRecord) {
        records.value = records.value.filterNot { it.id == record.id }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.ui.RecordTimelineViewModelTest"`

Expected: FAIL because `RecordTimelineViewModel` and `RecordOperations` do not exist yet.

- [ ] **Step 3: Implement the ViewModel**

Create `app/src/main/java/com/example/royalnote/ui/RecordTimelineViewModel.kt`:

```kotlin
package com.example.royalnote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.royalnote.data.NoteRecord
import com.example.royalnote.data.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

interface RecordOperations {
    fun observeRecords(): Flow<List<NoteRecord>>
    suspend fun addRecord(eventText: String, moodTag: String?, moodNote: String?, nowMillis: Long)
    suspend fun updateRecord(record: NoteRecord)
    suspend fun deleteRecord(record: NoteRecord)
}

data class RecordTimelineUiState(
    val eventText: String = "",
    val selectedMood: String? = null,
    val moodNote: String = "",
    val editingRecord: NoteRecord? = null,
    val timelineDays: List<TimelineDay> = emptyList(),
    val message: String? = null,
) {
    val isEditing: Boolean = editingRecord != null
}

data class TimelineDay(
    val label: String,
    val records: List<NoteRecord>,
)

class RecordTimelineViewModel(
    private val repository: RecordOperations,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val formState = MutableStateFlow(RecordTimelineUiState())

    val uiState: StateFlow<RecordTimelineUiState> = combine(
        formState,
        repository.observeRecords(),
    ) { state, records ->
        state.copy(timelineDays = records.toTimelineDays(clock))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordTimelineUiState())

    fun updateEventText(value: String) {
        formState.update { it.copy(eventText = value, message = null) }
    }

    fun selectMood(value: String?) {
        formState.update { it.copy(selectedMood = value, message = null) }
    }

    fun updateMoodNote(value: String) {
        formState.update { it.copy(moodNote = value, message = null) }
    }

    fun save() {
        val state = formState.value
        val eventText = state.eventText.trim()
        if (eventText.isEmpty()) {
            formState.update { it.copy(message = "先写下做了什么") }
            return
        }

        viewModelScope.launch {
            runCatching {
                val moodNote = state.moodNote.trim().ifEmpty { null }
                val now = clock.millis()
                val editing = state.editingRecord
                if (editing == null) {
                    repository.addRecord(eventText, state.selectedMood, moodNote, now)
                } else {
                    repository.updateRecord(
                        editing.copy(
                            eventText = eventText,
                            moodTag = state.selectedMood,
                            moodNote = moodNote,
                            updatedAt = now,
                        )
                    )
                }
            }.onSuccess {
                formState.value = RecordTimelineUiState()
            }.onFailure {
                formState.update { it.copy(message = "保存失败，请再试一次") }
            }
        }
    }

    fun startEditing(record: NoteRecord) {
        formState.value = RecordTimelineUiState(
            eventText = record.eventText,
            selectedMood = record.moodTag,
            moodNote = record.moodNote.orEmpty(),
            editingRecord = record,
        )
    }

    fun cancelEditing() {
        formState.value = RecordTimelineUiState()
    }

    fun delete(record: NoteRecord) {
        viewModelScope.launch {
            runCatching { repository.deleteRecord(record) }
                .onFailure { formState.update { state -> state.copy(message = "删除失败，请再试一次") } }
        }
    }

    fun clearMessage() {
        formState.update { it.copy(message = null) }
    }
}

class RecordTimelineViewModelFactory(
    private val repository: RecordOperations,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RecordTimelineViewModel(repository) as T
    }
}

private fun List<NoteRecord>.toTimelineDays(clock: Clock): List<TimelineDay> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(clock)
    val yesterday = today.minusDays(1)
    return groupBy { record ->
        Instant.ofEpochMilli(record.createdAt).atZone(zone).toLocalDate()
    }.map { (date, records) ->
        TimelineDay(
            label = when (date) {
                today -> "今天"
                yesterday -> "昨天"
                else -> date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            },
            records = records,
        )
    }
}
```

- [ ] **Step 4: Make the repository implement the ViewModel interface**

Update `app/src/main/java/com/example/royalnote/data/NoteRepository.kt`:

```kotlin
package com.example.royalnote.data

import com.example.royalnote.ui.RecordOperations
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val dao: NoteRecordDao) : RecordOperations {
    override fun observeRecords(): Flow<List<NoteRecord>> = dao.observeRecords()

    override suspend fun addRecord(
        eventText: String,
        moodTag: String?,
        moodNote: String?,
        nowMillis: Long,
    ) {
        dao.insert(
            NoteRecord(
                eventText = eventText,
                moodTag = moodTag,
                moodNote = moodNote,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            )
        )
    }

    override suspend fun updateRecord(record: NoteRecord) {
        dao.update(record)
    }

    override suspend fun deleteRecord(record: NoteRecord) {
        dao.delete(record)
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.ui.RecordTimelineViewModelTest"`

Expected: PASS.

---

### Task 3: Compose Home Screen

**Files:**
- Modify: `app/src/main/java/com/example/royalnote/MainActivity.kt`

**Interfaces:**
- Consumes: `RoyalNoteDatabase.getInstance(context)`, `NoteRepository`, `RecordTimelineViewModelFactory`, `RecordTimelineUiState`, `MoodLabels.ALL`.
- Produces: single-page UI with quick form, grouped timeline, edit mode, cancel, delete confirmation, validation messages.

- [ ] **Step 1: Replace the template UI**

Update `app/src/main/java/com/example/royalnote/MainActivity.kt`:

```kotlin
package com.example.royalnote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.royalnote.data.MoodLabels
import com.example.royalnote.data.NoteRecord
import com.example.royalnote.data.NoteRepository
import com.example.royalnote.data.RoyalNoteDatabase
import com.example.royalnote.ui.RecordTimelineUiState
import com.example.royalnote.ui.RecordTimelineViewModel
import com.example.royalnote.ui.RecordTimelineViewModelFactory
import com.example.royalnote.ui.TimelineDay
import com.example.royalnote.ui.theme.RoyalNoteTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RoyalNoteTheme {
                val context = LocalContext.current
                val database = remember { RoyalNoteDatabase.getInstance(context) }
                val repository = remember { NoteRepository(database.noteRecordDao()) }
                val viewModel: RecordTimelineViewModel = viewModel(
                    factory = RecordTimelineViewModelFactory(repository)
                )
                val uiState by viewModel.uiState.collectAsState()
                RoyalNoteApp(
                    uiState = uiState,
                    onEventTextChange = viewModel::updateEventText,
                    onMoodSelected = viewModel::selectMood,
                    onMoodNoteChange = viewModel::updateMoodNote,
                    onSave = viewModel::save,
                    onEdit = viewModel::startEditing,
                    onCancelEdit = viewModel::cancelEditing,
                    onDelete = viewModel::delete,
                    onMessageShown = viewModel::clearMessage,
                )
            }
        }
    }
}

@Composable
private fun RoyalNoteApp(
    uiState: RecordTimelineUiState,
    onEventTextChange: (String) -> Unit,
    onMoodSelected: (String?) -> Unit,
    onMoodNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onEdit: (NoteRecord) -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: (NoteRecord) -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        val message = uiState.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onMessageShown()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                Text("起居注", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("记下此刻做了什么，和当时的心情。", style = MaterialTheme.typography.bodyMedium)
            }
            item {
                RecordEditor(
                    uiState = uiState,
                    onEventTextChange = onEventTextChange,
                    onMoodSelected = onMoodSelected,
                    onMoodNoteChange = onMoodNoteChange,
                    onSave = onSave,
                    onCancelEdit = onCancelEdit,
                )
            }
            if (uiState.timelineDays.isEmpty()) {
                item { EmptyTimeline() }
            } else {
                uiState.timelineDays.forEach { day ->
                    item { TimelineHeader(day.label) }
                    items(day.records, key = { it.id }) { record ->
                        RecordCard(record = record, onEdit = onEdit, onDelete = onDelete)
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordEditor(
    uiState: RecordTimelineUiState,
    onEventTextChange: (String) -> Unit,
    onMoodSelected: (String?) -> Unit,
    onMoodNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (uiState.isEditing) "编辑记录" else "快速记录", style = MaterialTheme.typography.titleMedium)
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
                    FilterChip(
                        selected = uiState.selectedMood == mood,
                        onClick = { onMoodSelected(mood) },
                        label = { Text(mood) },
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
}

@Composable
private fun TimelineHeader(label: String) {
    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun RecordCard(record: NoteRecord, onEdit: (NoteRecord) -> Unit, onDelete: (NoteRecord) -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatTime(record.createdAt), style = MaterialTheme.typography.labelMedium)
            Text(record.eventText, style = MaterialTheme.typography.bodyLarge)
            AssistChip(onClick = {}, label = { Text(record.moodTag ?: "未输入") })
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
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete(record)
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun EmptyTimeline() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "还没有记录。先写下此刻做了什么。",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun formatTime(millis: Long): String {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}

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
                        label = "今天",
                        records = listOf(NoteRecord(1, "读了半小时书", "满足", null, System.currentTimeMillis(), System.currentTimeMillis()))
                    )
                )
            ),
            onEventTextChange = {},
            onMoodSelected = {},
            onMoodNoteChange = {},
            onSave = {},
            onEdit = {},
            onCancelEdit = {},
            onDelete = {},
            onMessageShown = {},
        )
    }
}
```

- [ ] **Step 2: Compile Compose UI**

Run: `./gradlew.bat app:compileDebugKotlin`

Expected: PASS. If `FlowRow` is marked experimental, keep `@OptIn(ExperimentalMaterial3Api::class)` on `RecordEditor` or move to a stable row layout.

---

### Task 4: End-To-End Verification

**Files:**
- Modify only if build or tests reveal a concrete issue.

**Interfaces:**
- Consumes all tasks.
- Produces a working debug APK that stores records locally.

- [ ] **Step 1: Run unit tests**

Run: `./gradlew.bat app:testDebugUnitTest`

Expected: PASS.

- [ ] **Step 2: Run debug build**

Run: `./gradlew.bat assembleDebug`

Expected: PASS and APK created under `app/build/outputs/apk/debug/`.

- [ ] **Step 3: Optional device smoke test if a device is attached**

Run: `adb devices`

Expected: at least one attached device or emulator. If none is attached, skip this step and report that device verification was not run.

If a device is attached, run: `./gradlew.bat installDebug`

Manual checks:

- Launch 起居注.
- Add a record with only “做了什么”; verify mood shows `未输入`.
- Add a record with mood `平静` and a mood note.
- Tap a record, edit text and mood note, save, and verify it stays under the same date group.
- Delete a record and confirm the dialog appears before removal.
- Force close and reopen the app; verify records remain.

---

## Self-Review

- Spec coverage: add, timeline display, edit, delete, optional mood, local SQLite persistence, app name, non-goals, validation, and verification are covered.
- Placeholder scan: no `TBD`, `TODO`, `implement later`, or unspecified edge handling remains.
- Type consistency: `NoteRecord`, `MoodLabels`, `RecordOperations`, `RecordTimelineUiState`, `TimelineDay`, and `RecordTimelineViewModel` names are consistent across tasks.
- Commit policy: plan does not include commit steps because commits require explicit user approval in this workspace.
