# 0027: Artist shuffle also queues the songs that feature the artist

Date: 2026-11-14 · Status: accepted

**Context.** Design 7.9 and 7.11 show an artist's "Featured on" songs after their own, but the artist page Shuffle and the Search top-result Shuffle queued the own songs only. ADR 0017 fixes the sources (`ARTIST_SHUFFLE` from a row or the card) and says nothing about the song set.

**Decision.** `artistShuffleSongs(own, featuredOn)` in `:core:model` returns the own songs then the featured ones, each song once. The artist page Shuffle and the Search top-result Shuffle for an artist queue that list with `PlaySource.ARTIST_SHUFFLE`; featured songs in it are logged as `artist_shuffle` too. Play in order, a tap on an own or featured song, Play next, Add to queue and album order keep their lists. Artist shuffle stays outside smart shuffle (design 7.8).

**Consequences.** A featured song played from an artist shuffle counts toward that artist's listening like any `artist_shuffle` row, although the artist is not its main artist; scoring does not distinguish them. Extends ADR 0017 without changing it.
