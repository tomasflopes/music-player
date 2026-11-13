# 0005: Folder picker (SAF) mapped to a MediaStore RELATIVE_PATH prefix; tags read through a file descriptor

Date: 2026-11-13 · Status: accepted

**Context.** Guidelines §4.3 #1: can the folder the owner picks with the system picker be turned into a MediaStore query that lists exactly the MP3s in it? Measured with the Task 2 spike on the owner's phone (Galaxy S24, Android 17, API 37), music folder `primary:Music`.

**Measurements.** Q1 `primary:Music` → volume `external_primary`, prefix `Music/` (no SD card) ✓. Q2 MediaStore 14,145 `audio/mpeg` rows vs 14,146 `*.mp3` on disk; the one difference is `HH/Corja - Nós.mp3`, an MP4 (DASH) file named `.mp3` that MediaStore types `audio/mp4` ✓ explained. Q3 only `.mp3` under the prefix; the 2 WhatsApp MP3s and one `.wav` are excluded by the prefix and MIME filters ✓. Q4 first 4 KB of 500 files, cold: file path 5.92 ms/file, descriptor 3.77 ms/file, 0 failures each (file path still works on Android 17). Q5 owner to compare with Vinyl. Q6 not yet measured. Q7 the only `.nomedia` is `Music/.thumbnails`; no music hidden ✓. Q8 list time 304 ms ✓.

**Decision.** Keep the SAF picker (authority `com.android.externalstorage.documents` only), map the tree id with `TreeUriMapper`, and list with `MediaStore.Audio` filtered by `MIME_TYPE = 'audio/mpeg' AND RELATIVE_PATH LIKE ?`. Tags are read through `ContentResolver.openFileDescriptor` (faster, and does not depend on raw path access). No file walk.

**Consequences.** Q4 misses its 3 ms target on a cold read: a first full scan reads ~14k files in roughly a minute, in the background; later scans re-read only changed files (0004). Files whose content is not MP3 do not appear even if named `.mp3`. Q5 and Q6 are still open; Task 12's observer tests cover Q6 on the emulator.
