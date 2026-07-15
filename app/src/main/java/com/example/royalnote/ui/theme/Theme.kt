package com.example.royalnote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Bronze,
    onPrimary = BronzeOn,
    secondary = MutedCeladon,
    onSecondary = CeladonOn,
    tertiary = AntiqueGold,
    onTertiary = GoldOn,
    background = AgedPaper,
    onBackground = InkPrimary,
    surface = AgedPaperSurface,
    onSurface = InkPrimary,
    surfaceVariant = AgedPaperVariant,
    onSurfaceVariant = InkSecondary,
    surfaceTint = Bronze,
    surfaceDim = AgedPaperVariant,
    surfaceBright = AgedPaper,
    surfaceContainerLowest = AgedPaper,
    surfaceContainerLow = AgedPaperSurface,
    surfaceContainer = AgedPaperSurface,
    surfaceContainerHigh = AgedPaperSurface,
    surfaceContainerHighest = AgedPaperSurface,
    outline = PaperBorder,
    outlineVariant = PaperBorder,
    error = ErrorBrick,
)

private val DarkColorScheme = darkColorScheme(
    primary = WarmBronze,
    onPrimary = WarmBronzeOn,
    secondary = CeladonLight,
    onSecondary = CeladonLightOn,
    tertiary = AntiqueGoldDark,
    onTertiary = AntiqueGoldDarkOn,
    background = DeepInk,
    onBackground = RiceText,
    surface = DeepInkSurface,
    onSurface = RiceText,
    surfaceVariant = DeepInkVariant,
    onSurfaceVariant = RiceTextVariant,
    surfaceTint = WarmBronze,
    surfaceDim = DeepInk,
    surfaceBright = DeepInkSurface,
    surfaceContainerLowest = DeepInk,
    surfaceContainerLow = DeepInkSurface,
    surfaceContainer = DeepInkSurface,
    surfaceContainerHigh = DeepInkSurface,
    surfaceContainerHighest = DeepInkVariant,
    outline = DeepInkOutline,
    outlineVariant = DeepInkOutline,
    error = MutedTerracottaLight,
)

@Composable
fun RoyalNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
