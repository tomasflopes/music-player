# 0057: Import matches on NFC-normalised relative path and exact size

Date: 2026-11-14 · Status: proposed · Extends 0003

**Context.** The Mac library is a byte-for-byte copy of the phone's `Music/` folder (owner, 2026-11-14): same subfolders, same files, not re-tagged. macOS often stores names in NFD, Android in NFC, so `Nós.mp3` would never match raw.

**Decision.** Match on the path relative to the music root, NFC-normalised on both sides, plus exact file size. Duration is not a tie-breaker (paths are unique); it appears only in the mismatch report. Never MediaStore ids. Retired songs (ADR 0021) are matched too but not counted in the summary. `ProfileImportMatcher` is pure, in core.

**Consequences.** A file re-tagged or re-encoded after the copy changes size and falls to phone analysis. If the Mac copy drifts from the phone, the summary ("matched · queued") shows it.
