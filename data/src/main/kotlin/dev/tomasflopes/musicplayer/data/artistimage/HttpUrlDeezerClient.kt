package dev.tomasflopes.musicplayer.data.artistimage

import dev.tomasflopes.musicplayer.core.artistimage.DeezerClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * `GET <baseUrl>/search/artist?q=<name>&limit=<n>` with no key (ADR 0028). The artist name is the only thing sent.
 * Runs off the main thread, at most [maxParallel] requests at a time, and answers `null` for any failure.
 */
class HttpUrlDeezerClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    maxParallel: Int = DEFAULT_PARALLEL,
) : DeezerClient {
    private val gate = Semaphore(maxParallel)

    override suspend fun searchArtists(
        query: String,
        limit: Int,
    ): String? = gate.withPermit { withContext(dispatcher) { request(query, limit) } }

    @Suppress("SwallowedException")
    private fun request(
        query: String,
        limit: Int,
    ): String? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val connection = URL("$baseUrl/search/artist?q=$encoded&limit=$limit").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode == HttpURLConnection.HTTP_OK) connection.readBody() else null
        } catch (e: IOException) {
            // Offline, a timeout or a reset: the caller treats it as "try again later", never as "not found".
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.readBody(): String? {
        val bytes = inputStream.use { it.readNBytes(MAX_BODY_BYTES + 1) }
        return if (bytes.size > MAX_BODY_BYTES) null else bytes.toString(Charsets.UTF_8)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deezer.com"
        private const val DEFAULT_PARALLEL = 2
        private const val TIMEOUT_MS = 8_000
        private const val MAX_BODY_BYTES = 256 * 1024
    }
}
