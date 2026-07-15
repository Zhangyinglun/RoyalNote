package com.example.royalnote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.testTag as semanticsTestTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.pressBack
import com.example.royalnote.data.NoteRecord
import com.example.royalnote.settings.AnalysisModel
import com.example.royalnote.settings.AppSettings
import com.example.royalnote.settings.ReasoningEffort
import com.example.royalnote.ui.ImportScreen
import com.example.royalnote.ui.ImportUiState
import com.example.royalnote.ui.RecordTimelineUiState
import com.example.royalnote.ui.RoyalNoteNavigation
import com.example.royalnote.ui.SettingsScreen
import com.example.royalnote.ui.SettingsSupportingTextStyle
import com.example.royalnote.ui.SettingsUiState
import com.example.royalnote.ui.TimeRangeFields
import com.example.royalnote.ui.TimelineDay
import com.example.royalnote.ui.UsageUiState
import com.example.royalnote.ui.theme.AgedPaperSurface
import com.example.royalnote.ui.theme.DeepInk
import com.example.royalnote.ui.theme.PaperBorder
import com.example.royalnote.ui.theme.ErrorBrick
import com.example.royalnote.ui.theme.RoyalNoteTheme
import androidx.compose.ui.text.font.FontFamily
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoyalNoteAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun saveRecordClearsTextFieldFocusToDismissKeyboard() {
        composeRule.setContent {
            RoyalNoteTheme {
                RoyalNoteApp(
                    uiState = RecordTimelineUiState(),
                    onEventTextChange = {},
                    onMoodSelected = {},
                    onMoodNoteChange = {},
                    onStartedAtChange = {},
                    onEndedAtChange = {},
                    onSetTimeToNow = {},
                    onEditEventTextChange = {},
                    onEditMoodSelected = {},
                    onEditMoodNoteChange = {},
                    onEditStartedAtChange = {},
                    onEditEndedAtChange = {},
                    onSave = {},
                    onEdit = {},
                    onCancelEdit = {},
                    onDelete = {},
                    onMessageShown = {},
                    onImportClick = {},
                )
            }
        }

        composeRule.onNodeWithText("记录此刻，言简意赅……")
            .performClick()
            .assertIsFocused()
        composeRule.onNodeWithText("入录").performClick()
        composeRule.onNodeWithText("记录此刻，言简意赅……").assertIsNotFocused()
    }

    @Test
    fun lightThemeUsesAgedPaperForMaterialSurfaceContainers() {
        var surfaceContainerHigh = Color.Unspecified
        var surfaceContainerHighest = Color.Unspecified
        var outlineVariant = Color.Unspecified

        composeRule.setContent {
            RoyalNoteTheme(darkTheme = false) {
                surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
                surfaceContainerHighest = MaterialTheme.colorScheme.surfaceContainerHighest
                outlineVariant = MaterialTheme.colorScheme.outlineVariant
            }
        }

        assertEquals(AgedPaperSurface, surfaceContainerHigh)
        assertEquals(AgedPaperSurface, surfaceContainerHighest)
        assertEquals(PaperBorder, outlineVariant)
    }

    @Test
    fun bottomNavigationSwitchesTopLevelScreensAndHidesOnImport() {
        composeRule.setContent {
            RoyalNoteTheme {
                RoyalNoteNavigation(
                    homeContent = { onImport ->
                        Column {
                            Text("首页内容")
                            Button(onClick = onImport) { Text("测试导入") }
                        }
                    },
                    analysisContent = { Text("分析内容") },
                    settingsContent = { Text("设置内容") },
                    importContent = { onBack ->
                        Button(onClick = onBack) { Text("返回主页") }
                    },
                )
            }
        }

        composeRule.onNodeWithText("主页").assertIsDisplayed()
        composeRule.onNodeWithText("省察").performClick()
        composeRule.onNodeWithText("分析内容").assertIsDisplayed()
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("设置内容").assertIsDisplayed()
        composeRule.onNodeWithText("主页").performClick()
        composeRule.onNodeWithText("测试导入").performClick()
        composeRule.onNodeWithText("返回主页").assertIsDisplayed()
        composeRule.onNodeWithText("主页").assertDoesNotExist()
        composeRule.onNodeWithText("省察").assertDoesNotExist()
        composeRule.onNodeWithText("设置").assertDoesNotExist()
        composeRule.onNodeWithText("返回主页").performClick()
        composeRule.onNodeWithText("首页内容").assertIsDisplayed()
    }

    @Test
    fun navigationShellDoesNotAddTopInsetToRealAnalysisScreen() {
        val wrapped = mutableStateOf(false)
        composeRule.setContent {
            RoyalNoteTheme {
                if (wrapped.value) {
                    RoyalNoteNavigation(
                        homeContent = { Text("首页内容") },
                        analysisContent = { com.example.royalnote.ui.AnalysisScreen() },
                        settingsContent = { Text("设置内容") },
                        importContent = { Text("导入内容") },
                    )
                } else {
                    com.example.royalnote.ui.AnalysisScreen()
                }
            }
        }

        val standaloneTop = composeRule.onNodeWithText("七日省察")
            .getUnclippedBoundsInRoot().top.value
        composeRule.runOnUiThread { wrapped.value = true }
        composeRule.onNodeWithText("省察").performClick()
        val wrappedTop = composeRule.onAllNodesWithText("七日省察")[0]
            .getUnclippedBoundsInRoot().top.value

        assertEquals(standaloneTop, wrappedTop, 0.5f)
    }

    @Test
    fun clickingEditOpensInlineEditorAndSaveRestoresUpdatedCard() {
        val startedAt = localMillis(2026, 7, 11, 9, 0)
        val endedAt = localMillis(2026, 7, 11, 10, 30)
        val initialRecord = NoteRecord(
            id = 1,
            eventText = "读了半小时书",
            moodTag = "平静",
            moodNote = "心里安稳了一点",
            startedAt = startedAt,
            endedAt = endedAt,
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
                    onStartedAtChange = { uiState = uiState.copy(startedAt = it) },
                    onEndedAtChange = { uiState = uiState.copy(endedAt = it) },
                    onSetTimeToNow = {},
                    onEditEventTextChange = { uiState = uiState.copy(editEventText = it) },
                    onEditMoodSelected = { uiState = uiState.copy(editSelectedMood = it) },
                    onEditMoodNoteChange = { uiState = uiState.copy(editMoodNote = it) },
                    onEditStartedAtChange = { uiState = uiState.copy(editStartedAt = it) },
                    onEditEndedAtChange = { uiState = uiState.copy(editEndedAt = it) },
                    onSave = {
                        val editing = uiState.editingRecord
                        if (editing != null) {
                            val updated = editing.copy(
                                eventText = uiState.editEventText,
                                moodTag = uiState.editSelectedMood,
                                moodNote = uiState.editMoodNote.ifBlank { null },
                                startedAt = uiState.editStartedAt ?: editing.startedAt,
                                endedAt = uiState.editEndedAt ?: editing.endedAt,
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
                            editStartedAt = record.startedAt,
                            editEndedAt = record.endedAt,
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
        composeRule.onNodeWithText("09:00–10:30").assertIsDisplayed()

        val timeline = composeRule.onNodeWithTag("recordTimeline")
        timeline.performScrollToNode(hasContentDescription("更多记录操作"))
        composeRule.onNodeWithContentDescription("更多记录操作")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("修订").assertIsDisplayed().performClick()

        timeline.performScrollToNode(hasText("速录一则"))
        composeRule.onNodeWithText("速录一则").assertIsDisplayed()
        timeline.performScrollToNode(hasText("修订此则"))
        composeRule.onNodeWithText("修订此则").assertIsDisplayed()
        composeRule.onNodeWithText("改毕入录").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("读了半小时书").performTextReplacement("读了两页书摘")
        composeRule.onNodeWithText("改毕入录").performScrollTo().performClick()

        composeRule.onNodeWithText("读了两页书摘").assertIsDisplayed()
        composeRule.onNodeWithText("09:00–10:30").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("更多记录操作").assertIsDisplayed()
    }

    @Test
    fun timeRangeControlsHaveDistinctAccessibleNamesAndClickActions() {
        val zone = ZoneId.of("UTC")
        val startedAt = localMillis(zone, 2026, 7, 11, 9, 0)
        val endedAt = localMillis(zone, 2026, 7, 11, 10, 30)

        composeRule.setContent {
            RoyalNoteTheme {
                TimeRangeFields(
                    startedAt = startedAt,
                    endedAt = endedAt,
                    onStartedAtChange = {},
                    onEndedAtChange = {},
                    zoneId = zone,
                )
            }
        }

        composeRule.onNodeWithTag("startDateButton")
            .assertContentDescriptionEquals("开始日期，2026-07-11")
            .assertHasClickAction()
        composeRule.onNodeWithTag("startTimeButton")
            .assertContentDescriptionEquals("开始时间，09:00")
            .assertHasClickAction()
        composeRule.onNodeWithTag("endDateButton")
            .assertContentDescriptionEquals("结束日期，2026-07-11")
            .assertHasClickAction()
        composeRule.onNodeWithTag("endTimeButton")
            .assertContentDescriptionEquals("结束时间，10:30")
            .assertHasClickAction()
    }

    @Test
    fun setTimeToNowButtonUsesCompactVisualsAndCallsCallback() {
        val zone = ZoneId.of("UTC")
        val startedAt = localMillis(zone, 2026, 7, 11, 9, 0)
        val endedAt = localMillis(zone, 2026, 7, 11, 10, 30)
        var clicks = 0

        composeRule.setContent {
            RoyalNoteTheme {
                TimeRangeFields(
                    startedAt = startedAt,
                    endedAt = endedAt,
                    onStartedAtChange = {},
                    onEndedAtChange = {},
                    onSetToNow = { clicks += 1 },
                    zoneId = zone,
                )
            }
        }

        composeRule.onNodeWithText("起止时间").assertIsDisplayed()
        composeRule.onNodeWithTag("setTimeToNowButton")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertEquals(1, clicks)
        composeRule.onNodeWithText("已更新").assertIsDisplayed()
    }

    @Test
    fun confirmingStartDateCallsOnlyStartCallback() {
        val zone = ZoneId.of("UTC")
        val startedAt = localMillis(zone, 2026, 7, 11, 9, 0)
        val endedAt = localMillis(zone, 2026, 7, 11, 10, 30)
        var startedResult: Long? = null
        var endedResult: Long? = null

        composeRule.setContent {
            RoyalNoteTheme {
                TimeRangeFields(
                    startedAt = startedAt,
                    endedAt = endedAt,
                    onStartedAtChange = { startedResult = it },
                    onEndedAtChange = { endedResult = it },
                    zoneId = zone,
                )
            }
        }

        composeRule.onNodeWithTag("startDateButton").performClick()
        composeRule.onNodeWithTag("datePicker").assertIsDisplayed()
        composeRule.onNodeWithTag("dateConfirmButton").performClick()

        assertEquals(startedAt, startedResult)
        assertNull(endedResult)
    }

    @Test
    fun cancelAndDismissDoNotCallTimeRangeCallbacks() {
        val zone = ZoneId.of("UTC")
        val startedAt = localMillis(zone, 2026, 7, 11, 9, 0)
        val endedAt = localMillis(zone, 2026, 7, 11, 10, 30)
        var startedResult: Long? = null
        var endedResult: Long? = null

        composeRule.setContent {
            RoyalNoteTheme {
                TimeRangeFields(
                    startedAt = startedAt,
                    endedAt = endedAt,
                    onStartedAtChange = { startedResult = it },
                    onEndedAtChange = { endedResult = it },
                    zoneId = zone,
                )
            }
        }

        composeRule.onNodeWithTag("startDateButton").performClick()
        composeRule.onNodeWithTag("dateCancelButton").performClick()
        composeRule.onNodeWithTag("endTimeButton").performClick()
        composeRule.onNodeWithTag("timePicker").assertIsDisplayed()
        pressBack()
        composeRule.waitForIdle()

        assertNull(startedResult)
        assertNull(endedResult)
    }

    @Test
    fun confirmingEndTimeCallsOnlyEndCallback() {
        val zone = ZoneId.of("UTC")
        val startedAt = localMillis(zone, 2026, 7, 11, 9, 0)
        val endedAt = localMillis(zone, 2026, 7, 11, 10, 30)
        var startedResult: Long? = null
        var endedResult: Long? = null

        composeRule.setContent {
            RoyalNoteTheme {
                TimeRangeFields(
                    startedAt = startedAt,
                    endedAt = endedAt,
                    onStartedAtChange = { startedResult = it },
                    onEndedAtChange = { endedResult = it },
                    zoneId = zone,
                )
            }
        }

        composeRule.onNodeWithTag("endTimeButton").performClick()
        composeRule.onNodeWithTag("timePicker").assertIsDisplayed()
        composeRule.onNodeWithTag("timeConfirmButton").performClick()

        assertNull(startedResult)
        assertEquals(endedAt, endedResult)
    }

    @Test
    fun recordCardsDisplaySameDayAndCrossDayTimeRanges() {
        val sameDayStart = localMillis(2026, 7, 11, 9, 0)
        val sameDayEnd = localMillis(2026, 7, 11, 10, 30)
        val crossDayStart = localMillis(2026, 7, 11, 23, 30)
        val crossDayEnd = localMillis(2026, 7, 12, 0, 30)
        val records = listOf(
            NoteRecord(
                id = 1,
                eventText = "同日记录",
                moodTag = null,
                moodNote = null,
                startedAt = sameDayStart,
                endedAt = sameDayEnd,
                createdAt = sameDayStart,
                updatedAt = sameDayStart,
            ),
            NoteRecord(
                id = 2,
                eventText = "跨日记录",
                moodTag = null,
                moodNote = null,
                startedAt = crossDayStart,
                endedAt = crossDayEnd,
                createdAt = crossDayStart,
                updatedAt = crossDayStart,
            ),
        )

        composeRule.setContent {
            RoyalNoteTheme {
                RoyalNoteApp(
                    uiState = RecordTimelineUiState(
                        timelineDays = listOf(TimelineDay(label = "今日", records = records)),
                    ),
                    onEventTextChange = {},
                    onMoodSelected = {},
                    onMoodNoteChange = {},
                    onStartedAtChange = {},
                    onEndedAtChange = {},
                    onSetTimeToNow = {},
                    onEditEventTextChange = {},
                    onEditMoodSelected = {},
                    onEditMoodNoteChange = {},
                    onEditStartedAtChange = {},
                    onEditEndedAtChange = {},
                    onSave = {},
                    onEdit = {},
                    onCancelEdit = {},
                    onDelete = {},
                    onMessageShown = {},
                    onImportClick = {},
                )
            }
        }

        composeRule.onNodeWithText("09:00–10:30").assertIsDisplayed()
        composeRule.onNodeWithTag("recordTimeline")
            .performScrollToNode(hasText("07-11 23:30–07-12 00:30"))
        composeRule.onNodeWithText("07-11 23:30–07-12 00:30").assertIsDisplayed()
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
                    onStartedAtChange = {},
                    onEndedAtChange = {},
                    onSetTimeToNow = {},
                    onEditEventTextChange = {},
                    onEditMoodSelected = {},
                    onEditMoodNoteChange = {},
                    onEditStartedAtChange = {},
                    onEditEndedAtChange = {},
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

    @Test
    fun settingsScreenFiltersEffortsAndReportsSelections() {
        var state by mutableStateOf(SettingsUiState())
        composeRule.setContent {
            RoyalNoteTheme {
                SettingsScreen(
                    uiState = state,
                    onApiKeyChange = {
                        state = state.copy(settings = state.settings.copy(apiKey = it))
                    },
                    onToggleKeyVisibility = {},
                    keyVisible = false,
                    onModelSelected = {
                        state = state.copy(settings = state.settings.copy(selectedModel = it))
                    },
                    onEffortSelected = { model, effort ->
                        state = state.copy(
                            settings = state.settings.copy(
                                efforts = state.settings.efforts + (model to effort),
                            ),
                        )
                    },
                    onRefreshUsage = {},
                    onVisible = {},
                )
            }
        }

        composeRule.onNodeWithText("DeepSeek V4 Pro").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("xhigh").assertIsDisplayed()
        composeRule.onNodeWithText("max").assertDoesNotExist()
        composeRule.onNodeWithText("GPT Latest").performClick()
        composeRule.onNodeWithText("max").assertIsDisplayed()
        composeRule.onNodeWithText("关闭").assertIsDisplayed()
        composeRule.onNodeWithTag("effortSlider")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(5f)
            }
        composeRule.runOnIdle {
            assertEquals(ReasoningEffort.MAX, state.settings.effortFor(AnalysisModel.GPT_LATEST))
        }
    }

    @Test
    fun usageCardShowsMissingLoadingSuccessAndErrorStates() {
        lateinit var setUsage: (UsageUiState) -> Unit
        composeRule.setContent {
            var usage by remember { mutableStateOf<UsageUiState>(UsageUiState.MissingKey) }
            setUsage = { usage = it }
            RoyalNoteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(usage = usage),
                    keyVisible = false,
                    onApiKeyChange = {},
                    onToggleKeyVisibility = {},
                    onModelSelected = {},
                    onEffortSelected = { _, _ -> },
                    onRefreshUsage = {},
                    onVisible = {},
                )
            }
        }

        composeRule.onNodeWithText("填写 API Key 后可查询").assertIsDisplayed()
        composeRule.runOnUiThread { setUsage(UsageUiState.Loading()) }
        composeRule.onNodeWithTag("usageLoading").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("刷新本月消费").assertIsNotEnabled()
        composeRule.runOnUiThread { setUsage(UsageUiState.Success(12.34, 1L)) }
        composeRule.onNodeWithText("\$12.34").assertIsDisplayed()
        composeRule.runOnUiThread {
            setUsage(UsageUiState.Error("用量查询失败，请稍后再试", 8.0, 1L))
        }
        composeRule.onNodeWithText("\$8.00").assertIsDisplayed()
        composeRule.onNodeWithText("用量查询失败，请稍后再试").assertIsDisplayed()
    }

    @Test
    fun apiKeyIsPasswordByDefaultAndVisibilityActionIsAccessible() {
        composeRule.setContent {
            var visible by remember { mutableStateOf(false) }
            RoyalNoteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(
                        settings = AppSettings(apiKey = "sk-or-v1-secret"),
                    ),
                    keyVisible = visible,
                    onApiKeyChange = {},
                    onToggleKeyVisibility = { visible = !visible },
                    onModelSelected = {},
                    onEffortSelected = { _, _ -> },
                    onRefreshUsage = {},
                    onVisible = {},
                )
            }
        }

        composeRule.onNodeWithText("API 密钥").performClick()
        composeRule.onNodeWithContentDescription("显示 API Key")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithContentDescription("隐藏 API Key").assertIsDisplayed()
    }

    @Test
    fun settingsSupportingTextUsesSerifTypography() {
        assertEquals(FontFamily.Serif, SettingsSupportingTextStyle.fontFamily)
    }

    @Test
    fun settingsErrorUsesMutedInkFragranceColor() {
        composeRule.setContent {
            RoyalNoteTheme(darkTheme = false) {
                SettingsScreen(
                    uiState = SettingsUiState(
                        usage = UsageUiState.Error("用量查询失败，请稍后再试"),
                    ),
                    keyVisible = false,
                    onApiKeyChange = {},
                    onToggleKeyVisibility = {},
                    onModelSelected = {},
                    onEffortSelected = { _, _ -> },
                    onRefreshUsage = {},
                    onVisible = {},
                )
            }
        }

        val pixels = composeRule.onNodeWithText("用量查询失败，请稍后再试")
            .captureToImage()
            .toPixelMap()
        val containsMutedErrorColor = (0 until pixels.height).any { y ->
            (0 until pixels.width).any { x ->
                val pixel = pixels[x, y]
                kotlin.math.abs(pixel.red - ErrorBrick.red) < 0.02f &&
                    kotlin.math.abs(pixel.green - ErrorBrick.green) < 0.02f &&
                    kotlin.math.abs(pixel.blue - ErrorBrick.blue) < 0.02f &&
                    pixel.alpha > 0.8f
            }
        }

        assertTrue("Error text should use the muted ErrorBrick color", containsMutedErrorColor)
    }

    @Test
    fun usageCardKeepsStableHeightWhenErrorAppears() {
        lateinit var setUsage: (UsageUiState) -> Unit
        composeRule.setContent {
            var usage by remember { mutableStateOf<UsageUiState>(UsageUiState.MissingKey) }
            setUsage = { usage = it }
            RoyalNoteTheme {
                SettingsScreen(
                    uiState = SettingsUiState(usage = usage),
                    keyVisible = false,
                    onApiKeyChange = {},
                    onToggleKeyVisibility = {},
                    onModelSelected = {},
                    onEffortSelected = { _, _ -> },
                    onRefreshUsage = {},
                    onVisible = {},
                )
            }
        }

        val missingHeight = composeRule.onNodeWithTag("usageCard")
            .getUnclippedBoundsInRoot().height
        composeRule.runOnUiThread {
            setUsage(UsageUiState.Error("用量查询失败，请稍后再试"))
        }
        val errorHeight = composeRule.onNodeWithTag("usageCard")
            .getUnclippedBoundsInRoot().height

        assertEquals(missingHeight, errorHeight)
    }

    @Test
    fun darkThemeSettingsErrorMeetsReadableContrast() {
        composeRule.setContent {
            RoyalNoteTheme(darkTheme = true) {
                SettingsScreen(
                    uiState = SettingsUiState(
                        usage = UsageUiState.Error("用量查询失败，请稍后再试"),
                    ),
                    keyVisible = false,
                    onApiKeyChange = {},
                    onToggleKeyVisibility = {},
                    onModelSelected = {},
                    onEffortSelected = { _, _ -> },
                    onRefreshUsage = {},
                    onVisible = {},
                )
            }
        }

        fun linearChannel(value: Float): Double = if (value <= 0.04045f) {
            value.toDouble() / 12.92
        } else {
            Math.pow((value.toDouble() + 0.055) / 1.055, 2.4)
        }
        fun luminance(color: Color): Double =
            0.2126 * linearChannel(color.red) +
                0.7152 * linearChannel(color.green) +
                0.0722 * linearChannel(color.blue)
        fun contrast(first: Color, second: Color): Double {
            val firstLuminance = luminance(first)
            val secondLuminance = luminance(second)
            return (maxOf(firstLuminance, secondLuminance) + 0.05) /
                (minOf(firstLuminance, secondLuminance) + 0.05)
        }

        val pixels = composeRule.onNodeWithText("用量查询失败，请稍后再试")
            .captureToImage()
            .toPixelMap()
        val renderedColors = mutableMapOf<Int, Int>()
        (0 until pixels.height).forEach { y ->
            (0 until pixels.width).forEach { x ->
                val argb = pixels[x, y].toArgb()
                renderedColors[argb] = renderedColors.getOrDefault(argb, 0) + 1
            }
        }
        val sampledCardBackground = Color(
            renderedColors.maxBy { (_, count) -> count }.key,
        )
        val maximumRenderedContrast = (0 until pixels.height).maxOf { y ->
            (0 until pixels.width).maxOf { x ->
                contrast(pixels[x, y], sampledCardBackground)
            }
        }

        assertTrue(
            "Rendered settings background should be the Ink Fragrance background color",
            kotlin.math.abs(sampledCardBackground.red - DeepInk.red) < 0.01f &&
                kotlin.math.abs(sampledCardBackground.green - DeepInk.green) < 0.01f &&
                kotlin.math.abs(sampledCardBackground.blue - DeepInk.blue) < 0.01f,
        )
        assertTrue(
            "Dark-theme error text contrast against sampled card background was " +
                "$maximumRenderedContrast, expected at least 4.5:1",
            maximumRenderedContrast >= 4.5,
        )
    }

    @Test
    fun apiKeyFloatingLabelUsesRenderedSerifStyle() {
        composeRule.setContent {
            RoyalNoteTheme {
                Box {
                    SettingsScreen(
                        uiState = SettingsUiState(
                            settings = AppSettings(apiKey = "sk-or-v1-test"),
                        ),
                        keyVisible = false,
                        onApiKeyChange = {},
                        onToggleKeyVisibility = {},
                        onModelSelected = {},
                        onEffortSelected = { _, _ -> },
                        onRefreshUsage = {},
                        onVisible = {},
                    )
                    Text(
                        "API Key",
                        modifier = Modifier
                            .alpha(0f)
                            .clearAndSetSemantics {
                                semanticsTestTag = "serifApiKeyLabelReference"
                            },
                        style = SettingsSupportingTextStyle,
                    )
                }
            }
        }

        composeRule.onNodeWithText("API 密钥").performClick()
        val renderedWidth = composeRule.onNodeWithText("API Key", useUnmergedTree = true)
            .getUnclippedBoundsInRoot().width.value
        val serifWidth = composeRule.onNodeWithTag("serifApiKeyLabelReference")
            .getUnclippedBoundsInRoot().width.value

        assertEquals(serifWidth, renderedWidth, 0.01f)
    }

    private fun localMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    private fun localMillis(
        zoneId: ZoneId,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zoneId)
        .toInstant()
        .toEpochMilli()
}
