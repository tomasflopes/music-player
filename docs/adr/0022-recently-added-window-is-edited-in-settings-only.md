# 0022: The Recently added window is edited in Settings only

Date: 2026-11-14 · Status: accepted

**Context.** Design 7.7 and plan Task 24 put a "Last N days" chip on the Recently added screen that opened a picker (quick options and a custom number), next to the same value in Settings. After using the app the owner changes the window rarely and never in the middle of listening, so the chip and its sheet were extra controls on a screen meant for playing.

**Decision.** The list screen shows the window as a read-only caption ("Last 20 days") and nothing on it edits the value. The window is changed only from Settings, through `DaysWindowEditor` and `DaysWindowSheet`, which stay as they are. `DynamicListViewModel` no longer edits settings: it only reads `recentlyAddedDays` to show the caption and to re-emit the list. `DynamicListUiState.Ready.window` (a `DaysWindow`) becomes `windowDays` (an `Int?`).

**Consequences.** Changing the window takes a trip to Settings instead of one tap on the list; the list still updates at once when the value changes, because the provider reads the same setting. Acceptance item 5 ("changing to 15 takes one tap") no longer applies; Settings is the one way. If the chip comes back, add it as a new decision. Quick options and the range stay in `LibraryConfig` for Settings.
