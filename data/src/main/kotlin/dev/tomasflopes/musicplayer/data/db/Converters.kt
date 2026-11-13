package dev.tomasflopes.musicplayer.data.db

import androidx.room.TypeConverter
import dev.tomasflopes.musicplayer.core.model.log.EndedBy
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import dev.tomasflopes.musicplayer.core.model.log.SearchItemType
import dev.tomasflopes.musicplayer.core.model.log.StartedBy

/** Log enums are stored as their permanent wire strings, never as ordinals or Kotlin names. */
internal class Converters {
    @TypeConverter fun playSource(value: PlaySource): String = value.wire

    @TypeConverter fun playSource(value: String): PlaySource = PlaySource.fromWire(value)

    @TypeConverter fun endedBy(value: EndedBy): String = value.wire

    @TypeConverter fun endedBy(value: String): EndedBy = EndedBy.fromWire(value)

    @TypeConverter fun startedBy(value: StartedBy): String = value.wire

    @TypeConverter fun startedBy(value: String): StartedBy = StartedBy.fromWire(value)

    @TypeConverter fun searchItemType(value: SearchItemType): String = value.name.lowercase()

    @TypeConverter fun searchItemType(value: String): SearchItemType = SearchItemType.valueOf(value.uppercase())
}
