# 0034: The Top 100 rebuilds when the library changes, through a `LibraryChanges` port

Date: 2026-11-14 · Status: accepted

**Context.** `TopHundredProvider` re-ran only when the `ScoreBoard` emitted, which is when the listening log changes. It then asked the library for its songs once. A song retired by a rescan stayed in the list and in the card count, and a song that came back stayed out, until the next log insert. Audit finding S3. The existing `SongReader` flows were not a fit: `observeSongs` re-reads and maps every song (about 16 000) on each change, and a query that is always empty, such as `observeAddedSince` with a far-future date, relies on Room re-emitting equal results, which is not guaranteed.

**Decision.** A small port in `:core:model`, `LibraryChanges.observe(): Flow<Unit>`, fires when the library's songs may have changed. `RoomLibraryChanges` in `:data` implements it with Room's invalidation tracker on the `song` table, which is where a scan adds, retires and revives songs. `TopHundredProvider` combines the board with this signal, rebuilds the list on either, and does not emit equal lists twice.

**Consequences.** The list follows a rescan at once. The signal says "something changed" and not what, so each firing costs one ranked read and one `songsByIds` chunk; a scan that writes in batches fires it more than once, which the distinct filter absorbs when the result is the same. Other lists that hold songs by id can use the same port. The Room implementation has no JVM test (the DAO tests need an emulator); the provider is tested with a fake port.
