package dev.tomasflopes.musicplayer.core.search

/**
 * An in-memory index over one snapshot of the library. Each indexed word of each field points to the songs
 * that contain it, so prefix and mid-word queries hit instantly. Rebuilt whenever the library changes.
 */
class SearchIndex {
    private val songs = mutableMapOf<Long, IndexableSong>() // song id -> song
    private val termIndex = mutableMapOf<String, MutableSet<Long>>() // normalized word -> song ids

    fun rebuild(songs: List<IndexableSong>) {
        this.songs.clear()
        this.termIndex.clear()
        for (song in songs) {
            this.songs[song.id.value] = song
            indexSong(song.id.value, song)
        }
    }

    private fun indexSong(
        songId: Long,
        song: IndexableSong,
    ) {
        indexField(songId, song.title)
        indexField(songId, song.artist)
        song.album?.let { indexField(songId, it) }
        song.albumArtist?.let { indexField(songId, it) }
        song.folderName?.let { indexField(songId, it) }
        song.featured.forEach { indexField(songId, it) }
    }

    private fun indexField(
        songId: Long,
        text: String?,
    ) {
        text?.let { Normalizer.words(it).forEach { add(it, songId) } }
    }

    private fun add(
        term: String,
        songId: Long,
    ) {
        termIndex.getOrPut(term) { mutableSetOf() }.add(songId)
    }

    /**
     * Song ids containing [term] in any field, at contains level or better. The library is small enough
     * (16k songs, tens of thousands of distinct words) that scanning the distinct words per keystroke is
     * instant, so this also covers starts-with and mid-word matches without a separate prefix index.
     */
    fun songsMatching(term: String): Set<Long> {
        val n = Normalizer.normalize(term)
        if (n.isEmpty()) return emptySet()
        return termIndex.entries
            .filter { (word, _) -> word == n || word.startsWith(n) || word.contains(n) }
            .flatMap { it.value }
            .toSet()
    }

    fun song(songId: Long): IndexableSong? = songs[songId]

    val size: Int get() = songs.size
}
