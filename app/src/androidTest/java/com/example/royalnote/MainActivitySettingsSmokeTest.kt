package com.example.royalnote

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySettingsSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun activityWiringPersistsObscuredApiKeyAcrossRecreation() {
        composeRule.onNode(hasText("设置") and hasClickAction()).performClick()
        val apiKeyField = composeRule.onNode(hasSetTextAction())
        apiKeyField.performTextReplacement("sk-review-smoke")
        apiKeyField.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))

        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("OpenRouter API Key").assertExists()

        composeRule.onNode(hasSetTextAction())
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.EditableText,
                    AnnotatedString("•".repeat("sk-review-smoke".length)),
                ),
            )
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        composeRule.onNodeWithContentDescription("显示 API Key").performClick()
        composeRule.onNode(hasSetTextAction())
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.EditableText,
                    AnnotatedString("sk-review-smoke"),
                ),
            )
    }

    @Test
    fun modelOptionsExposeOneSelectableGroup() {
        composeRule.onNodeWithText("设置").performClick()

        composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup) and
                hasAnyDescendant(hasText("DeepSeek V4 Pro")),
        ).assertExists()
    }
}
