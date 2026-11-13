# 0004: Read ID3 tags from each file, not from MediaStore

Date: 2026-11-13 · Status: accepted

**Context.** The owner curates tags (artist, album, album artist, track and disc number) and relies on exact album order. MediaStore copies can be incomplete or stale.

**Decision.** MediaStore only lists files. Metadata comes from the ID3 tags in each file. Albums order by disc, then track, and group by album plus album artist. "Contributing artists" holds the main artist only; featured artists are parsed from the title after `feat`.

**Consequences.** The scan reads every file's tags; it is re-read only when a file's size or modified date changes.
