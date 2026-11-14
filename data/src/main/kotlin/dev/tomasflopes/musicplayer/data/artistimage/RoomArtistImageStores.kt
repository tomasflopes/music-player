package dev.tomasflopes.musicplayer.data.artistimage

import dev.tomasflopes.musicplayer.core.artistimage.ArtistImageChoice
import dev.tomasflopes.musicplayer.core.artistimage.ArtistImageLookup
import dev.tomasflopes.musicplayer.core.artistimage.ArtistImageLookupCache
import dev.tomasflopes.musicplayer.core.artistimage.ArtistImageOverrideStore
import dev.tomasflopes.musicplayer.core.artistimage.TimeSource
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.data.db.dao.ArtistImageDao
import dev.tomasflopes.musicplayer.data.db.entity.ArtistImageLookupEntity
import dev.tomasflopes.musicplayer.data.db.entity.ArtistImageOverrideEntity

/** The owner's per-artist choice in `artist_image_override` (ADR 0030). */
class RoomArtistImageOverrideStore(
    private val dao: ArtistImageDao,
    private val time: TimeSource,
) : ArtistImageOverrideStore {
    override suspend fun get(id: ArtistId): ArtistImageChoice? = dao.override(id.value)?.toChoice()

    override suspend fun set(
        id: ArtistId,
        choice: ArtistImageChoice,
    ) = dao.upsertOverride(choice.toEntity(id, time.nowMs()))

    override suspend fun clear(id: ArtistId) = dao.clearOverride(id.value)
}

/** The stored Deezer answers in `artist_image_lookup` (ADR 0030). */
class RoomArtistImageLookupCache(
    private val dao: ArtistImageDao,
) : ArtistImageLookupCache {
    override suspend fun get(id: ArtistId): ArtistImageLookup? =
        dao.lookup(id.value)?.let { ArtistImageLookup(it.pictureUrl, it.checkedAt) }

    override suspend fun put(
        id: ArtistId,
        lookup: ArtistImageLookup,
    ) = dao.upsertLookup(ArtistImageLookupEntity(id.value, lookup.pictureUrl, lookup.checkedAtMs))
}

internal const val KIND_REMOTE = "remote"
internal const val KIND_LOCAL = "local"
internal const val KIND_GENERATED = "generated"

internal fun ArtistImageChoice.toEntity(
    id: ArtistId,
    nowMs: Long,
): ArtistImageOverrideEntity =
    when (this) {
        is ArtistImageChoice.Remote -> ArtistImageOverrideEntity(id.value, KIND_REMOTE, url, nowMs)
        is ArtistImageChoice.LocalFile -> ArtistImageOverrideEntity(id.value, KIND_LOCAL, fileName, nowMs)
        ArtistImageChoice.Generated -> ArtistImageOverrideEntity(id.value, KIND_GENERATED, null, nowMs)
    }

/** A row this build does not know (a later version wrote it) reads as no choice: automatic. */
internal fun ArtistImageOverrideEntity.toChoice(): ArtistImageChoice? =
    when {
        kind == KIND_REMOTE && value != null -> ArtistImageChoice.Remote(value)
        kind == KIND_LOCAL && value != null -> ArtistImageChoice.LocalFile(value)
        kind == KIND_GENERATED -> ArtistImageChoice.Generated
        else -> null
    }
