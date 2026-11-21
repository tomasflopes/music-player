package dev.tomasflopes.musicplayer.core.taste.lists

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.sound.SoundSpace
import dev.tomasflopes.musicplayer.core.taste.score.SongScore
import kotlin.random.Random

/**
 * Everything a Discover rule may read (ADR 0063, 0071). Pure data, so the rules can be run side by side on the same
 * input.
 *
 * @param library every song in the library.
 * @param played songs that have any log row, of any age or outcome.
 * @param artistScores global score per main artist; an artist with no entry counts as 0.
 * @param songScores decayed global score per song, best first (what the anchors are chosen from).
 * @param space the sound of every profiled song, or `null` when it is not known.
 */
class DiscoverInput(
    val library: List<Song>,
    val played: Set<SongId>,
    val artistScores: Map<ArtistId, Double>,
    val songScores: List<SongScore> = emptyList(),
    val space: SoundSpace? = null,
)

/**
 * The decision point of ADR 0071: how Discover picks its list. [DiscoverPolicy] (artist taste only, ADR 0063) and
 * [SoundAwareDiscoverPolicy] (artist taste plus sound affinity) both implement it, so the provider does not change
 * and the two can be compared on the same input.
 */
fun interface DiscoverRule {
    fun pick(
        input: DiscoverInput,
        random: Random,
    ): List<Song>
}
