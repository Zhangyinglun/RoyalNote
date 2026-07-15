package com.example.royalnote.ui.theme

import androidx.compose.ui.graphics.Color

// 浅色模式（宣纸基调）
val Bronze = Color(0xFF7A5C3E)
val BronzeOn = Color(0xFFF9F6EF)
val MutedCeladon = Color(0xFF55806A)
val CeladonOn = Color(0xFFF9F6EF)
val AntiqueGold = Color(0xFF937A46)
val GoldOn = Color(0xFFF9F6EF)
val AgedPaper = Color(0xFFF2EDE3)
val AgedPaperSurface = Color(0xFFEAE4D6)
val AgedPaperVariant = Color(0xFFE2DCC8)
val InkPrimary = Color(0xFF2A2015)
val InkSecondary = Color(0xFF63584A)
val PaperBorder = Color(0xFFD6CDBB)

// 深色模式（深墨基调）
val WarmBronze = Color(0xFFC49A6C)
val WarmBronzeOn = Color(0xFF1A1814)
val CeladonLight = Color(0xFF8FB9A3)
val CeladonLightOn = Color(0xFF1A1814)
val AntiqueGoldDark = Color(0xFFC4A65A)
val AntiqueGoldDarkOn = Color(0xFF1A1814)
val DeepInk = Color(0xFF1A1814)
val DeepInkSurface = Color(0xFF262320)
val DeepInkVariant = Color(0xFF302C27)
val RiceText = Color(0xFFEDE6D6)
val RiceTextVariant = Color(0xFFB8AE9A)
val DeepInkOutline = Color(0xFF3D3833)

// 心绪 chip 七色（古雅降饱和版）：正面取青绿冷色，负面取赭黄与红色。
val MoodPositiveGreen = Color(0xFF3F7659) // 开心 - 翡翠绿
val MoodPositiveTeal = Color(0xFF3E7270)  // 满足 - 松石青
val MoodPositiveBlue = Color(0xFF496E8A)  // 平静 - 静水蓝
val MoodNegativeOchre = Color(0xFF8D6B2C) // 疲惫 - 赭黄
val MoodNegativeOrange = Color(0xFFA14F3C) // 烦躁 - 朱橙
val MoodNegativeUmber = Color(0xFF875132)  // 低落 - 赤褐
val MoodNegativeCrimson = Color(0xFF8C3F49) // 焦虑 - 胭脂红

fun moodColor(mood: String?): Color? = when (mood) {
    "开心" -> MoodPositiveGreen
    "满足" -> MoodPositiveTeal
    "平静" -> MoodPositiveBlue
    "疲惫" -> MoodNegativeOchre
    "烦躁" -> MoodNegativeOrange
    "低落" -> MoodNegativeUmber
    "焦虑" -> MoodNegativeCrimson
    else -> null
}

val ErrorBrick = Color(0xFF8A5340)

// 深色模式错误提示：在 DeepInkSurface 上保持古雅暖陶色，同时满足正文对比度。
val MutedTerracottaLight = Color(0xFFD08B76)
