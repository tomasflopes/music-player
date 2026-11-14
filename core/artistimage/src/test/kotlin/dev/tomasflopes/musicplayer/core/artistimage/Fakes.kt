package dev.tomasflopes.musicplayer.core.artistimage

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import kotlinx.coroutines.flow.MutableStateFlow

fun artist(
    id: Long = 1,
    name: String = "Rui Veloso",
) = ArtistRef(ArtistId(id), name)

fun coverSong(id: Long) =
    Song(
        id = SongId(id),
        title = "Song $id",
        artistId = ArtistId(1),
        artistName = "Rui Veloso",
        albumId = AlbumId(id),
        albumTitle = "Album $id",
        albumArtist = "Rui Veloso",
        trackNumber = null,
        discNumber = null,
        durationMs = 1000,
        volumeName = "external_primary",
        path = "Music/$id.mp3",
        fileName = "$id.mp3",
        folderPath = "Music",
        dateAddedEpochSec = 0,
        contentUri = "content://media/external_primary/audio/media/$id",
        hasCover = true,
    )

class FakeSource(
    override val priority: Int,
    override val online: Boolean = false,
    private val result: () -> SourceResult = { SourceResult.Miss },
) : ArtistImageSource {
    var calls = 0

    override suspend fun find(artist: ArtistRef): SourceResult {
        calls += 1
        return result()
    }
}

class FakePolicy(
    var allowed: Boolean = true,
) : OnlinePolicy {
    override suspend fun allowed() = allowed
}

class FakeClient(
    var body: String? = null,
) : DeezerClient {
    val requests = mutableListOf<Pair<String, Int>>()

    override suspend fun searchArtists(
        query: String,
        limit: Int,
    ): String? {
        requests += query to limit
        return body
    }
}

class FakeLookupCache : ArtistImageLookupCache {
    val entries = mutableMapOf<ArtistId, ArtistImageLookup>()

    override suspend fun get(id: ArtistId) = entries[id]

    override suspend fun put(
        id: ArtistId,
        lookup: ArtistImageLookup,
    ) {
        entries[id] = lookup
    }
}

class FakeOverrideStore : ArtistImageOverrideStore {
    val choices = mutableMapOf<ArtistId, ArtistImageChoice>()

    override suspend fun get(id: ArtistId) = choices[id]

    override suspend fun set(
        id: ArtistId,
        choice: ArtistImageChoice,
    ) {
        choices[id] = choice
    }

    override suspend fun clear(id: ArtistId) {
        choices.remove(id)
    }
}

class FakeFiles : LocalImageFiles {
    val deleted = mutableListOf<String>()

    override suspend fun delete(fileName: String) {
        deleted += fileName
    }
}

class FakeSettings(
    initial: ArtistPhotoMode = ArtistPhotoMode.WIFI_ONLY,
) : ArtistPhotoSettings {
    private val state = MutableStateFlow(initial)
    override val mode = state

    override suspend fun setMode(mode: ArtistPhotoMode) {
        state.value = mode
    }
}

/** Same name ignoring case; the real matcher folds accents and punctuation too (`:data`). */
val SameNameIgnoringCase = ArtistNameMatcher { tag, candidate -> tag.equals(candidate, ignoreCase = true) }

class FakeTime(
    var now: Long = 1_000_000,
) : TimeSource {
    override fun nowMs() = now
}

fun deezerReply(vararg hits: Pair<String, String>): String =
    hits.joinToString(prefix = """{"data":[""", postfix = """],"total":${hits.size}}""") { (name, picture) ->
        """{"id":1,"name":"$name","picture_medium":"$picture"}"""
    }

const val REAL_PHOTO = "https://cdn.test/images/artist/0123456789abcdef/250x250.jpg"
const val OTHER_PHOTO = "https://cdn.test/images/artist/fedcba9876543210/250x250.jpg"
const val PLACEHOLDER_PHOTO = "https://cdn.test/images/artist//250x250.jpg"
const val EMPTY_HASH_PHOTO = "https://cdn.test/images/artist/d41d8cd98f00b204e9800998ecf8427e/250x250.jpg"
