# 0097: Both players end at speed 1.0, and the ease-back runs in a job of its own

Date: 2026-11-22 · Status: proposed

**Context.** The anti-goal is a song left sped up. ADR 0092 reset both players at the end of an overlap, a skip, a pause, a seek and a kill. The ease-back of 0095 now runs after the overlap, for tens of seconds, while the watcher is already planning the next transition.

**Decision.** `TwoPlayerCrossfadeEngine` starts the ease-back as its own coroutine on its scope (`easeJob`, plus the player it eases, `easing`): every 50 ms it sets the last step whose position the player has reached, and ends at exactly 1.0. The outgoing player is never stretched, and the retired player is reset when it is discarded. Everything that can end the song's normal course cancels the ease and sets the player to 1.0 at once: `invalidate` (skip, jump, seek, queue or settings change), `onPlayingChanged(false)`, the start of the next overlap and `release`. An overlap cut short or paused puts both players to 1.0 with no ease-back after it. An overlap that ends with a stretch that is not 1.0 and has no schedule resets at the end. The speed is never set by the host or the session, only by the engine.

**Consequences.** Any queue change during an ease-back, such as a refill appending songs, stops the ease and makes one step to 1.0 of up to 8 %; this is the price of the rule that every change resets, and it is for the owner to judge on the phone. Tested on virtual players and a fake clock (`TwoPlayerTransitionEngineTest`); not run. Extends 0061, 0092.
