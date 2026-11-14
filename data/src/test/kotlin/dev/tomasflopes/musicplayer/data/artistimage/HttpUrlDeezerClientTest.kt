package dev.tomasflopes.musicplayer.data.artistimage

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress

/** The real client against a loopback server: the request shape and every way a request can fail. */
class HttpUrlDeezerClientTest {
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    private val requests = mutableListOf<String>()
    private var status = 200
    private var body = """{"data":[]}"""

    init {
        server.createContext("/") { exchange ->
            requests += exchange.requestURI.toString()
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterEach
    fun stop() {
        runCatching { server.stop(0) }
    }

    private fun client(url: String = "http://127.0.0.1:${server.address.port}") = HttpUrlDeezerClient(baseUrl = url)

    @Test
    fun `asks the artist search with the encoded name and the limit, and returns the body`() =
        runBlocking {
            body = """{"data":[{"name":"Beyoncé"}]}"""
            assertEquals(body, client().searchArtists("Beyoncé & Co", 5))
            assertEquals(listOf("/search/artist?q=Beyonc%C3%A9+%26+Co&limit=5"), requests)
        }

    @Test
    fun `a status other than 200 is a failure`() =
        runBlocking {
            status = 500
            assertNull(client().searchArtists("Ed", 5))
            status = 403
            assertNull(client().searchArtists("Ed", 5))
        }

    @Test
    fun `an unreachable server is a failure, not an exception`() =
        runBlocking {
            val port = server.address.port
            server.stop(0)
            assertNull(client("http://127.0.0.1:$port").searchArtists("Ed", 5))
        }

    @Test
    fun `a body past the size cap is a failure`() =
        runBlocking {
            body = "x".repeat(300 * 1024)
            assertNull(client().searchArtists("Ed", 5))
        }
}
