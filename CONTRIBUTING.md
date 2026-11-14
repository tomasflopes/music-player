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

Every extension point is a small interface. A new behaviour is a new implementation, one binding line and a test
that extends the interface's contract. Home, Settings and the queue read the Hilt sets and never name a member, so
nothing else changes. A decision behind a new extension point still gets an ADR (see `docs/adr/INDEX.md`).

### Add a dynamic list (Top 100, Discover, Rewind)

- **Interface:** `DynamicListProvider` in `:core:model` (`core/model/.../lists/`): `id`, `title`, `order`,
  `playSource`, `songs()`.
- **Implement** it in `:core:taste/lists/`, pure Kotlin and without annotations, taking ports in its constructor
  (see `RecentlyAddedProvider`). `playSource` is what plays started from the list are logged as.
- **Binding line:** in `ExtensionPointsModule` (`:app`):
  `@Binds @IntoSet abstract fun topHundred(provider: TopHundredProvider): DynamicListProvider`. Core classes carry
  no annotations, so its construction is one `@Provides` in `RepositoryModule` (`:data`), beside
  `recentlyAddedProvider`.
- **Test:** a class extending `DynamicListProviderContract` (`core/taste/src/test/.../lists/`) that implements
  `provider()`; see `RecentlyAddedProviderTest`. The "Last N days" caption appears only for the list whose id is
  `RecentlyAddedProvider.ID`; the number itself is edited in Settings.

### Add a search field or tier

- **Interface:** `SearchMatcher` in `:core:search`, one rule per `SearchField`; the tiers are `MatchTier`.
- **New field:** add an entry to `SearchField` (its position is the tie-break order) and put its values in the
  documents `SearchIndex` builds. `FieldMatcher.defaults()` already makes one matcher per entry.
- **New tier or smarter rule:** add a `MatchTier` entry with its rank and return it from a `SearchMatcher`. Read
  ADR 0012 first: the tiers decide what is shown under "See all".
- **Binding line:** `SearchIndex()` in `CoreSearchEngineFactory.build` (`:app`, `ui/search/SearchEngine.kt`)
  becomes `SearchIndex(matchers = FieldMatcher.defaults() + MyMatcher())`. A matcher for a field that already has
  one replaces the default, because the last one listed wins.
- **Test:** add cases to `SearcherTest` (`core/search/src/test/...`). There is no abstract contract test for
  `SearchMatcher` yet; write one if a second custom matcher appears.

### Add a Home section

- **Interface:** `HomeSection` in `:app` (`ui/home/HomeSection.kt`): `id`, `order` and
  `@Composable Content(onNavigate)`. Shuffle all is order 0, the dynamic lists row is 10.
- **Implement** a class with `@Inject constructor()` in `ui/home/sections/`, with its own `@HiltViewModel` (see
  `ShuffleAllSection`, `DynamicListsSection`).
- **Binding line:** in `ExtensionPointsModule`:
  `@Binds @IntoSet abstract fun mySection(section: MySection): HomeSection`.
- **Test:** there is no contract test for `HomeSection` (it is a composable). Test its ViewModel the way
  `DynamicListsViewModelTest` does; the Home flow is covered on the emulator by `OnboardingFlowTest`.

### Add a Settings entry

- **Interface:** `SettingsEntry` in `:app` (`ui/settings/SettingsEntry.kt`): `id`, `order` and
  `@Composable Content()`, drawn below the built-in rows (ADR 0019). Use `SettingRow` for a row that looks like
  the others.
- **Implement** a class with `@Inject constructor()`; keep its state in its own ViewModel.
- **Binding line:** `@Binds @IntoSet abstract fun myEntry(entry: MyEntry): SettingsEntry` in `ExtensionPointsModule`
  for every build, or in a module under `app/src/debug` for a debug-only entry (see `DebugModule`; `main` must
  not name debug classes).
- **Test:** no contract test exists. `SettingsViewModelTest` shows how entries are ordered (`FakeSettingsEntry`);
  test your entry's own ViewModel beside it.

### Swap the shuffle strategy

- **Interface:** `ShuffleStrategy` in `:core:taste` (`shuffle/`): `order(items, random)`, which must not change
  `items`. Keep `PlainShuffle` when adding another, so both can be compared.
- **Binding line:** `fun shuffleStrategy(): ShuffleStrategy = PlainShuffle()` in `PlaybackModule` (`:playback`).
  Change the right-hand side.
- **Test:** a class extending `ShuffleStrategyContract` (`core/taste/src/test/.../shuffle/`) that implements
  `strategy()`; see `PlainShuffleTest`.

### Change the schema

- **Interface:** Room's `Migration`.
- **Binding line:** the entry you add to `Migrations.ALL` (`:data`, `data/db/Migrations.kt`).
- **Test:** a `migrateNToN+1` case in `MusicDatabaseMigrationTest` (`data/src/androidTest`), run with
  `./gradlew deviceCheck` (ADR 0009).

Steps:

1. Change the entities and bump `version` in `MusicDatabase`.
2. Add `Migration(n, n + 1)` to `Migrations.ALL` (hand-written SQL; never destructive).
3. Build once and commit the new `data/schemas/.../<n + 1>.json`.
4. In the test, insert a `play_event` row at version n, run the migration and find the row afterwards.
