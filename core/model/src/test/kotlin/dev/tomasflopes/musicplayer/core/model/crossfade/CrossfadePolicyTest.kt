package dev.tomasflopes.musicplayer.core.model.crossfade

import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CrossfadePolicyTest {
    private val on = CrossfadeSettings(enabled = true, durationMs = 4_000)

    private fun track(
        name: String,
        track: Int,
        album: String = "Album",
        durationMs: Long = 180_000,
        id: Long = name.hashCode().toLong(),
    ): Song = song(name, track = track, id = id).copy(albumTitle = album, durationMs = durationMs)

    private fun transition(
        outgoing: Song = track("a", 1),
        incoming: Song = track("b", 1, album = "Other", id = 2),
        cause: TransitionCause = TransitionCause.END_OF_SONG,
        shuffled: Boolean = false,
        repeatOne: Boolean = false,
    ) = Transition(outgoing, incoming, cause, shuffled, repeatOne)

    private fun decide(
        settings: CrossfadeSettings = on,
        transition: Transition = transition(),
    ) = CrossfadePolicy.decide(settings, transition)

    @Test
    fun `fades by the requested length between two songs of different albums`() {
        assertEquals(CrossfadeDecision.Fade(4_000), decide())
    }

    @Test
    fun `is a hard cut when crossfade is off`() {
        assertEquals(CrossfadeDecision.HardCut, decide(on.copy(enabled = false)))
    }

    @Test
    fun `is off by default`() {
        assertEquals(CrossfadeDecision.HardCut, decide(CrossfadeSettings()))
    }

    @Test
    fun `is a hard cut on repeat one`() {
        assertEquals(CrossfadeDecision.HardCut, decide(transition = transition(repeatOne = true)))
    }

    @Test
    fun `is a hard cut after a manual seek`() {
        assertEquals(
            CrossfadeDecision.HardCut,
            decide(transition = transition(cause = TransitionCause.MANUAL_SEEK)),
        )
    }

    @Test
    fun `is a hard cut after a tap or jump`() {
        assertEquals(CrossfadeDecision.HardCut, decide(transition = transition(cause = TransitionCause.TAP_JUMP)))
    }

    @Test
    fun `is a hard cut to the next track of the same album in order`() {
        val result = decide(transition = transition(track("a", 1), track("b", 2)))

        assertEquals(CrossfadeDecision.HardCut, result)
    }

    @Test
    fun `is a hard cut to a later track of the same album in order, with a track missing between`() {
        assertEquals(CrossfadeDecision.HardCut, decide(transition = transition(track("a", 1), track("c", 3))))
    }

    @Test
    fun `fades between two tracks of the same album when they are not in order`() {
        assertEquals(CrossfadeDecision.Fade(4_000), decide(transition = transition(track("b", 2), track("a", 1))))
    }

    @Test
    fun `fades between tracks of the same album when shuffle is on`() {
        val result = decide(transition = transition(track("a", 1), track("b", 2), shuffled = true))

        assertEquals(CrossfadeDecision.Fade(4_000), result)
    }

    @Test
    fun `fades between the last track of one album and the first of another`() {
        val result = decide(transition = transition(track("z", 12), track("a", 1, album = "Next")))

        assertEquals(CrossfadeDecision.Fade(4_000), result)
    }

    @Test
    fun `is a hard cut from a song to itself`() {
        val same = transition(track("a", 1, id = 7), track("a", 1, "X", id = 7))

        assertEquals(CrossfadeDecision.HardCut, decide(transition = same))
    }

    @Test
    fun `shrinks the fade to a third of the shorter song when the outgoing song is short`() {
        val result = decide(transition = transition(outgoing = track("a", 1, durationMs = 9_000)))

        assertEquals(CrossfadeDecision.Fade(3_000), result)
    }

    @Test
    fun `shrinks the fade to a third of the shorter song when the incoming song is short`() {
        val result = decide(transition = transition(incoming = track("b", 1, "Other", durationMs = 6_000, id = 2)))

        assertEquals(CrossfadeDecision.Fade(2_000), result)
    }

    @Test
    fun `keeps the requested fade when both songs are exactly three fades long`() {
        val result = decide(transition = transition(outgoing = track("a", 1, durationMs = 12_000)))

        assertEquals(CrossfadeDecision.Fade(4_000), result)
    }

    @Test
    fun `is a hard cut when the shrunk fade would be under half a second`() {
        val result = decide(transition = transition(outgoing = track("a", 1, durationMs = 1_400)))

        assertEquals(CrossfadeDecision.HardCut, result)
    }

    @Test
    fun `is a hard cut when a duration is unknown`() {
        assertEquals(
            CrossfadeDecision.HardCut,
            decide(transition = transition(outgoing = track("a", 1, durationMs = 0))),
        )
    }

    @Test
    fun `forces an out of range length into one to twelve seconds`() {
        assertEquals(CrossfadeDecision.Fade(12_000), decide(on.copy(durationMs = 60_000)))
        assertEquals(CrossfadeDecision.Fade(1_000), decide(on.copy(durationMs = 10)))
    }

    @Test
    fun `an unnumbered album in file name order stays gapless`() {
        val first = song("01 a.mp3").copy(durationMs = 180_000)
        val second = song("02 b.mp3").copy(durationMs = 180_000)

        assertEquals(CrossfadeDecision.HardCut, decide(transition = transition(first, second)))
    }
}
