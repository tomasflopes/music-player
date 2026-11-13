package dev.tomasflopes.musicplayer.data.repo

import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.ports.SongFeatureReader
import dev.tomasflopes.musicplayer.data.db.MusicDatabase

class RoomFeatureReader(
    private val db: MusicDatabase,
) : SongFeatureReader {
    override suspend fun namesFor(songId: SongId): List<String> = db.songFeatureDao().namesFor(songId.value)
}
