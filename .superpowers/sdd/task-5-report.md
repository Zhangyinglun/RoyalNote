# Task 5 Report: 记录卡片 + 时间线视觉化 + 空状态

## What I Implemented

Three composables in `app/src/main/java/com/example/royalnote/MainActivity.kt` were upgraded per the task brief:

### 1. TimelineHeader (lines 263-284)
- Wrapped in a `Row` with `Alignment.CenterVertically` and 8.dp spacing
- Added an 8.dp `Box` with `CircleShape` background filled with `MaterialTheme.colorScheme.primary` — a circle node marking each timeline day
- Kept the original `Text(label, ...)` with `titleMedium` + `FontWeight.SemiBold`

### 2. RecordCard (lines 286-357)
- Extracted `moodColorValue`, `primaryColor`, `outlineColor`, `onSurfaceVariantColor` as local vals before the `Card` (required because `drawBehind` is not a @Composable context)
- Added `padding(start = 20.dp)` for timeline indent
- Added `border(1.dp, outlineColor, RoundedCornerShape(6.dp))`
- Added `drawBehind { drawRect(primaryColor, Offset.Zero, Size(3.dp.toPx(), height)) }` — the left cinnabar line
- Set `shape = RoundedCornerShape(6.dp)`
- Time text now uses `color = onSurfaceVariantColor`
- `AssistChip` now uses `AssistChipDefaults.assistChipColors(containerColor = moodColorValue, labelColor = Color.White)` when a mood is selected; default colors when null (preserves "未输入" text)
- All preserved strings intact: "未输入", "编辑", "删除", "删除这条记录？", "删除后不能在应用内恢复。", "取消"

### 3. EmptyTimeline (lines 359-377)
- Replaced the `Card` wrapper with a `Column` (fillMaxWidth, vertical padding 24.dp, center horizontally)
- Added a decorative 40.dp x 2.dp `Box` divider in `primaryColor` above the text
- Added 12.dp `Spacer`
- Text "还没有记录。先写下此刻做了什么。" preserved, now styled with `onSurfaceVariant` color

## Compilation Result

```
.\gradlew.bat assembleDebug
BUILD SUCCESSFUL in 2s
37 actionable tasks: 5 executed, 32 up-to-date
```

## Test Result

```
.\gradlew.bat test
BUILD SUCCESSFUL in 1s
26 actionable tasks: 4 executed, 22 up-to-date
```

All unit tests pass.

## Files Changed

- `app/src/main/java/com/example/royalnote/MainActivity.kt` — 68 insertions, 6 deletions

## Commit

- `f6f1ce4` — feat(ui): 记录卡片左侧竖线+心情 chip 色彩，时间线圆点节点，空状态装饰

## Self-Review Findings

- ✅ All three composables match the brief's code verbatim
- ✅ Preserved strings confirmed present: "未输入", "编辑", "删除", "删除这条记录？", "删除后不能在应用内恢复。", "还没有记录。先写下此刻做了什么。"
- ✅ `drawBehind` color extraction pattern followed (primaryColor, outlineColor, onSurfaceVariantColor extracted to local vals)
- ✅ Only MainActivity.kt modified — theme files untouched
- ✅ Existing imports from Task 4 cover all new APIs used (Box, CircleShape, background, size, border, drawBehind, RoundedCornerShape, AssistChipDefaults, Offset, Size, Alignment)
- ✅ No new imports needed
- ✅ Build and tests green
- No concerns.
