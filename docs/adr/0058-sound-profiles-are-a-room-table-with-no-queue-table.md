# 0058: Sound profiles are a Room table; the queue is a query, not a table

Date: 2026-11-14 · Status: accepted · Amends the data section of the phase 3 file · Extends 0037

**Context.** Design §7.5 named `sound_profile` and `analysis_queue`. A queue table can drift out of step with `song` (retired songs, rescans).

**Decision.** Next schema version (v6 today) adds `sound_profile (song_id, profiler_version, features BLOB, source mac|phone, file_size, analysed_at)`, keyed by `song_id` with no foreign key to `song` (as in 0037), and `analysis_failure (song_id, profiler_version, attempts, last_error)`. The queue is a query: in-library songs with no profile for the current version and fewer than 3 failed attempts. `file_size` spots a replaced file.

**Consequences.** One LEFT JOIN per queue read. Every reader must filter on `in_library` (0021). Migration and DAO tests run in `deviceCheck` (0009).
