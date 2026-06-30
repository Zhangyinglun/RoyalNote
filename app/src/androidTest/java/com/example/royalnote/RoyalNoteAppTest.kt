package com.example.royalnote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.royalnote.data.NoteRecord
import com.example.royalnote.ui.ImportScreen
import com.example.royalnote.ui.ImportUiState
import com.example.royalnote.ui.RecordTimelineUiState
import com.example.royalnote.ui.TimelineDay
import com.example.royalnote.ui.theme.RoyalNoteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoyalNoteAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickingEditOpensInlineEditorAndSaveRestoresUpdatedCard() {
        val initialRecord = NoteRecord(
            id = 1,
            eventText = "读了半小时书",
            moodTag = "平静",
            moodNote = "心里安稳了一点",
            createdAt = 1_787_777_700_000L,
            updatedAt = 1_787_777_700_000L,
        )

        composeRule.setContent {
            var uiState by remember {
                mutableStateOf(
                    RecordTimelineUiState(
                        timelineDays = listOf(TimelineDay(label = "今日", records = listOf(initialRecord))),
                    )
                )
            }
            RoyalNoteTheme {
                RoyalNoteApp(
                    uiState = uiState,
                    onEventTextChange = { uiState = uiState.copy(eventText = it) },
                    onMoodSelected = { uiState = uiState.copy(selectedMood = it) },
                    onMoodNoteChange = { uiState = uiState.copy(moodNote = it) },
                    onEditEventTextChange = { uiState = uiState.copy(editEventText = it) },
                    onEditMoodSelected = { uiState = uiState.copy(editSelectedMood = it) },
                    onEditMoodNoteChange = { uiState = uiState.copy(editMoodNote = it) },
                    onSave = {
                        val editing = uiState.editingRecord
                        if (editing != null) {
                            val updated = editing.copy(
                                eventText = uiState.editEventText,
                                moodTag = uiState.editSelectedMood,
                                moodNote = uiState.editMoodNote.ifBlank { null },
                                updatedAt = editing.updatedAt + 1,
                            )
                            uiState = uiState.copy(
                                editEventText = "",
                                editSelectedMood = null,
                                editMoodNote = "",
                                editingRecord = null,
                                timelineDays = listOf(TimelineDay(label = "今日", records = listOf(updated))),
                            )
                        }
                    },
                    onEdit = { record ->
                        uiState = uiState.copy(
                            editEventText = record.eventText,
                            editSelectedMood = record.moodTag,
                            editMoodNote = record.moodNote.orEmpty(),
                            editingRecord = record,
                        )
                    },
                    onCancelEdit = {
                        uiState = uiState.copy(
                            editEventText = "",
                            editSelectedMood = null,
                            editMoodNote = "",
                            editingRecord = null,
                        )
                    },
                    onDelete = {},
                    onMessageShown = {},
                    onImportClick = {},
                )
            }
        }

        composeRule.onNodeWithText("速录一则").assertIsDisplayed()
        composeRule.onNodeWithText("读了半小时书").assertIsDisplayed()

        composeRule.onNodeWithText("修订").performClick()

        composeRule.onNodeWithText("速录一则").assertIsDisplayed()
        composeRule.onNodeWithText("修订此则").assertIsDisplayed()
        composeRule.onNodeWithText("改毕入录").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("读了半小时书").performTextReplacement("读了两页书摘")
        composeRule.onNodeWithText("改毕入录").performScrollTo().performClick()

        composeRule.onNodeWithText("读了两页书摘").assertIsDisplayed()
        composeRule.onNodeWithText("修订").assertIsDisplayed()
    }

    @Test
    fun clickingImportActionInvokesCallback() {
        var importClicks = 0

        composeRule.setContent {
            RoyalNoteTheme {
                RoyalNoteApp(
                    uiState = RecordTimelineUiState(),
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
                    onImportClick = { importClicks++ },
                )
            }
        }

        composeRule.onNodeWithText("导入").assertIsDisplayed().performClick()

        assertEquals(1, importClicks)
    }

    @Test
    fun importSuccessDialogConfirmsReturn() {
        var confirmedReturns = 0

        composeRule.setContent {
            RoyalNoteTheme {
                ImportScreen(
                    uiState = ImportUiState(successDialogMessage = "已入录 3 则"),
                    onTextChange = {},
                    onImportClick = {},
                    onBack = {},
                    onMessageShown = {},
                    onSuccessConfirmed = { confirmedReturns++ },
                )
            }
        }

        composeRule.onNodeWithText("导入完毕").assertIsDisplayed()
        composeRule.onNodeWithText("已入录 3 则").assertIsDisplayed()
        composeRule.onNodeWithText("回到首页").performClick()

        assertEquals(1, confirmedReturns)
    }
}
