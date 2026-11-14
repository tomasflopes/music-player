# 0048: Shuffle is the primary action of a list; Play in order is the secondary one

Date: 2026-11-14 · Status: accepted

**Context.** The owner mostly shuffles, and the pair of buttons on lists and the search Top result had Play filled and Shuffle tonal. The pair is built in two places (`PlayShuffleButtons` and `TopResultCard`).

**Decision.**
- Shuffle is the filled `Button`, first (left); "Play in order" is the tonal `FilledTonalButton`, second. Both come from `ShuffleButton` and `PlayInOrderButton` in `PlayShuffleButtons.kt`, so there is one style.
- Every pair uses them: album, artist, playlist, Recently added, Top 100 (`PlayShuffleButtons`) and the search Top result for an artist or album (`TopResultCard`). A Top result that is one song has no Shuffle, so its single Play stays filled.
- Only the look and the order of the buttons change. What each button starts is unchanged, so a tapped song in an album still plays the album in tagged order.
- Home's "Shuffle all" card is already a single shuffle action and is unchanged.

**Consequences.** The old "Play" label became "Play in order" so its meaning stays clear next to the primary button; a future list header uses `PlayShuffleButtons` instead of building its own pair.
