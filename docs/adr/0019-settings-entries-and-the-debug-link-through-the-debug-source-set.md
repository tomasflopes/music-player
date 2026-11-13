# 0019: Settings entries are a Hilt set, and the debug link is added from the debug source set

Date: 2026-11-13 · Status: accepted

**Context.** Plan Task 30 asks for a link to `DebugScreen` in Settings, in debug builds only, and Task 22 asks to remove the debug launcher icon. `DebugActivity` and `DebugScreen` live in `app/src/debug` and call `:data`, which `ui` must not import (ADR 0016), so Settings in `app/src/main` cannot name them. A runtime flag (`BuildConfig.DEBUG`) would still compile the link into release, and the release source set must stay free of `DebugScreen` references.

**Decision.** `SettingsEntry` (id, order, `@Composable Content()`) is a new extension point, mirroring `HomeSection`: `SettingsViewModel` takes `Set<SettingsEntry>`, sorts it by order then id and draws each member below the built-in rows. `ExtensionPointsModule` declares the set with `@Multibinds`, so it is empty in release. `app/src/debug` has a `DebugModule` that adds `DebugSettingsEntry` with `@IntoSet`; its row starts `DebugActivity`, whose manifest entry is no longer exported and has no launcher filter. Settings itself is one plain-string route (`settings`, ADR 0016) opened from a gear icon on Home; it is not a tab and `TopLevelDestination` does not change.

**Consequences.** Release builds contain no debug class, row or manifest entry. Any later Settings section (analysis import, phase 3) is one more `@IntoSet` line. The debug source set now depends on `SettingsEntry` and `SettingRow` from `main`, which is the allowed direction. The debug screen is reachable only through Settings, so a broken Settings screen also hides it; Android Studio can still start `DebugActivity` directly.
