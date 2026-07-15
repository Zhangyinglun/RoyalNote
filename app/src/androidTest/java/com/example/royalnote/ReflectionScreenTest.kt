package com.example.royalnote

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.royalnote.reflection.ReflectionCoverage
import com.example.royalnote.reflection.ReflectionBlindSpot
import com.example.royalnote.reflection.ReflectionEvidenceItem
import com.example.royalnote.reflection.ReflectionExperiment
import com.example.royalnote.reflection.ReflectionHistoryItem
import com.example.royalnote.reflection.ReflectionPeriod
import com.example.royalnote.reflection.SevenDayReflection
import com.example.royalnote.ui.AnalysisScreen
import com.example.royalnote.ui.ReflectionUiState
import com.example.royalnote.ui.theme.RoyalNoteTheme
import org.junit.Rule
import org.junit.Test

class ReflectionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reflectionScreenShowsReviewChatHistoryAndMemoryEntrances() {
        val reflection = SevenDayReflection(
            period = ReflectionPeriod("2026-07-06", "2026-07-12"),
            coverage = ReflectionCoverage(3, listOf("2026-07-08", "2026-07-10")),
            summary = listOf(ReflectionEvidenceItem("共留下三则记录", listOf(1, 2, 3))),
            blindSpots = listOf(
                ReflectionBlindSpot(
                    hypothesis = "忙碌可能延续到了晚上。",
                    question = "这是必要，还是不容易停下来？",
                    evidenceRecordIds = listOf(1, 2),
                )
            ),
            reflectionQuestion = "哪一刻最值得再看一眼？",
            experiments = listOf(
                ReflectionExperiment("one", "温和收尾", "十点半停止新任务"),
                ReflectionExperiment("two", "短暂散步", "散步十分钟"),
            ),
        )
        val state = ReflectionUiState(
            todayThreadDate = "2026-07-13",
            selectedThreadDate = "2026-07-13",
            reflection = reflection,
            history = listOf(ReflectionHistoryItem("2026-07-13", "2026-07-06", "2026-07-12", 0)),
        )
        composeRule.setContent {
            RoyalNoteTheme {
                AnalysisScreen(uiState = state)
            }
        }

        composeRule.onNodeWithText("七日省察").assertIsDisplayed()
        composeRule.onNodeWithText("七日回望").assertIsDisplayed()
        composeRule.onNodeWithText("共留下三则记录").assertIsDisplayed()
        composeRule.onNodeWithText("另一种可能").assertIsDisplayed()
        composeRule.onNodeWithTag("reflectionThread")
            .performScrollToNode(hasText("尝试计划一 · 温和收尾"))
        composeRule.onNodeWithText("尝试计划一 · 温和收尾").assertIsDisplayed()
        composeRule.onNodeWithTag("reflectionThread")
            .performScrollToNode(hasText("尝试计划二 · 短暂散步"))
        composeRule.onNodeWithText("尝试计划二 · 短暂散步").assertIsDisplayed()
        composeRule.onNodeWithTag("reflectionInput").assertIsDisplayed()

        composeRule.onNodeWithText("往日").performClick()
        composeRule.onNodeWithText("往日省察").assertIsDisplayed()
        composeRule.onNodeWithText("今日 · 7月13日").assertIsDisplayed()
        composeRule.onNodeWithText("返回").performClick()

        composeRule.onNodeWithText("记忆").performClick()
        composeRule.onNodeWithText("长期记忆").assertIsDisplayed()
        composeRule.onNodeWithText("尚无长期记忆。只有明确接受或确认的内容才会收入这里。")
            .assertIsDisplayed()
        composeRule.onNodeWithText("返回").performClick()
        composeRule.onNodeWithText("七日省察").assertIsDisplayed()
    }
}
