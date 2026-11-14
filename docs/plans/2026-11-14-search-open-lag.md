# Search tab open lag, 2026-11-14

Branch `fix/search-open-lag` from `main`. No ADR: ADR 0017 (index per snapshot, built on first collect) is unchanged.

## Assumptions
- The cause is a hypothesis, not profiled: main-thread library mapping when the tab first collects, plus the IME animation starting mid tab transition. Both are addressed; which one mattered more is unknown until measured.
- Fix 1: the `combine` of artists, albums and songs now runs on an injected dispatcher (`@SearchWorkDispatcher`, `Dispatchers.Default` in `AppModule`) through `flowOn`, before `debounce` and `mapLatest`. Idle/Indexing/Results behaviour is untouched. The index build stays in `CoreSearchEngineFactory`.
- The JVM test proves where the flow body is collected (the work dispatcher, not Main). It cannot prove the Room row mapping itself is off the main thread; that depends on the DAO flow collecting on the dispatcher it is given.
- Fix 2: "tab restored with its state" is read as a configuration change or process restore. Opening Search from another tab, including re-selecting it with the saved tab state, still opens the keyboard; returning from an artist or album page does not.
- The keyboard opens once per tap on the Search tab made from elsewhere. The shell raises a counter (`searchFocusRequest`, saved); the screen remembers the last value it served (saved), so a return from an artist or album page, or a restore, serves nothing, and one value is never served twice.
- "Settled" is the destination's enter transition reaching `EnterExitState.Visible`, plus one frame. If it never reports settled the keyboard still opens after `SETTLE_TIMEOUT_MS` (1 s), so a bad signal delays it and never removes it.
- The request counter is passed to the NavHost graph as state (`IntState`) and read inside the screen: the graph builder runs once and is remembered, so passing a plain `Int` froze it at 0 and the keyboard never opened (found on the phone, fixed in the third fix commit).
- Tapping Search while on an artist or album page counts as opening it from another page.
- No UI test covers fix 2 (the project has JVM tests only); it is checked on the phone below.
- Not touched: building the index before the tab opens (needs its own ADR against 0017), the engine and ranking, other screens.

## Measure it on the phone
- Compare a release build before (`main`) and after (this branch). A debug build is much janker in Compose and says little about this.
- Perfetto or Android Studio System Trace over the window from the tap on the Search tab to the keyboard being fully open.
- Look at: main-thread frames (long or missed frames during the tab animation and the IME animation), and the Default threads (the library mapping should now show there, not on main).
- Repeat each case several times; the stutter is intermittent, so one clean run proves nothing.

## Not tested
- First open after process start versus a later open. The ViewModel and its index live on after the first build (`SharingStarted.Lazily`), so the first open pays for the mapping and later ones should not; the two may behave differently, and only the first was described.

## Only the phone can verify
- The keyboard still opens on the Search tab, after the transition, not during it.
- It does not reopen after returning from an artist or album page, or after rotating the phone.
- Leaving for another tab and tapping Search again opens it once, not twice.
- Opening Search by tapping the tab while on an artist or album page.
