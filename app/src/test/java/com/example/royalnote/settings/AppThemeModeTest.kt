package com.example.royalnote.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeModeTest {
    @Test
    fun automaticFollowsSystemWhileExplicitModesOverrideIt() {
        assertTrue(AppThemeMode.AUTO.useDarkTheme(systemInDarkTheme = true))
        assertFalse(AppThemeMode.AUTO.useDarkTheme(systemInDarkTheme = false))
        assertFalse(AppThemeMode.LIGHT.useDarkTheme(systemInDarkTheme = true))
        assertTrue(AppThemeMode.DARK.useDarkTheme(systemInDarkTheme = false))
    }
}
