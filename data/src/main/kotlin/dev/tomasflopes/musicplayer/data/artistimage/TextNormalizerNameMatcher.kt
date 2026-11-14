package dev.tomasflopes.musicplayer.data.artistimage

import dev.tomasflopes.musicplayer.core.artistimage.ArtistNameMatcher
import dev.tomasflopes.musicplayer.core.search.TextNormalizer

/**
 * Two names are the same when some folded form of one equals one of the other, so case, accents and punctuation
 * do not matter (`Beyoncé` = `beyonce`, `AC/DC` = `ACDC`, `A$AP Rocky` = `asap rocky`). Whole names only: `Ed`
 * does not match `Ed Sheeran`.
 */
class TextNormalizerNameMatcher : ArtistNameMatcher {
    override fun matches(
        tagName: String,
        candidateName: String,
    ): Boolean = TextNormalizer.variants(tagName).any { it in TextNormalizer.variants(candidateName) }
}
