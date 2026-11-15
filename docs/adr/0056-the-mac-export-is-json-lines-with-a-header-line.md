# 0056: The Mac export is JSON Lines with a header line

Date: 2026-11-14 · Status: accepted, line format amended by 0065 · Amends the "profiles.db" example in design §7.5

**Context.** The export must be resumable after Ctrl-C, easy to diff and readable by the app without a second database reader.

**Decision.** One file: a header line (`profiler_version`, feature names, music root hint) then one line per song `(relative_path, size, duration_ms, features)`, appended as songs finish. Codec in `:core:analysis` with kotlinx-serialization (already in the catalog). A rerun reads the file and skips songs already done; a different `profiler_version` stops the run, and the app rejects it with a clear message (ADR 0003).

**Consequences.** Larger than a binary file (~16k lines, a few MB) and not queryable; SQLite would need sqlite-jdbc on the Mac and a second reader on the phone. A half-written last line must be ignored by the reader.
