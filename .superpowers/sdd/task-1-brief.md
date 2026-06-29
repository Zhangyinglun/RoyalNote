### Task 1: Color.kt — 中式传统色 + 心情七色

**Files:**
- Modify: `app/src/main/java/com/example/royalnote/ui/theme/Color.kt`

**Interfaces:**
- Produces: `InkBlack`, `RicePaper`, `RicePaperSurface`, `RicePaperVariant`, `Cinnabar`, `CinnabarOn`, `Celadon`, `Ochre`, `InkOnSurface`, `InkOnSurfaceVariant`, `RicePaperOutline`（浅色）；`DeepInk`, `DeepInkSurface`, `DeepInkVariant`, `WarmCinnabar`, `WarmCinnabarOn`, `CeladonLight`, `WarmGold`, `RiceText`, `RiceTextVariant`, `DeepInkOutline`（深色）；`MoodRed`, `MoodOchre`, `MoodCeladon`, `MoodGray`, `MoodBrick`, `MoodInkBlue`, `MoodPurple`（心情七色）

- [ ] **Step 1: 重写 Color.kt**

替换 `app/src/main/java/com/example/royalnote/ui/theme/Color.kt` 全部内容：

```kotlin
package com.example.royalnote.ui.theme

import androidx.compose.ui.graphics.Color

// 浅色模式（宣纸基调）
val InkBlack = Color(0xFF1C1B1F)
val RicePaper = Color(0xFFFAF6EE)
val RicePaperSurface = Color(0xFFFFFEF7)
val RicePaperVariant = Color(0xFFF0EBE0)
val Cinnabar = Color(0xFFB91C1C)
val CinnabarOn = Color(0xFFFFFFFF)
val Celadon = Color(0xFF5F8F7A)
val Ochre = Color(0xFFC9A227)
val InkOnSurface = Color(0xFF1C1B1F)
val InkOnSurfaceVariant = Color(0xFF5C5448)
val RicePaperOutline = Color(0xFFD6CFC0)

// 深色模式（深墨基调）
val DeepInk = Color(0xFF1A1814)
val DeepInkSurface = Color(0xFF252320)
val DeepInkVariant = Color(0xFF2F2B26)
val WarmCinnabar = Color(0xFFE07A5C)
val WarmCinnabarOn = Color(0xFF1A1814)
val CeladonLight = Color(0xFF8FB9A3)
val WarmGold = Color(0xFFD4B556)
val RiceText = Color(0xFFEDE6D6)
val RiceTextVariant = Color(0xFFB8AE9A)
val DeepInkOutline = Color(0xFF3D3833)

// 心情 chip 七色（传统色映射）
val MoodRed = Color(0xFFC73E3A)       // 开心 - 朱红
val MoodOchre = Color(0xFFC9A227)     // 满足 - 赭黄
val MoodCeladon = Color(0xFF5F8F7A)   // 平静 - 青瓷
val MoodGray = Color(0xFF6B7280)      // 疲惫 - 黛灰
val MoodBrick = Color(0xFFB7654A)     // 烦躁 - 赭红
val MoodInkBlue = Color(0xFF3D5A6C)   // 低落 - 墨青
val MoodPurple = Color(0xFF8B5A6B)    // 焦虑 - 紫赭
```

- [ ] **Step 2: 编译验证**

Run: `gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/royalnote/ui/theme/Color.kt
git commit -m "feat(theme): 替换默认紫色为中式传统色 + 心情七色"
```
