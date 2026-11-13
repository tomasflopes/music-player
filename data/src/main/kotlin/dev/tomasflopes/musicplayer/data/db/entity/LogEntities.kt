package dev.tomasflopes.musicplayer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlayEvent
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.SearchEvent
import dev.tomasflopes.musicplayer.core.model.log.SearchItemType
import dev.tomasflopes.musicplayer.core.model.log.StartedBy

/** The listening log (design §7.3, ADR 0008). Insert-only. No foreign key: nothing may cascade into it. */
@Entity(tableName = "play_event", indices = [Index("song_id"), Index("started_at")])
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "song_id") val songId: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "listened_ms") val listenedMs: Long,
    @ColumnInfo(name = "song_duration_ms") val songDurationMs: Long,
    @ColumnInfo(name = "ended_by") val endedBy: EndedBy,
    val source: PlaySource,
    @ColumnInfo(name = "started_by") val startedBy: StartedBy,
    val shuffle: Boolean,
    @ColumnInfo(name = "mix_id") val mixId: Long?,
    @ColumnInfo(name = "playlist_id") val playlistId: Long?,
) {
    fun toDomain() =
        PlayEvent(
            songId = SongId(songId),
            startedAtEpochMs = startedAt,
            listenedMs = listenedMs,
            songDurationMs = songDurationMs,
            endedBy = endedBy,
            source = source,
            startedBy = startedBy,
            shuffle = shuffle,
            mixId = mixId,
            playlistId = playlistId,
        )

    companion object {
        fun from(event: PlayEvent) =
            with(event) {
                PlayEventEntity(
                    songId = songId.value,
                    startedAt = startedAtEpochMs,
                    listenedMs = listenedMs,
                    songDurationMs = songDurationMs,
                    endedBy = endedBy,
                    source = source,
                    startedBy = startedBy,
                    shuffle = shuffle,
                    mixId = mixId,
                    playlistId = playlistId,
                )
            }
    }
}

/** A chosen search result. Insert-only, like [PlayEventEntity]. */
@Entity(tableName = "search_event")
data class SearchEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    @ColumnInfo(name = "chosen_type") val chosenType: SearchItemType,
    @ColumnInfo(name = "chosen_id") val chosenId: Long,
    val at: Long,
) {
    companion object {
        fun from(event: SearchEvent) =
            SearchEventEntity(
                query = event.query,
                chosenType = event.chosenType,
                chosenId = event.chosenId,
                at = event.atEpochMs,
            )
    }
}
