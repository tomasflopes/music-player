# 0070: Discover treats copies of a recording as one song

Date: 2026-11-15 · Status: proposed · Extends 0063

**Context.** The library holds 162 file names twice (for example HH/Chill and HH/Dillaz), each with its own song id. A copy of a song the owner has played would show up in Discover as "never played".

**Decision.** The duplicate key is the normalised main artist plus the title: lower case, accents and punctuation stripped, spaces collapsed (`Song.tagArtistName` and `Song.title`, so the artist is the tag artist that names the artist page, ADR 0026). A song counts as played if any copy with the same key has a `play_event` row. Of the copies that are not played, only the one with the lowest song id is a Discover candidate. The rule is one function, `neverPlayed`, in `:core:taste`, used by the v0 policy now and by the sound ranking later (0071), so the v0 fallback behaves the same. Nothing is merged or deleted in the library.

**Consequences.** Only library songs can name a key, so a played copy that was retired from the library no longer hides a copy that is still there. Two different recordings with the same artist and title (a live version tagged alike) count as one. Normalisation does not fold letters such as ø or ß. The owner's own play log is not rewritten, so Top 100 and the other lists still see two songs.
