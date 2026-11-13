# 0006: Hand-written ID3 reader, no tag library

Date: 2026-11-13 · Status: accepted

**Context.** ADR 0004 makes the file's ID3 tag the source of truth, so the tag reader decides whether the library looks right. The app needs six text frames and "is there a cover"; tag libraries are large, read whole pictures into memory, and several are unmaintained or carry licences that do not fit.

**Decision.** `:core:tags` has its own pure-Kotlin reader (`Id3TagReader`) for ID3v2.2, v2.3, v2.4 and ID3v1. It reads front to back once, skips picture payloads, never throws, and keeps text exactly as tagged. A v2.4 frame size is tried as synchsafe first and as a plain 32-bit number when the synchsafe reading does not land on a frame, padding or the end of the tag (old iTunes). UTF-16 text without a byte order mark is read as little endian. A track or disc number of 0 means "not tagged". Of a multi-value frame, the first value is used.

**Validation, three layers.** (1) Byte-level unit tests against tags written by a separate fixture writer that shares no code with the reader. (2) A seeded mutation test: 1000 truncated and bit-flipped files must come back as a result, never an exception. (3) An audit command run over the owner's real library (plan Task 6) before the reader is trusted.

**Consequences.** We own the edge cases: anything the audit shows that the reader gets wrong is fixed here, with a new byte-level test. Compressed and encrypted frames are skipped with a warning. Not read: ID3v2 tags appended at the end of a file, APEv2, Lyrics3.
