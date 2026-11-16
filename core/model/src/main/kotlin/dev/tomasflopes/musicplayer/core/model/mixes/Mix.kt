package dev.tomasflopes.musicplayer.core.model.mixes

import dev.tomasflopes.musicplayer.core.model.playlists.PlaylistName

@JvmInline
value class MixId(
    val value: Long,
)

/** What a mix seed points at. [wire] values are stored in `mix_seed.kind` and never renamed. */
enum class MixSeedKind(
    val wire: String,
) {
    SONG("song"),
    ARTIST("artist"),
    ;

    companion object {
        fun fromWire(wire: String): MixSeedKind = entries.first { it.wire == wire }
    }
}

/** One seed of a mix: a song id or an artist id, by [kind]. A seed is in a mix once. */
data class MixSeed(
    val kind: MixSeedKind,
    val refId: Long,
)

/**
 * A mix (design 7.6, ADR 0076): a name and its seeds, in the order the owner picked them. A seed whose song left the
 * library is not in [seeds] (ADR 0021, 0037); its row stays and comes back with the file.
 */
data class Mix(
    val id: MixId,
    val name: String,
    val seeds: List<MixSeed>,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

/** How many seeds a mix may have (domain) and how many the create flow asks for. */
object MixLimits {
    const val MIN_SEEDS = 1
    const val MAX_SEEDS = 5
    const val CREATE_MIN_SEEDS = 3
}

/** A mix is named by the playlist rule: trimmed, at most [MAX_LENGTH] characters, not empty. */
object MixName {
    const val MAX_LENGTH = PlaylistName.MAX_LENGTH

    fun clean(raw: String): String? = PlaylistName.clean(raw)
}
