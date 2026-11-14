# 0045: Library has no Folders tab

Date: 2026-11-14 · Status: accepted

**Context.** Library had five sub-tabs: Artists, Albums, Folders, Songs, Playlists (design 8.2, ADR 0016). The owner does not browse by folder, and five labels made the tab row overflow on the phone.

**Decision.**
- Library has four sub-tabs: Artists, Albums, Songs, Playlists. The Folders tab is removed, with everything only it used: `FoldersTab`, `FoldersViewModel`, `FoldersUiState`, their test, and `FolderRow`. No Hilt module or navigation entry named them (`@HiltViewModel` and the tab list were the only wiring).
- The "Save as playlist" button on a folder (ADR 0044) goes away with it. Save as playlist stays on dynamic lists, album, artist and Now Playing, and the use case is unchanged.
- Kept on purpose, because other code or a later feature can use them: the `FOLDER` `PlaySource` (old log rows carry it; the log is append-only), `SongReader.observeFolderSongs`, the folder search index (`SearchField.FOLDER`), and the folder data (`Folder`, `FolderReader`, `RoomFolderReader`, the folder DAO query, `FakeFolderReader`). `FolderReader` and `FakeFolderReader` now have no caller; they are listed as candidates for removal, not removed.

**Consequences.** Nothing in the library can be browsed as a folder tree. Bringing the tab back means a new UI over the kept data. Songs are still found by folder name in search.
