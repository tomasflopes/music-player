# 0012: Search matching ignores case, accents and symbols; display stays exactly as tagged

Date: 2026-11-13 · Status: accepted

**Context.** Phase 1's visible win is search (design §7.9, §10). The owner's library is full of names that are hard to type exactly: `JAY-Z`, `A$AP Rocky`, `m.A.A.d city`, `N.W.A`, `Beyoncé`, `Song (Remix)`. Vinyl matches substrings everywhere, so `Ed` buries the artist Ed under every song containing "ed". Search is a performance-critical path (16k songs, every keystroke), so matching and ranking must be testable off-device.

**Decision.** `:core:search` folds text for matching only: `spaced` turns punctuation into spaces, `compact` removes it, and `$` is also read as `s` (`A$AP Rocky` matches `asap rocky`). Display text is never normalised; results show the tags exactly. Ranking has three tiers: exact, whole-word / starts-with, mid-word substring. The main results show only the first two; mid-word matches appear last under "See all". A query must match every word it contains, in any order. Top result prefers an artist, then an album, then a song at the same tier; section ties are alphabetical until phase 2 adds recent listening score. Each field has a `SearchMatcher`; the default `FieldMatcher` is registered per field, so new fields or rules are added without editing `SearchIndex`.

**Consequences.** The index is rebuilt in memory from the library snapshot and is not persisted. `$` expands one name to three folded forms, which is cheap. A misspelled word is still a miss; fuzzy matching is out of scope.
