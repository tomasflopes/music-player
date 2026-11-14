package dev.tomasflopes.musicplayer.core.artistimage

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ArtistImageEditorTest {
    private val store = FakeOverrideStore()
    private val files = FakeFiles()
    private val id = artist().id

    private fun editor(scope: TestScope): Pair<ArtistImageEditor, ArtistImageResolver> {
        val policy = FakePolicy()
        val chain = ArtistImageChain(listOf(UserChoiceSource(store, policy), InitialsSource()))
        val resolver = ArtistImageResolver(chain, scope.backgroundScope)
        return ArtistImageEditor(store, files, resolver) to resolver
    }

    @Test
    fun `choosing an online photo is saved and shown at once`() =
        runTest {
            val (editor, resolver) = editor(this)
            assertEquals(ArtistImage.Initials("RV"), resolver.resolve(artist()))
            editor.useRemote(id, OTHER_PHOTO)
            assertEquals(ArtistImageChoice.Remote(OTHER_PHOTO), editor.current(id))
            assertEquals(ArtistImage.Remote(OTHER_PHOTO), resolver.resolve(artist()))
        }

    @Test
    fun `replacing a gallery image deletes the old copy, keeping the new one`() =
        runTest {
            val (editor, _) = editor(this)
            editor.useLocalFile(id, "1-100.img")
            editor.useLocalFile(id, "1-200.img")
            assertEquals(listOf("1-100.img"), files.deleted)
            assertEquals(ArtistImageChoice.LocalFile("1-200.img"), editor.current(id))
        }

    @Test
    fun `picking the same gallery file again deletes nothing`() =
        runTest {
            val (editor, _) = editor(this)
            editor.useLocalFile(id, "1-100.img")
            editor.useLocalFile(id, "1-100.img")
            assertEquals(emptyList<String>(), files.deleted)
        }

    @Test
    fun `generated deletes the gallery copy and shows the initials`() =
        runTest {
            val (editor, resolver) = editor(this)
            editor.useLocalFile(id, "1-100.img")
            editor.useGenerated(id)
            assertEquals(listOf("1-100.img"), files.deleted)
            assertEquals(ArtistImageChoice.Generated, editor.current(id))
            assertEquals(ArtistImage.Initials("RV"), resolver.resolve(artist()))
        }

    @Test
    fun `reset forgets the choice and deletes the gallery copy`() =
        runTest {
            val (editor, _) = editor(this)
            editor.useLocalFile(id, "1-100.img")
            editor.reset(id)
            assertNull(editor.current(id))
            assertEquals(listOf("1-100.img"), files.deleted)
        }

    @Test
    fun `reset of an artist with no choice is harmless`() =
        runTest {
            val (editor, _) = editor(this)
            editor.reset(id)
            assertNull(editor.current(id))
            assertEquals(emptyList<String>(), files.deleted)
        }
}
