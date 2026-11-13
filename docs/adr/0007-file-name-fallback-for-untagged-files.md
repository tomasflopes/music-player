# 0007: Untagged files take title and artist from an "Artist - Title" file name

Date: 2026-11-13 · Status: accepted · Extends 0004

**Context.** The plan Task 6 audit of `~/Downloads/musica` found 378 files with no title or artist tag (350 converted by ffmpeg), 373 of them named `Artist - Title.mp3`. The owner confirmed the real library mixes tagged and untagged files. With only "file name as title, Unknown artist", most of the library would sit under one artist, breaking the Artists tab and the "Ed" search.

**Decision.** Tags always win (0004 stands). Per field, when a tag is missing or blank, `DisplayFallbacks` splits the file name (extension dropped) on the first ` - `: left = artist, right = title. No split when either side is empty or the left side is a bare track number (`01`, `07.`). Otherwise title = file name, artist = "Unknown artist". The result is what the app shows and groups by; nothing is written back to the files.

**Consequences.** Artist entries can come from file names, spelled as the file names spell them (`Perigo Publico` and `Perigo Público` are two artists). Featured artists stay in the derived title for the feature parser. The audit reports how often the fallback fires. Tagging the files later changes nothing in code.
