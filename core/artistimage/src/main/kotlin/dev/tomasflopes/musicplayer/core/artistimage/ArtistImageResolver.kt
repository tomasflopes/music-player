package dev.tomasflopes.musicplayer.core.artistimage

import dev.tomasflopes.musicplayer.core.model.ArtistId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the screens ask. Results are kept in memory (least recently used), and an artist asked for by several rows
 * at once is resolved once, in [scope], so a row scrolled away does not cancel the lookup the next row needs.
 * Anything that changes an answer calls [invalidate]; [changes] then ticks and visible rows ask again.
 */
class ArtistImageResolver(
    private val chain: ArtistImageChain,
    private val scope: CoroutineScope,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val lock = Any()
    private val results =
        object : LinkedHashMap<ArtistId, ArtistImage>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ArtistId, ArtistImage>) = size > maxEntries
        }
    private val inFlight = HashMap<ArtistId, Deferred<ArtistImage>>()
    private val generation = MutableStateFlow(0)

    /** Ticks every time an answer may have changed. */
    val changes: StateFlow<Int> = generation

    /** The remembered answer, or `null`. Never starts a lookup. */
    fun peek(id: ArtistId): ArtistImage? = synchronized(lock) { results[id] }

    suspend fun resolve(artist: ArtistRef): ArtistImage {
        val (done, pending) =
            synchronized(lock) {
                val remembered = results[artist.id]
                if (remembered != null) {
                    remembered to null
                } else {
                    null to inFlight.getOrPut(artist.id) { start(artist) }
                }
            }
        return done ?: checkNotNull(pending).await()
    }

    /** Forgets one artist, e.g. after the owner changed the photo. */
    fun invalidate(id: ArtistId) = reset { results.remove(id) }

    /** Forgets everything, e.g. after the setting changed. */
    fun invalidateAll() = reset { results.clear() }

    private fun reset(forget: () -> Unit) {
        synchronized(lock) {
            forget()
            inFlight.clear()
            generation.value += 1
        }
    }

    private fun start(artist: ArtistRef): Deferred<ArtistImage> {
        val startedAt = generation.value
        return scope.async(start = CoroutineStart.LAZY) {
            chain.resolve(artist).also { image ->
                synchronized(lock) {
                    if (generation.value == startedAt) {
                        results[artist.id] = image
                        inFlight.remove(artist.id)
                    }
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 1000
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}
