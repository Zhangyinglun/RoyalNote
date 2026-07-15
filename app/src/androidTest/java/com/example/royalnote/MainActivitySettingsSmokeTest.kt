package com.example.royalnote

import android.content.Context
import android.content.SharedPreferences
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
import androidx.test.platform.app.InstrumentationRegistry
import com.example.royalnote.network.MonthlyUsage
import com.example.royalnote.network.OpenRouterUsageProvider
import com.example.royalnote.settings.SettingsRepository
import com.example.royalnote.settings.SettingsStorage
import com.example.royalnote.settings.SharedPreferencesSettingsStorage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySettingsSmokeTest {
    private val usage = SmokeFakeUsageProvider()
    private val storage = SmokeSettingsStorage()
    private val dependencyRule = MainActivityDependencyRule(storage, usage)
    private val composeRule = createAndroidComposeRule<MainActivity>()
    private lateinit var preferences: SharedPreferences
    private var savedPreferences: Map<String, *> = emptyMap<String, Any?>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(dependencyRule).around(composeRule)

    @Before
    fun isolateRealPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        preferences = context.getSharedPreferences(
            SharedPreferencesSettingsStorage.FILE_NAME,
            Context.MODE_PRIVATE,
        )
        savedPreferences = preferences.all.toMap()
        preferences.edit().clear().commit()
    }

    @After
    fun restoreRealPreferences() {
        preferences.edit().clear().applyValues(savedPreferences).commit()
    }

    @Test
    fun activityWiringPersistsObscuredApiKeyAcrossRecreation() {
        composeRule.onNode(hasText("设置") and hasClickAction()).performClick()
        composeRule.onNodeWithText("API 密钥").performClick()
        val apiKeyField = composeRule.onNode(hasSetTextAction())
        apiKeyField.performTextReplacement("sk-review-smoke")
        apiKeyField.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))

        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("API 密钥").assertExists().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { usage.calls == 1 }
        assertEquals("sk-review-smoke", usage.keys.single())
        composeRule.onNodeWithText("\$7.89").assertExists()

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
        composeRule.onNodeWithText("DeepSeek V4 Pro").performClick()

        composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup) and
                hasAnyDescendant(hasText("DeepSeek V4 Pro")),
        ).assertExists()
    }
}

private class MainActivityDependencyRule(
    private val storage: SettingsStorage,
    private val usage: OpenRouterUsageProvider,
) : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val override = MainActivityDependencyOverrides.installForTest(
                MainActivitySettingsDependencies(SettingsRepository(storage), usage),
            )
            try {
                base.evaluate()
            } finally {
                override.close()
            }
        }
    }
}

private class SmokeSettingsStorage : SettingsStorage {
    private val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) {
        values[key] = value
    }
}

private class SmokeFakeUsageProvider : OpenRouterUsageProvider {
    val keys = mutableListOf<String>()
    val calls: Int get() = keys.size
    override suspend fun monthlyUsage(apiKey: String): MonthlyUsage {
        keys += apiKey
        return MonthlyUsage(7.89)
    }
}

private fun SharedPreferences.Editor.applyValues(values: Map<String, *>): SharedPreferences.Editor {
    values.forEach { (key, value) ->
        when (value) {
            is String -> putString(key, value)
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(key, value as Set<String>)
        }
    }
    return this
}
