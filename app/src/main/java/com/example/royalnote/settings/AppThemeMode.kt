package com.example.royalnote.settings

enum class AppThemeMode(
    val storageValue: String,
    val displayName: String,
) {
    AUTO("auto", "自动"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    fun useDarkTheme(systemInDarkTheme: Boolean): Boolean = when (this) {
        AUTO -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStorageValue(value: String?): AppThemeMode? =
            entries.firstOrNull { it.storageValue == value }
    }
}
