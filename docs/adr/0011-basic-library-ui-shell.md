# 0011: Basic UI shell: one Library screen, one ViewModel, mini-player

Date: 2026-11-13 · Status: accepted

**Context.** Phase 1 has scanning, the library readers and playback, but the launcher activity only shows a title. The owner wants something usable on the phone now, before the real navigation (design §10) exists.

**Decision.** A single `LibraryScreen` (folder picker, song list, shuffle, rescan) and a `MiniPlayer` bottom bar, both driven by `LibraryViewModel`, which only talks to the `:core:model` ports and `PlaybackController`. No navigation library yet. `READ_MEDIA_AUDIO` is requested at launch.

**Consequences.** Replaced when the real navigation lands: the screen splits into destinations, the ViewModel into per-screen ones. The debug screen stays for diagnostics. Not built: now-playing screen, queue view, search, stats.
