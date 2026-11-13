# 0010: Queue modes: shuffle is a toggle, repeat has three states, manual entries survive a new play

Date: 2026-11-13 · Status: accepted

**Context.** Design §7.2 asks for plain shuffle plus Play next / Add to queue. The owner approved three refinements at plan time (plan A1, A2, C7) that decide how the queue behaves when the owner changes their mind mid-listen.

**Decision.** `PlayQueue` (pure Kotlin, `:playback`) owns the whole order; the player holds a window of it (plan C3). *Shuffle toggle (A1):* tapping a song plays its list in order. Turning shuffle on keeps the current song and re-orders only the non-manual songs still to come; turning it off puts them back in list order with the current song in its own place, so "next" is the song after it in the list. Shuffle buttons on Home and the artist, album, folder and Recently added pages start with shuffle on and the tapped song first. *Repeat (A2):* `OFF → ALL → ONE`. `ALL` wraps and re-shuffles when shuffle is on; `ONE` repeats the song when it ends by itself, but a pressed "next" still moves on and wraps. The mode survives a new play request. *Manual entries (C7):* Play next goes right after the current song, Add to queue after the last queued song; they are logged as `queue`, never move, never re-shuffle, and stay ahead of the new list when something else is played. *Logging:* toggling relabels upcoming non-manual entries with `PlaySource.withShuffle`.

**Consequences.** Unshuffling needs the original list, so the queue keeps it (and saves it in its snapshot). A song played twice in one list is matched by reference, so duplicates in a list should carry distinct refs (the library never produces duplicates).
