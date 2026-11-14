# 0026: The shown artist is the file-name artist when it differs from the tag; the tag stays the key

Date: 2026-11-14 · Status: accepted

**Context.** Some files carry a playlist-like artist tag (`Guitarra`, the owner's guitar covers) while the file is `Rui Veloso - Anel de Rubi.mp3`. The app showed `Guitarra` everywhere, but the artist page, artist shuffle and "play all" must keep grouping those songs under `Guitarra`.

**Decision.** `DisplayArtist` in `:core:search` (pure Kotlin) parses the file name: extension and a leading track number (`01 `, `01.`, `01 - `, never `21 Savage`) are dropped, the artist is the text before the first spaced hyphen or dash (`Jay-Z` is not split), a trailing `feat.` credit is cut, and no spaced separator means no file-name artist. It is compared with the tag through `TextNormalizer` (case, accents, punctuation, `$` as `s`); one name of a credit (`A & B`, `A, B`) also matches. On a mismatch the file-name artist is shown, otherwise the tag spelling. The value is derived on read in `SongRow.toSong()`, so there is no Room column and no migration: `Song.artistName` is what is shown and the new `Song.tagArtistName` is the tag. `artistId`, artist pages, featured parsing and shuffles are untouched. Search indexes both names, so `rui veloso` and `guitarra` both find the song; artist results still come from tag artists.

**Consequences.** The parse runs for every song on each library emission (cheap string work, not measured on 16k songs). A file named `Title - Artist` shows the title as artist when it differs from the tag, and fixing the file name fixes it. Artist rows and the Artists tab list tag artists only, so `Rui Veloso` has no page unless some file is tagged with it.
