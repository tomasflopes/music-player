package dev.tomasflopes.musicplayer.core.artistimage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeezerParserTest {
    private fun hits(body: String) = (DeezerParser.parse(body) as DeezerParse.Hits).hits

    @Test
    fun `a hit gives its name and medium picture`() {
        assertEquals(listOf(DeezerHit("Rui Veloso", REAL_PHOTO)), hits(deezerReply("Rui Veloso" to REAL_PHOTO)))
    }

    @Test
    fun `no hit is an empty list, not a failure`() {
        assertEquals(emptyList<DeezerHit>(), hits("""{"data":[],"total":0}"""))
    }

    @Test
    fun `the empty-path placeholder is not a photo`() {
        assertEquals(emptyList<DeezerHit>(), hits(deezerReply("Ed" to PLACEHOLDER_PHOTO)))
    }

    @Test
    fun `the empty-string hash is not a photo either`() {
        assertEquals(emptyList<DeezerHit>(), hits(deezerReply("Ed" to EMPTY_HASH_PHOTO)))
    }

    @Test
    fun `a placeholder is dropped and the other hits stay`() {
        val body = deezerReply("Ed" to PLACEHOLDER_PHOTO, "Ed Sheeran" to REAL_PHOTO)
        assertEquals(listOf("Ed Sheeran"), hits(body).map { it.name })
    }

    @Test
    fun `hits without a name or a picture are ignored`() {
        val body = """{"data":[{"name":"A"},{"picture_medium":"$REAL_PHOTO"},{"name":null}]}"""
        assertEquals(emptyList<DeezerHit>(), hits(body))
    }

    @Test
    fun `a quota error sent with status 200 is a failure`() {
        val body = """{"error":{"type":"Exception","message":"Quota limit exceeded","code":4}}"""
        assertEquals(DeezerParse.Failed, DeezerParser.parse(body))
    }

    @Test
    fun `a body that is not json is a failure`() {
        assertEquals(DeezerParse.Failed, DeezerParser.parse("<html>502</html>"))
        assertEquals(DeezerParse.Failed, DeezerParser.parse("[1,2]"))
        assertEquals(DeezerParse.Failed, DeezerParser.parse("{}"))
    }

    @Test
    fun `a wrong-name top hit is not accepted, a later right-name hit is`() {
        val found = hits(deezerReply("Ed Sheeran" to OTHER_PHOTO, "Ed" to REAL_PHOTO))
        assertEquals(REAL_PHOTO, DeezerParser.firstMatch(found, "Ed", SameNameIgnoringCase)?.pictureUrl)
    }

    @Test
    fun `no name match gives nothing`() {
        val found = hits(deezerReply("Queens of the Stone Age" to OTHER_PHOTO))
        assertNull(DeezerParser.firstMatch(found, "Queen", SameNameIgnoringCase))
    }

    @Test
    fun `placeholder detection`() {
        assertTrue(DeezerParser.isPlaceholder(""))
        assertTrue(DeezerParser.isPlaceholder(PLACEHOLDER_PHOTO))
        assertFalse(DeezerParser.isPlaceholder(REAL_PHOTO))
    }
}
