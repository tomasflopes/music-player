# 0077: Position is its own flow, and the queue state publishes only on queue events

Date: 2026-11-20 · Status: proposed

**Context.** `MediaControllerPlaybackController` ran a 500 ms ticker while playing, and every tick rebuilt the whole `PlaybackState`: a Room query for up to 220 queue songs, the mapping on the main thread, new lists and new `Song` objects. Every Now Playing row, the header and the mini-player recomposed twice a second, the tick ran with no screen open, and the progress bar moved only when the whole publish finished (performance slice 2, P1, P2, C1, C2).

**Decision.** `PlaybackPosition(positionMs, durationMs)` is not part of `PlaybackState`. `PlaybackController.position` is a cold `Flow` that reads `currentPosition` and `duration` from the controller, with no database. Each collector ticks (`positionTickMs`) only while the player is playing; while paused it emits once and again on any player event, a seek included. The singleton holds no ticker, so no collector means no work. The seek bar and the mini-player progress line collect it themselves. The queue state publishes only on media item transition, timeline change, is-playing, shuffle, repeat and playback state events (the custom commands announce themselves through `notifyModes`), and `publish()` reuses the previous `Song` objects when the window's song ids have not changed.

**Consequences.** Contract change in `PlaybackController` (`:playback`); amends the note on state in 0020, which stays unedited. A queue edit outside the player's window relies on the `notifyModes` signal reaching the controller as a shuffle or repeat event. Songs are re-read only when the window ids change, so a tag edit shows after the next queue change. Not measured on the phone; `check` has not run.
