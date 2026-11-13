# Contributing

Solo project, written so a future feature is "add code in one place".

## Rules

- `:core:*` modules are pure Kotlin: no Android imports. `:app` talks to repositories and use cases, never to DAOs or MediaStore.
- New behaviour is a new implementation of a small interface, not an edit to a `when` over every case.
- Every tunable number (skip weights, half-life, exploration ratio, batch sizes) lives in one config object, never inline.
- The listening log (`play_event`, `search_event`) is append-only. Scores, Top 100 and Wrapped are derived and rebuildable from it.
- Room: every schema change has a migration and a migration test. Never `fallbackToDestructiveMigration`.
- No `INTERNET` permission and no network libraries.
- Versions live only in `gradle/libs.versions.toml`.

## Before you push

`./gradlew check` must pass (the pre-push hook runs it). One branch per feature; small commits.

## How to add a feature

Extension points (`DynamicListProvider`, `SignalRule`, `CandidateSource`, `SuggestionFilter`, `WrappedCard`,
`SearchMatcher`, `SoundProfiler`) arrive with phase 1 and later phases. When the first one lands, add its recipe here:
which interface to implement, where to register it (a Hilt `@IntoSet` binding), and which test to copy.
