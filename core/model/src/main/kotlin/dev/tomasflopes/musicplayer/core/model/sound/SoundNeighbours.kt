package dev.tomasflopes.musicplayer.core.model.sound

import java.text.Normalizer
import java.util.Locale

/**
 * Nearest songs for the evaluation tools (ADR 0073): the analyser command and the Sound neighbours screen both ask
 * [SoundSpace.nearest] through here, so they leave out the same songs. A song whose file has the same name as the
 * seed's is a copy of it and is never listed.
 */
object SoundNeighbours {
    /** The file name of [pathOrName] without folders or `.mp3`, NFC-normalised and lower case. */
    fun fileKey(pathOrName: String): String =
        Normalizer
            .normalize(pathOrName, Normalizer.Form.NFC)
            .substringAfterLast('/')
            .lowercase(Locale.ROOT)
            .removeSuffix(".mp3")

    /**
     * The [k] songs closest to [row], nearest first, without any song whose [fileKeyOfSong] equals the seed's. A
     * seed with no key leaves nothing out. [artistsOfSong] feeds the outlier [guard] (ADR 0087): an isolated seed
     * gets a share of its slots from songs of its own artist; without artists the list is plain sound.
     */
    fun nearest(
        space: SoundSpace,
        row: Int,
        k: Int,
        artistsOfSong: (Long) -> SongArtistKeys? = { null },
        guard: IsolationGuard = IsolationGuard(),
        fileKeyOfSong: (Long) -> String?,
    ): List<Neighbour> {
        val seedKey = fileKeyOfSong(space.songIds[row])
        val accept = { other: Int -> seedKey == null || fileKeyOfSong(space.songIds[other]) != seedKey }
        return guard.blend(space, row, k, accept, artistsOfSong)
    }
}
