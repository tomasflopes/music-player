package dev.tomasflopes.musicplayer.core.model.transition

import dev.tomasflopes.musicplayer.core.model.AlbumId
import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.Song
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeDecision
import dev.tomasflopes.musicplayer.core.model.crossfade.CrossfadeSettings
import dev.tomasflopes.musicplayer.core.model.crossfade.Transition
import dev.tomasflopes.musicplayer.core.model.crossfade.TransitionCause
import dev.tomasflopes.musicplayer.core.model.crossfade.TransitionStyle

/** One named check; plain code, so JUnit 5 and JUnit 4 can both run it (as in the other contracts). */
class TechniqueCheck(
    val name: String,
    val run: () -> Unit,
)

/** A profiled song as the contract and the planner tests build one: a steady [tempo] with a 16 bar intro. */
fun transitionProfile(
    tempo: Double = 120.0,
    firstBeatMs: Double = 500.0,
    confidence: Float = 0.9f,
    musicStartMs: Long = 500,
    introEndMs: Long = 20_000,
    outroStartMs: Long = 160_000,
    musicEndMs: Long = 178_000,
) = TransitionProfile(tempo, firstBeatMs, confidence, musicStartMs, introEndMs, outroStartMs, musicEndMs)

fun transitionSong(
    id: Long,
    durationMs: Long = 180_000,
) = Song(
    id = SongId(id),
    title = "Song $id",
    artistId = ArtistId(id),
    artistName = "Artist $id",
    albumId = AlbumId(id),
    albumTitle = "Album $id",
    albumArtist = "Artist $id",
    trackNumber = 1,
    discNumber = 1,
    durationMs = durationMs,
    volumeName = "external_primary",
    path = "Music/$id.mp3",
    fileName = "$id.mp3",
    folderPath = "Music",
    dateAddedEpochSec = 0,
    contentUri = "content://media/external/audio/media/$id",
    hasCover = false,
)

fun transitionRequest(
    outgoing: TransitionProfile? = transitionProfile(),
    incoming: TransitionProfile? = transitionProfile(),
    outgoingDurationMs: Long = 180_000,
    incomingDurationMs: Long = 180_000,
    config: TransitionConfig = TransitionConfig(),
) = TransitionRequest(
    transition =
        Transition(
            transitionSong(1, outgoingDurationMs),
            transitionSong(2, incomingDurationMs),
            TransitionCause.END_OF_SONG,
            shuffled = true,
            repeatOne = false,
        ),
    settings = CrossfadeSettings(enabled = true, durationMs = 4_000, style = TransitionStyle.DJ_MIX),
    phaseSix = CrossfadeDecision.Fade(4_000),
    outgoing = outgoing,
    incoming = incoming,
    config = config,
)

/**
 * What every [TransitionTechnique] must do (ADR 0092): never throw whatever the profiles hold, answer the same twice,
 * and, when it makes an overlap, keep it inside both songs with volume curves that stay in 0..1, start at 0 and 1,
 * end at 1 and 0 and only ever move one way.
 */
object TransitionTechniqueContract {
    private val awkward =
        listOf(
            null,
            transitionProfile(tempo = Double.NaN),
            transitionProfile(tempo = 0.0),
            transitionProfile(tempo = 1_000.0),
            transitionProfile(confidence = Float.NaN),
            transitionProfile(introEndMs = 100, musicStartMs = 5_000),
            transitionProfile(outroStartMs = 10_000, musicEndMs = 5_000),
            transitionProfile(firstBeatMs = -3.0),
            transitionProfile(musicEndMs = 5_000_000),
        )

    fun checks(factory: () -> TransitionTechnique): List<TechniqueCheck> =
        listOf(
            TechniqueCheck("has a name and a priority") { identity(factory()) },
            TechniqueCheck("never throws for missing or out-of-range profiles") { awkwardProfiles(factory()) },
            TechniqueCheck("never throws for unknown or tiny durations") { oddDurations(factory()) },
            TechniqueCheck("answers the same request the same way") { deterministic(factory()) },
            TechniqueCheck("an overlap stays inside both songs") { overlapFits(factory()) },
            TechniqueCheck("the volume curves are bounded, exact at the ends and monotone") { curves(factory()) },
        )

    private fun identity(technique: TransitionTechnique) {
        check(technique.name.isNotBlank()) { "blank name" }
        check(technique.priority in 0..TransitionTechnique.LAST_PRIORITY) { "priority ${technique.priority}" }
    }

    private fun awkwardProfiles(technique: TransitionTechnique) {
        for (a in awkward) for (b in awkward) technique.plan(transitionRequest(outgoing = a, incoming = b))
    }

    private fun oddDurations(technique: TransitionTechnique) {
        for (d in listOf(0L, 1L, 1_000L, 12_345L)) {
            technique.plan(transitionRequest(outgoingDurationMs = d, incomingDurationMs = d))
        }
    }

    private fun deterministic(technique: TransitionTechnique) {
        val first = technique.plan(transitionRequest())
        val second = technique.plan(transitionRequest())
        check(describe(first) == describe(second)) { "${describe(first)} then ${describe(second)}" }
    }

    private fun overlapFits(technique: TransitionTechnique) {
        for (request in sampleRequests()) {
            val plan = technique.plan(request) as? TransitionPlan.Overlap ?: continue
            val out = request.transition.outgoing.durationMs
            val inc = request.transition.incoming.durationMs
            check(plan.overlapMs > 0) { "empty overlap" }
            check(plan.switchPointMs >= 0 && plan.incomingStartMs >= 0) { "negative start" }
            check(plan.switchPointMs + plan.overlapMs <= out) { "outgoing runs out: ${describe(plan)}" }
            check(plan.incomingStartMs + plan.overlapMs <= inc) { "incoming runs out: ${describe(plan)}" }
        }
    }

    private fun curves(technique: TransitionTechnique) {
        for (request in sampleRequests()) {
            val plan = technique.plan(request) as? TransitionPlan.Overlap ?: continue
            val curve = plan.curve
            check(curve.outgoing(0f) == 1f && curve.incoming(0f) == 0f) { "start of ${plan.technique}" }
            check(curve.outgoing(1f) == 0f && curve.incoming(1f) == 1f) { "end of ${plan.technique}" }
            var previousOut = 1f
            var previousIn = 0f
            for (i in 0..STEPS) {
                val p = i.toFloat() / STEPS
                val out = curve.outgoing(p)
                val inc = curve.incoming(p)
                check(out in 0f..1f && inc in 0f..1f) { "out of range at $p" }
                check(out <= previousOut && inc >= previousIn) { "not monotone at $p" }
                previousOut = out
                previousIn = inc
            }
        }
    }

    private fun sampleRequests() =
        listOf(
            transitionRequest(),
            transitionRequest(incoming = transitionProfile(tempo = 120.5)),
            transitionRequest(incoming = transitionProfile(introEndMs = 9_000)),
            transitionRequest(outgoing = transitionProfile(tempo = 90.0), incoming = transitionProfile(tempo = 90.0)),
        )

    private fun describe(plan: TransitionPlan?): String =
        when (plan) {
            null -> "none"
            is TransitionPlan.Overlap ->
                "${plan.technique}:${plan.switchPointMs}:${plan.incomingStartMs}:${plan.overlapMs}:${plan.incomingSpeed}"
            else -> plan.toString()
        }

    private const val STEPS = 100
}
