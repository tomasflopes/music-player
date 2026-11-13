# 0009: Room, DAO and migration tests run on the emulator, outside `check`

Date: 2026-11-13 · Status: accepted

**Context.** The listening log must survive every schema change (design §7.3), so DAO queries and migrations need a trustworthy test. Guidelines §3.5 allow instrumented tests or Robolectric; Robolectric on API 37 is unverified, and real SQLite on Android is the better judge. Plan decision C2.

**Decision.** Room, DAO and migration tests are instrumented tests in `data/src/androidTest`, run with `./gradlew deviceCheck` (every module's `connectedDebugAndroidTest`) on the emulator. `./gradlew check` and the pre-push hook stay JVM-only and fast. A JVM test (`LogIsAppendOnlyTest`) still guards, inside `check`, that nothing updates or deletes log rows or drops the database. Schemas are exported to `data/schemas/` and committed.

**Consequences.** A broken DAO query is only caught when `deviceCheck` is run, so run it after touching `:data`. With the phone plugged in, set `ANDROID_SERIAL` so tests do not run on it.
