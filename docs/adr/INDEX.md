# ADR index

One line per decision. Open only the ADRs relevant to the task. Accepted ADRs are never edited, only superseded.

| # | Decision | Status |
|---|---|---|
| [0001](0001-no-cloud-ci.md) | No cloud CI; `./gradlew check` runs locally, enforced by a pre-push hook | accepted |
| [0002](0002-min-sdk-android-17.md) | minSdk, targetSdk and compileSdk are all Android 17 (API 37) | accepted |
| [0003](0003-bulk-analysis-on-the-mac.md) | Analyse the library on the Mac, import to the phone | accepted |
| [0004](0004-read-id3-tags-from-files.md) | Read ID3 tags from each file, not from MediaStore | accepted |
| [0005](0005-folder-picker-to-mediastore.md) | Folder picker (SAF) mapped to a MediaStore `RELATIVE_PATH` prefix; tags read through a file descriptor | accepted |
| [0006](0006-hand-written-id3-reader.md) | Hand-written ID3 reader in `:core:tags`, no tag library | accepted |
| [0007](0007-file-name-fallback-for-untagged-files.md) | Untagged files take title and artist from an "Artist - Title" file name; tags always win | accepted |
| [0008](0008-listening-log-fields-beyond-the-design.md) | Listening log adds `songs` / `artist` sources, `started_by` and `shuffle` | accepted |
| [0009](0009-room-tests-on-the-emulator.md) | Room, DAO and migration tests run on the emulator via `deviceCheck`, outside `check` | accepted |
| [0010](0010-queue-modes-shuffle-toggle-and-repeat.md) | Queue modes: shuffle toggle, repeat off/all/one, manual entries survive a new play | accepted |
| [0011](0011-basic-library-ui-shell.md) | Basic UI shell: one Library screen, one ViewModel, mini-player; replaced by real navigation later | accepted |
| [0012](0012-search-normalisation-and-tiers.md) | Search folds case, accents and symbols for matching only; exact → whole-word → substring, with mid-word under "See all" | accepted |
