# 0069: Sound vectors reach `:core:taste` through a port that returns a ready `SoundSpace`

Date: 2026-11-15 · Status: proposed · Extends 0058, 0068

**Context.** `:core:taste` may depend on `:core:model` only (ADR 0013), so it must not see `SoundProfile`, which lives in `:core:analysis`. Reading 16k blobs of about 3 KB each, curve included, would cost memory and time that Discover does not need.

**Decision.** `SoundVectorSource` in `:core:model` has one call, `suspend fun space(): SoundSpace`. A `SoundSpace` holds song ids, `dim` and one flat `FloatArray`, already normalised and weighted (0068). `RoomSoundVectorSource` in `:data` runs one query, `SELECT song_id, substr(features, 1, 132)` joined to `song` on `in_library = 1` (every reader filters it, ADR 0058), the current `profiler_version`, and the file size the profile was made from (as the queue queries do, so a replaced file is not used). Rows with a shorter blob are skipped. The vectorizer runs on `Dispatchers.Default`. The space is built on the first call and kept in memory for the process; there is no new table and no schema change. The contract test `SoundVectorSourceContract` lives in `:core:analysis` test fixtures: a fake runs it in `check`, Room runs it in `deviceCheck` (ADR 0009).

**Consequences.** Profiles imported or analysed after the first call show up at the next app start. A second reader of the same space (the debug screen) costs nothing. The first call on the full library pays one query and one vectorizer pass; the time to open Home is a phone check. `:core:analysis` now depends on `:core:model`, which the onion rules allow.
