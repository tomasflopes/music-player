# 0081: A drag in Up next moves one upcoming song inside its own group, as a session command

Date: 2026-11-20 · Status: proposed

**Context.** The Now playing queue can remove rows and jump to them (ADR 0047) but not reorder them; only the playlist page has a drag handle (ADR 0043). The owner wants to reorder what is coming up while listening normally.

**Decision.**
- `moveInQueue(from, to, songId)` is a session command like `removeFromQueue`: places in the whole queue plus a song-id guard, so a stale place moves nothing. The engine runs it through the serialised `edit { }` path, so only the part of the player after the current song is replaced and the current song never reloads. It announces itself through `notifyModes` (ADR 0077), because a move beyond the 20-song window changes no player item. Nothing is logged: no song starts or ends.
- Only upcoming songs move. Played songs and the current song have no handle, and `PlayQueue.moveTarget` refuses them.
- Manual entries stay before auto entries (ADR 0010), so a drag cannot cross the boundary: it stops at the edge of its own group (`moveTarget` clamps `to` to the run of entries of the same kind).
- A reorder of auto songs also rewrites `originalOrder` in the slots those songs hold, as ADR 0038 does for playlists, so turning shuffle off keeps the reorder. Reordering manual songs leaves `originalOrder` alone.
- Undo is the shared slot (ADR 0046): the offer drags the song back. The list drags on a local copy and commits one move on drop (ADR 0043); `NowPlayingViewModel.move` clamps by group too, so the undo knows the final place.
- No `QueueCodec` change: entries (with the manual flag) and `originalOrder` are already saved, and the queue saves through `queueChanged`.

**Consequences.** Adds one command to `PlaybackController` (`:playback`). It relies on the 0077 note that a queue edit outside the window announces itself through `notifyModes`; it edits no 0077 file and conflicts with none of it. Rows that repeat ALL wrapped round have no place and cannot move. The drag feel can only be judged on the phone; `PlaybackCommandsMoveTest` is a `deviceCheck` test and has not run.
