# Return To Today FAB Design

## Goal

Add a bottom-right floating action button that lets the user return to today's timeline position after scrolling down.

## Behavior

- The button is hidden while the list is at the top.
- The button appears only after the timeline has been scrolled down.
- Tapping the button smoothly scrolls the main list back to the top, where the editor and today's records are located.
- Empty timelines do not need a special case; the list will not be meaningfully scrollable, so the button remains hidden.

## UI

- Place the control in the bottom-right `floatingActionButton` slot of the existing `Scaffold`.
- Use Material 3 `FloatingActionButton` styling with the app's bronze primary color and serif label styling inherited from the theme.
- Keep the label short: `今日`.
- Avoid new bright colors or extra visual language; the button should feel like part of the current Ink Fragrance theme.

## Implementation Shape

- Add a `LazyListState` to the existing `LazyColumn`.
- Derive visibility from scroll state, using `firstVisibleItemIndex > 0` or a positive scroll offset.
- Launch `animateScrollToItem(0)` when the user taps the button.
- Keep the change inside `MainActivity.kt`; no ViewModel or data-layer changes are needed.

## Testing

- Add or update a Compose UI test that renders enough timeline records to scroll.
- Verify the button is not visible initially.
- Scroll down and verify `今日` becomes visible.
- Click it and verify the title/editor area is visible again.

## Scope

- No manual date navigation.
- No new navigation destination.
- No changes to record grouping, sorting, saving, editing, or deletion.
