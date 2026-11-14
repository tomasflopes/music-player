# 0062: Crossfade policy is a pure rule table; the overlap is booked to the outgoing log row

Date: 2026-11-14 · Status: proposed

**Context.** Whether and how long to fade depends on settings, the two songs and what caused the move; it must not live in the player code. The log needs one `play_event` row per track (0025) and listened time on a monotonic clock (0031) that does not count the overlap twice.

**Decision.** `CrossfadePolicy.decide(settings, transition)` in `:core:model` is pure Kotlin. A hard cut when: off; repeat one; the cause is not the natural end (`MANUAL_SEEK` in the outgoing song, `TAP_JUMP` for next, previous, a tap or a controller jump); same song; the incoming song is the next of the same album in album order with shuffle off (`AlbumOrder`, so an album played in order stays gapless); a duration is unknown; or the fade that fits is under 500 ms. Otherwise the fade is `min(requested, shorter song / 3)`, the requested length forced into 1 to 12 s. `FadeCurve` is equal-power (`cos`, `sin` of `p * pi / 2`, exact 1 and 0 at the ends). `CrossfadeSettings` (off, 4 s) is stored through `SettingsRepository`.
Logging: at the start of the fade the outgoing row ends `completed` (its full duration, as for any natural end) and the incoming row starts `auto`; the incoming row's listening clock is held until the fade ends (`PlayEventTracker.holdClock`, `releaseHold`), whatever the player reports meanwhile. The overlap is therefore counted once, in the outgoing row. Skipping the incoming song during the fade ends its row `skipped` with 0 ms listened.

**Consequences.** Extends 0025 and 0031; edits neither. A listener who pauses mid-fade cannot add the overlap to the incoming row. Next and previous are hard cuts, a rule call the owner can overrule. A fade shrunk or cut by the policy needs no player support. Not accepted until `check` and the phone checks pass.
