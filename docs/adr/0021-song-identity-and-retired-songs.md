# 0021: A song keeps its id for life; missing files are retired, never deleted

Date: 2026-11-13 · Status: accepted

**Context.** The listening log (design 7.3) points at songs by id and must survive file moves, a rebuilt MediaStore index and the owner changing the music folder and changing it back (design 7.0). A song row that is deleted when its file disappears would orphan its history (plan C4).

**Decision.** A song gets a `SongId` the first time a scan sees it and keeps it. `ScanPlanner` matches each MediaStore file to a known song by volume and path first, then by volume and MediaStore id among songs still in the library; each known song matches at most once per scan. Rows are never deleted. A known song whose file is gone is retired (`in_library = 0`) and hidden from every reader; its `play_event` rows stay. A retired song whose file shows up again is revived with the same id. A file that moved keeps its id and gets the new path. A new MediaStore id at the same path (the index was rebuilt) only refreshes the stored id. Tags are re-read when size or modified time changed. Identity is the path or the MediaStore id, not the content: there is no hash.

**Consequences.** History comes back when the folder is changed back. The songs table only grows. A file that is renamed or moved and also gets a new MediaStore id matches nothing and counts as a new song, while the old one is retired; its history is not carried over. Every reader must filter on `in_library` (`SongDaoTest` covers this); a new reader that forgets shows retired songs.
