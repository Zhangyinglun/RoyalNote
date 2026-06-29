# RoyalNote Agent Notes

## Project Shape

- Single-module Android app: Gradle includes only `:app`; app id/namespace is `com.example.royalnote`.
- Toolchain is AGP `9.2.1`, Gradle wrapper `9.4.1`, Kotlin `2.2.10`, KSP `2.2.10-2.0.2`, Compose BOM `2026.02.01`.
- Android config: `compileSdk` is Android `36.1`, `minSdk = 33`, `targetSdk = 36`.
- Dependencies come from `gradle/libs.versions.toml`; Room uses KSP, not kapt. `settings.gradle.kts` sets `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, so add repositories there, not in module builds.
- `gradle.properties` enables configuration cache and has `android.disallowKotlinSourceSets=false`, which currently prints an experimental-option warning during Gradle configuration.

## Commands

- On Windows, prefer `.\gradlew.bat ...` from `C:\Users\zhang\AndroidStudioProjects\RoyalNote`.
- Build debug APK: `.\gradlew.bat assembleDebug`.
- Run local JVM tests: `.\gradlew.bat testDebugUnitTest`.
- Run one JVM test method: `.\gradlew.bat app:testDebugUnitTest --tests "com.example.royalnote.ui.RecordTimelineViewModelTest.saveAddsRecordAndClearsForm"`.
- Run lint for the debug variant: `.\gradlew.bat lintDebug`.
- Run instrumentation/UI tests only with a connected device or emulator: `.\gradlew.bat connectedDebugAndroidTest`.
- Install the debug app to the connected device: `.\gradlew.bat installDebug`.

## App Architecture

- `MainActivity.kt` is the real UI entrypoint and currently contains the single-page Compose UI; there is no Navigation setup.
- App wiring is manual in `MainActivity`: `RoyalNoteDatabase.getInstance(context)` -> `NoteRepository` -> `RecordTimelineViewModelFactory`.
- Data layer is Room-backed local SQLite: database file `royal_note.db`, entity table `note_records`, schema export disabled.
- `RecordTimelineViewModel` owns form state, edit state, validation, snackbar messages, and date grouping. It depends on `RecordOperations`, which keeps JVM tests independent from Room.
- MVP scope in docs and code is add/display/edit/delete local records only. Do not add search, mood filtering, manual time editing, cloud sync, export, or multi-page navigation unless the user asks.

## Design System — 墨香 (Ink Fragrance)

**Design philosophy:** 古朴 (antique), 低调奢华 (understated luxury). Like an imperial scribe's desk — aged xuan paper, dark ink, muted bronze accents. Nothing shouts; everything whispers quality.

### Color Palette

| Role | Light | Dark |
|------|-------|------|
| Primary (accent) | `#7A5C3E` Bronze | `#C49A6C` WarmBronze |
| Secondary | `#55806A` MutedCeladon | `#8FB9A3` CeladonLight |
| Tertiary | `#937A46` AntiqueGold | `#C4A65A` AntiqueGoldDark |
| Background | `#F2EDE3` AgedPaper | `#1A1814` DeepInk |
| Surface | `#EAE4D6` AgedPaperSurface | `#262320` DeepInkSurface |
| SurfaceVariant | `#E2DCC8` AgedPaperVariant | `#302C27` DeepInkVariant |
| OnPrimary/OnSecondary/OnTertiary | `#F9F6EF` | `#1A1814` |
| OnBackground/OnSurface | `#2A2015` InkPrimary | `#EDE6D6` RiceText |
| OnSurfaceVariant | `#63584A` InkSecondary | `#B8AE9A` RiceTextVariant |
| Outline | `#D6CDBB` PaperBorder | `#3D3833` DeepInkOutline |

### Mood Chip Colors (古雅降饱和版)

| Mood | Hex | Name |
|------|-----|------|
| 开心 | `#B6624A` | 暖朱 |
| 满足 | `#927530` | 赭金 |
| 平静 | `#4B7662` | 青瓷 |
| 疲惫 | `#6B7080` | 黛灰 |
| 烦躁 | `#8A5340` | 赭褐 |
| 低落 | `#425663` | 墨青 |
| 焦虑 | `#6B4A5A` | 紫赭 |

### Typography

- All text uses `FontFamily.Serif` (system serif, maps to Noto Serif CJK on Chinese devices).
- Headline: 28sp Bold. Title: 18sp SemiBold. Body: 16sp/14sp Normal. Label: 14sp/12sp Medium.
- Wide letter spacing (0.03–0.08em) for an unhurried, classical rhythm.

### Layout & Visual Rules

- Card corner radius: `8.dp`.
- Title underline: 40dp × 2dp line + 3dp dot, both in primary color at reduced alpha (0.7/0.5).
- Card left accent bar: 3dp wide, primary color at alpha 0.6 (record) / 0.7 (editor).
- Record cards: vertical connecting line (1dp outline) between cards; actions row separated by 1dp divider line at outline alpha 0.4.
- Mood chips: solid background color + white text on record cards; outlined → filled toggle in editor.
- Empty timeline: centered underline (40dp, alpha 0.5) + muted text.

### Color Source Files

- `Color.kt` — All color constants.
- `Theme.kt` — Light/Dark `ColorScheme` mapping.
- `Type.kt` — `Typography` definition.

When adding new UI elements, follow this palette; do not introduce bright or saturated colors. The primary accent is bronze, not red. Gold is tertiary — use sparingly as decoration, never as a dominant action color.

## Behavior To Preserve

- User-facing app name/title is `起居注`.
- Mood labels are exactly `开心`, `满足`, `平静`, `疲惫`, `烦躁`, `低落`, `焦虑`; missing mood displays as `未输入`.
- Empty `eventText` must not save and shows `先写下做了什么`.
- Editing preserves `createdAt` and updates `updatedAt`.
- Timeline records are queried by `createdAt DESC`, grouped into `今天`, `昨天`, or ISO date like `2026-06-24`.
- UI tests assert exact Chinese text nodes, including two `未输入` nodes in the sample screen; keep those labels stable unless updating tests too.
- Current visual direction is `墨香` (Ink Fragrance): antique, understated luxury, muted-warm palette. `dynamicColor = false`, Serif typography, Material3 + Compose foundation only. See Design System section for full spec.

## Device / ADB Notes

- In this Windows environment, `adb` may not be on PATH. Use `C:\Users\zhang\AppData\Local\Android\Sdk\platform-tools\adb.exe` before assuming no device exists.
- Known emulator example: `emulator-5554`, displayed as `Medium_Phone_API_36.1(AVD)`.
- Check devices: `& "C:\Users\zhang\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices`.
- Cold-start app: `& "C:\Users\zhang\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 shell am start -W -S -n com.example.royalnote/.MainActivity`.
- Check focused window: `& "C:\Users\zhang\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 shell dumpsys window | findstr mCurrentFocus`.
- Crash buffer: `& "C:\Users\zhang\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 logcat -b crash -d`.

## Git Gotcha

- The Git root is `C:\Users\zhang`, not the project directory. Use path-limited status/diff commands such as `git status --short -- AndroidStudioProjects/RoyalNote/AGENTS.md` to avoid scanning unrelated user-profile files.
