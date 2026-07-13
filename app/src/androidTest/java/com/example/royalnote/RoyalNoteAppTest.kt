package com.example.royalnote

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag as semanticsTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.royalnote.data.NoteRecord
import com.example.royalnote.settings.AnalysisModel
import com.example.royalnote.settings.AppSettings
import com.example.royalnote.ui.ImportScreen
import com.example.royalnote.ui.ImportUiState
import com.example.royalnote.ui.RecordTimelineUiState
import com.example.royalnote.ui.SettingsScreen
import com.example.royalnote.ui.SettingsSupportingTextStyle
import com.example.royalnote.ui.SettingsUiState
import com.example.royalnote.ui.TimelineDay
import com.example.royalnote.ui.UsageUiState
import com.example.royalnote.ui.theme.MoodBrick
import com.example.royalnote.ui.theme.DeepInkSurface
import com.example.royalnote.ui.theme.RoyalNoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

        composeRule.onNodeWithText("DeepSeek V4 Pro").assertIsDisplayed()
        composeRule.onNodeWithText("xhigh").assertIsDisplayed()
        composeRule.onNodeWithText("max").assertDoesNotExist()
        composeRule.onNodeWithText("GPT Latest").performClick()
        composeRule.onNodeWithText("max").assertIsDisplayed()
        composeRule.onNodeWithText("none").assertIsDisplayed()
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
            RoyalNoteTheme {
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
                kotlin.math.abs(pixel.red - MoodBrick.red) < 0.02f &&
                    kotlin.math.abs(pixel.green - MoodBrick.green) < 0.02f &&
                    kotlin.math.abs(pixel.blue - MoodBrick.blue) < 0.02f &&
                    pixel.alpha > 0.8f
            }
        }

        assertTrue("Error text should use the muted MoodBrick color", containsMutedErrorColor)
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
            "Rendered card background should be the Ink Fragrance surface color",
            kotlin.math.abs(sampledCardBackground.red - DeepInkSurface.red) < 0.01f &&
                kotlin.math.abs(sampledCardBackground.green - DeepInkSurface.green) < 0.01f &&
                kotlin.math.abs(sampledCardBackground.blue - DeepInkSurface.blue) < 0.01f,
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

        val renderedWidth = composeRule.onNodeWithText("API Key", useUnmergedTree = true)
            .getUnclippedBoundsInRoot().width.value
        val serifWidth = composeRule.onNodeWithTag("serifApiKeyLabelReference")
            .getUnclippedBoundsInRoot().width.value

        assertEquals(serifWidth, renderedWidth, 0.01f)
    }
}
