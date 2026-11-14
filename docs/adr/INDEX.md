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
| [0011](0011-basic-library-ui-shell.md) | Basic UI shell: one Library screen, one ViewModel, mini-player; replaced by real navigation later | superseded by [0016](0016-app-shell-with-tabs-theme-and-song-rows.md) |
| [0012](0012-search-normalisation-and-tiers.md) | Search folds case, accents and symbols for matching only; exact → whole-word → substring, with mid-word under "See all" | accepted |
| [0013](0013-onion-rules-shared-and-run-per-module.md) | Onion ArchUnit rules written once in `:core:model` fixtures and run by every module | accepted |
| [0014](0014-start-media-store-observer-from-the-application.md) | `MediaStoreObserver.start()` runs from `MusicPlayerApp.onCreate` | accepted |
| [0015](0015-playback-service-exported-and-no-cold-start-resumption.md) | Playback service stays exported (lint suppressed); no cold-start resumption until the queue is persisted | accepted |
| [0016](0016-app-shell-with-tabs-theme-and-song-rows.md) | App shell: bottom tabs, one dark and orange theme, shared song row, covers without Coil; supersedes 0011 | accepted |
| [0017](0017-search-tab-engine-per-snapshot-and-what-a-result-plays.md) | Search tab: one immutable engine per library snapshot behind an `:app` seam; what each result plays | accepted |
| [0018](0018-notification-cover-through-a-session-bitmap-loader.md) | Notification, lock screen and car covers come from a session `BitmapLoader` over a `CoverBitmapSource` seam bound by `:app` | accepted |
| [0019](0019-settings-entries-and-the-debug-link-through-the-debug-source-set.md) | Settings entries are a Hilt set; the debug link is added from the debug source set, not a runtime flag | accepted |
| [0020](0020-windowed-queue-between-play-queue-and-player.md) | The player holds a window of the queue (20 each side), kept in line by `QueueWindowSync`; the queue owns the order | accepted |
| [0021](0021-song-identity-and-retired-songs.md) | A song keeps its id for life, matched by volume and path then MediaStore id; missing files are retired, never deleted | accepted |
| [0022](0022-recently-added-window-is-edited-in-settings-only.md) | The Recently added window is edited in Settings only; the list shows it as a read-only caption | accepted |
| [0023](0023-top-100-is-scored-on-demand-behind-a-score-board.md) | The Top 100 is scored on demand behind a `ScoreBoard` port; a cached table is a later, separate decision | accepted |
| [0024](0024-taste-weights-live-in-one-config-in-core-taste.md) | Taste weights, burst rule, decay and list size live in one `TasteConfig` in `:core:taste`, defaults in code | accepted |
| [0025](0025-every-track-the-player-moves-to-is-logged.md) | Every track the player moves to gets its own `play_event` row; duration comes from the library; extends 0008 | accepted |
| [0026](0026-display-artist-comes-from-the-file-name-when-it-differs-from-the-tag.md) | The shown artist is the file-name artist when it differs from the tag, derived on read; the tag stays the grouping key | accepted |
| [0027](0027-artist-shuffle-also-queues-the-songs-that-feature-the-artist.md) | Artist shuffle also queues the songs that feature the artist; extends 0017 | accepted |
| [0028](0028-artist-photos-are-the-only-network-use.md) | Artist photos are the only network use, gated by a setting; amends design 3 | accepted |
| [0029](0029-artist-images-come-from-a-chain-of-sources.md) | Artist images come from a priority chain of sources: owner choice > Deezer > album collage > initials | accepted |
| [0030](0030-artist-image-overrides-live-in-room.md) | Artist image overrides and lookups live in Room (schema v2), gallery picks copied to app storage | accepted |
| [0031](0031-listened-time-uses-a-monotonic-clock.md) | Listened time uses an injected monotonic clock; `started_at` stays on the wall clock; extends 0025 | accepted |
| [0032](0032-a-controller-seek-to-a-window-index-is-a-queue-jump.md) | A controller seek to a window index goes through the queue as a jump and is logged as `picked`; extends 0025 | accepted |
| [0033](0033-previous-that-restarts-the-song-starts-a-new-row.md) | Previous that only restarts the song ends the row as `stopped` and starts a new `nav` row; extends 0025 | accepted |
| [0034](0034-top-100-rebuilds-when-the-library-changes.md) | The Top 100 rebuilds when the library changes, through a small `LibraryChanges` port over Room's invalidation tracker | accepted |
| [0035](0035-smart-shuffle-lives-behind-the-shuffle-strategy-and-artist-shuffle-is-exempted-in-the-queue.md) | Smart shuffle is a `ShuffleStrategy` with `orderAfter`; the queue exempts artist shuffle; gap 4 in `SmartShuffleConfig`, relaxed to fit; extends 0010 | accepted |
| [0036](0036-the-queue-is-saved-to-a-file-and-restored-paused-when-the-service-starts.md) | The queue is saved to a versioned file behind a `QueueStore` port and restored paused when the service starts, with no log row until play; extends 0015 | accepted |
| [0037](0037-playlists-are-two-room-tables-with-no-foreign-key-to-song.md) | Playlists are two Room tables (schema v3); a song is in a playlist once, with no foreign key to `song`; retired songs are hidden, not removed | accepted |
| [0038](0038-a-reorder-rewrites-only-the-slots-the-visible-songs-hold.md) | A playlist reorder rewrites only the slots the visible songs hold; hidden (retired) rows never move | accepted |
| [0039](0039-a-song-is-in-a-playlist-once-and-adding-reports-what-it-did.md) | A song is in a playlist once; adding skips repeats, reports "Added N, M already there", and always marks the playlist as used | accepted |
| [0040](0040-fast-add-is-one-sheet-one-target-type-and-a-per-screen-selection.md) | Fast add is one bottom sheet over an `AddTarget`, opened from the song menu, Now Playing "+" and a per-screen `SelectionState` | accepted |
| [0041](0041-playlists-on-home-in-library-and-a-plain-song-picker.md) | Playlists on Home and in Library; a new playlist gets its first songs from a plain song picker | accepted |
| [0042](0042-play-events-log-the-playlist-through-a-playlist-id-on-each-queue-entry.md) | Play events log the playlist via a playlist id on each queue entry; `PendingPlayStore` field 9; `QueueCodec` v2 | accepted |
| [0043](0043-the-playlist-page-edits-in-place-with-a-drag-handle-swipe-and-a-menu.md) | The playlist page edits in place: drag handle (`reorderable`), swipe to remove, menu for rename and delete | accepted |
| [0044](0044-save-as-playlist-is-one-use-case-refusing-shuffle-all-and-more-than-500-songs.md) | Save as playlist is one use case; refuses Shuffle all and more than 500 songs; the queue is read through `queueContent()` | accepted |
