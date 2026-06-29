### Task 2: Theme.kt — 关闭动态颜色 + 补全 ColorScheme

**Files:**
- Modify: `app/src/main/java/com/example/royalnote/ui/theme/Theme.kt`

**Interfaces:**
- Consumes: Task 1 的所有颜色常量
- Produces: `RoyalNoteTheme` composable（签名不变，但 dynamicColor 默认值改为 false）

- [ ] **Step 1: 重写 Theme.kt**

替换 `app/src/main/java/com/example/royalnote/ui/theme/Theme.kt` 全部内容：

```kotlin
package com.example.royalnote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Cinnabar,
    onPrimary = CinnabarOn,
    secondary = Celadon,
    onSecondary = CinnabarOn,
    tertiary = Ochre,
    onTertiary = CinnabarOn,
    background = RicePaper,
    onBackground = InkBlack,
    surface = RicePaperSurface,
    onSurface = InkOnSurface,
    surfaceVariant = RicePaperVariant,
    onSurfaceVariant = InkOnSurfaceVariant,
    outline = RicePaperOutline,
)

private val DarkColorScheme = darkColorScheme(
    primary = WarmCinnabar,
    onPrimary = WarmCinnabarOn,
    secondary = CeladonLight,
    onSecondary = WarmCinnabarOn,
    tertiary = WarmGold,
    onTertiary = WarmCinnabarOn,
    background = DeepInk,
    onBackground = RiceText,
    surface = DeepInkSurface,
    onSurface = RiceText,
    surfaceVariant = DeepInkVariant,
    onSurfaceVariant = RiceTextVariant,
    outline = DeepInkOutline,
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
```

- [ ] **Step 2: 编译验证**

Run: `gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 运行单元测试确保不破坏**

Run: `gradlew.bat test`
Expected: 所有 RecordTimelineViewModelTest 测试通过

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/royalnote/ui/theme/Theme.kt
git commit -m "feat(theme): 关闭动态颜色，启用中式传统色 ColorScheme"
```
