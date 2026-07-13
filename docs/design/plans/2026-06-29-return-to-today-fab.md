# Return To Today FAB Implementation Plan


**Goal:** Add a bottom-right floating action button that appears after scrolling and returns the user to today's timeline position.

**Architecture:** Keep the behavior local to `RoyalNoteApp` by adding `LazyListState` to the existing `LazyColumn`. The button lives in the existing `Scaffold` `floatingActionButton` slot and scrolls the list back to item `0` with `animateScrollToItem(0)`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose UI tests, existing Android app module.

## Global Constraints

- Project path: `C:\Users\zhang\AndroidStudioProjects\RoyalNote`.
- Single-module Android app: Gradle includes only `:app`.
- Android config: `compileSdk` is Android `36.1`, `minSdk = 33`, `targetSdk = 36`.
- Keep the current `墨香` visual direction: muted bronze primary, no bright or saturated colors, Serif typography.
- User-facing app name/title remains `起居注`.
- Mood labels and existing Chinese UI text remain stable.
- Do not add navigation, search, filters, cloud sync, export, or manual date controls.
- Do not create git commits unless the user explicitly asks.

---

## File Structure

- Modify `app/src/main/java/com/example/royalnote/MainActivity.kt`: add scroll state, scroll coroutine, a stable test tag on the list, FAB visibility, and the FAB UI.
- Modify `app/src/androidTest/java/com/example/royalnote/ui/RoyalNoteAppTest.kt`: add a scroll behavior test with enough records to make the timeline scrollable.

### Task 1: Add Compose UI Test Coverage

**Files:**
- Modify: `app/src/androidTest/java/com/example/royalnote/ui/RoyalNoteAppTest.kt`

**Interfaces:**
- Consumes: `RoyalNoteApp(uiState, callbacks...)` and `RecordTimelineUiState(timelineDays = ...)`.
- Produces: A failing UI test named `showsReturnToTodayButtonAfterScrollAndReturnsToTop`.

- [ ] **Step 1: Add imports for content description lookup and lazy list scrolling**

Add these imports to `RoyalNoteAppTest.kt`:

```kotlin
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import org.junit.Assert.assertEquals
```

- [ ] **Step 2: Add the failing test**

Add this test method below `showsEditorAndTimelineRecord()`:

```kotlin
@Test
fun showsReturnToTodayButtonAfterScrollAndReturnsToTop() {
    val records = (1..30).map { index ->
        NoteRecord(
            id = index.toLong(),
            eventText = "第${index}则",
            moodTag = null,
            moodNote = null,
            createdAt = 1_787_777_700_000L - index,
            updatedAt = 1_787_777_700_000L - index,
        )
    }

    composeRule.setContent {
        RoyalNoteTheme {
            RoyalNoteApp(
                uiState = RecordTimelineUiState(
                    timelineDays = listOf(
                        TimelineDay(label = "今日", records = records),
                    ),
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

    assertEquals(
        0,
        composeRule.onAllNodesWithContentDescription("回到今天").fetchSemanticsNodes().size,
    )

    composeRule.onNodeWithTag("timelineList").performScrollToIndex(32)

    composeRule.onNodeWithContentDescription("回到今天").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("回到今天").performClick()

    composeRule.waitForIdle()
    composeRule.onNodeWithText("起居注").assertIsDisplayed()
}
```

- [ ] **Step 3: Run the test and verify it fails**

Run: `./gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.royalnote.ui.RoyalNoteAppTest#showsReturnToTodayButtonAfterScrollAndReturnsToTop"`

Expected: FAIL because no node has content description `回到今天`.

### Task 2: Implement The Return-To-Today FAB

**Files:**
- Modify: `app/src/main/java/com/example/royalnote/MainActivity.kt`

**Interfaces:**
- Consumes: the existing `Scaffold` and `LazyColumn` inside `RoyalNoteApp`.
- Produces: A bottom-right FAB with `contentDescription = "回到今天"` and visible text `今日`.

- [ ] **Step 1: Add Compose imports**

Add these imports to `MainActivity.kt`:

```kotlin
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
```

- [ ] **Step 2: Add list state and visibility state**

Inside `RoyalNoteApp`, after `val snackbarHostState = remember { SnackbarHostState() }`, add:

```kotlin
val listState = rememberLazyListState()
val coroutineScope = rememberCoroutineScope()
val showReturnToToday by remember {
    derivedStateOf {
        listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
    }
}
```

- [ ] **Step 3: Add the FAB to `Scaffold`**

Change the `Scaffold` call to include `floatingActionButton`:

```kotlin
Scaffold(
    modifier = Modifier.fillMaxSize(),
    snackbarHost = { SnackbarHost(snackbarHostState) },
    floatingActionButton = {
        if (showReturnToToday) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.semantics {
                    contentDescription = "回到今天"
                },
            ) {
                Text("今日")
            }
        }
    },
) { innerPadding ->
```

- [ ] **Step 4: Attach the state to `LazyColumn`**

Change the `LazyColumn` call to pass the state:

```kotlin
LazyColumn(
    state = listState,
    modifier = Modifier
        .testTag("timelineList")
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
```

- [ ] **Step 5: Run validation**

Run: `./gradlew.bat connectedDebugAndroidTest`

Expected: PASS for `RoyalNoteAppTest`.

Run: `./gradlew.bat testDebugUnitTest`

Expected: PASS for local JVM tests.

Run: `./gradlew.bat assembleDebug`

Expected: BUILD SUCCESSFUL.

## Self-Review

- Spec coverage: The plan covers hidden-at-top behavior, visible-after-scroll behavior, bottom-right placement, smooth return to item `0`, and no ViewModel/data changes.
- Placeholder scan: No placeholders remain.
- Type consistency: The test uses `onNodeWithTag("timelineList")` to scroll the list and `onNodeWithContentDescription("回到今天")` to find the button; the implementation sets both values.
