package com.example.royalnote.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodColorTest {
    @Test
    fun positiveMoodsUseCoolColorsAndNegativeMoodsUseWarmColors() {
        assertEquals(MoodPositiveGreen, moodColor("开心"))
        assertEquals(MoodPositiveTeal, moodColor("满足"))
        assertEquals(MoodPositiveBlue, moodColor("平静"))
        assertEquals(MoodNegativeOchre, moodColor("疲惫"))
        assertEquals(MoodNegativeOrange, moodColor("烦躁"))
        assertEquals(MoodNegativeUmber, moodColor("低落"))
        assertEquals(MoodNegativeCrimson, moodColor("焦虑"))
        assertNull(moodColor(null))
        assertNull(moodColor("未输入"))
    }

    @Test
    fun everyMoodChipMeetsNormalTextContrastWithWhite() {
        val chipColors = listOf(
            MoodPositiveGreen,
            MoodPositiveTeal,
            MoodPositiveBlue,
            MoodNegativeOchre,
            MoodNegativeOrange,
            MoodNegativeUmber,
            MoodNegativeCrimson,
        )

        chipColors.forEach { background ->
            assertTrue(
                "Expected at least 4.5:1 contrast for $background",
                contrastRatio(Color.White, background) >= 4.5f,
            )
        }
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
