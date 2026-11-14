# 0029: Artist images come from a priority chain of sources

Date: 2026-11-14 · Status: accepted

**Context.** Artist rows, avatars and the artist page need a picture. Photos are online, optional and sometimes wrong; the app must always show something.

**Decision.** `ArtistImageSource` (`:core:artistimage`, pure Kotlin) has a `priority`, an `online` flag and `find(artist)`, which returns `Found`, `Miss` or `SkipOnline`. Implementations are registered with a Hilt `@IntoSet` binding in `ExtensionPointsModule`; `ArtistImageChain` sorts them and the first `Found` wins. Order: `UserChoiceSource` (0) > `DeezerSource` (100) > `AlbumCollageSource` (200) > `InitialsSource` (300). The owner's choice is local and always wins; "Use generated image" returns `SkipOnline`, which skips every online source but still lets the collage and initials run. A Deezer hit counts only if its name matches the tag name through a `ArtistNameMatcher` (implemented in `:data` with `TextNormalizer`, because core modules may not depend on each other, ADR 0013); the empty-hash placeholder image is a miss. A source that throws is a miss. `ArtistImageResolver` keeps results in memory, shares one lookup per artist and is invalidated by overrides and by the setting. Each source has a contract test.

**Consequences.** A new source is one class and one binding. The collage uses 1 cover, or 4 (2 or 3 covers repeat in order to fill the 2x2).
