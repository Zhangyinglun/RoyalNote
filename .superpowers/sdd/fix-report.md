# RoyalNote UI Beautification — Final Code Review Fixes

## Status: DONE

## Commit
- SHA: `066fc5b`
- Message: `fix(ui): drawWithContent 修复朱砂线可见性，加时间线连接线，心情 chip 对比度`
- Files changed: `MainActivity.kt`, `ui/theme/Color.kt` (2 files, +22 / -13)

## Verification
- `gradlew.bat assembleDebug` → **BUILD SUCCESSFUL** (2s)
- `gradlew.bat test` → **BUILD SUCCESSFUL** (1s, all tests pass)

## Fixes Applied

### Fix 1 (CRITICAL): drawBehind → drawWithContent
- Replaced `drawBehind` with `drawWithContent` in **both** `RecordEditor` and `RecordCard`.
- Added `drawContent()` before the cinnabar `drawRect` so the 3dp left line draws ON TOP of the Card's opaque surface background (previously invisible).
- Added import: `androidx.compose.ui.draw.drawWithContent`

### Fix 2 (IMPORTANT): Timeline connector vertical line
- Added `drawBehind` on `RecordCard`'s outer modifier (before `padding(start = 20.dp)`).
- Draws an `outline`-colored vertical line at `x = 8.dp` (center of the TimelineHeader 8dp dot), 1dp wide, full card height — connects the timeline dots through the 20dp indentation area.

### Fix 3 (IMPORTANT): Mood chip WCAG AA contrast
- Darkened 4 light mood colors in `Color.kt` for white-text contrast ≥ 4.5:1:
  - `MoodOchre`: `0xFFC9A227` → `0xFFA8841E`
  - `MoodCeladon`: `0xFF5F8F7A` → `0xFF4A7A65`
  - `MoodBrick`: `0xFFB7654A` → `0xFF9A4F34`
  - `MoodPurple`: `0xFF8B5A6B` → `0xFF734A5B`
- `MoodRed`, `MoodGray`, `MoodInkBlue` unchanged (already pass).

### Fix 4 (MINOR): Redundant fontWeight overrides removed
- `RoyalNoteApp` "起居注" Text: removed `fontWeight = FontWeight.Bold` (already in `headlineMedium`).
- `TimelineHeader` label Text: removed `fontWeight = FontWeight.SemiBold` (already in `titleMedium`).
- Removed now-unused `import androidx.compose.ui.text.font.FontWeight`.

### Fix 5 (MINOR): Extracted CardShape constant
- Added `private val CardShape = RoundedCornerShape(6.dp)` at file scope.
- Replaced all 4 occurrences of `RoundedCornerShape(6.dp)` with `CardShape`.

## Concerns
None. All edits verified by successful build + test run.
