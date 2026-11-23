package dev.tomasflopes.musicplayer.core.model.transition

/**
 * Why a technique did not apply to a pair, in the order its gates run (design phase 7b, ADR 0112). [OTHER] is a plan
 * that was made and then refused by the planner (a number out of range, a song too short), or a pair no technique
 * explained.
 */
enum class FallThrough {
    /** A song has no usable profile. */
    NO_PROFILE,

    /** A stored tempo confidence is under the gate (ADR 0099). */
    CONFIDENCE,

    /** The tempos are more than `maxStretch` apart, even at half or double. */
    TEMPO,

    /** The incoming intro cannot hold the shortest overlap (or the shortest overlap is over the cap). */
    INTRO,

    /** No switch point leaves room for the overlap before the music of the outgoing song ends. */
    TAIL,
    OTHER,
}

/** The counts the lab reads: techniques as [TransitionPlan.technique] names them, and the fall-through reasons. */
class TallySnapshot(
    val techniques: Map<String, Int>,
    val fallThroughs: Map<FallThrough, Int>,
) {
    val total: Int get() = techniques.values.sum()

    /** The pairs that ended in the phase 6 crossfade, which is what the reasons explain. */
    val crossfades: Int get() = techniques[CROSSFADE] ?: 0

    companion object {
        const val CROSSFADE = "crossfade"
    }
}

/**
 * A debug counter of which technique the planner picked for the transitions of this process, and why the pairs that
 * ended in the plain crossfade did (design phase 7b, ADR 0112). Nothing is stored: it starts at zero with the process
 * and the lab can reset it. Thread-safe, because the planner runs on a background dispatcher and the lab reads on the
 * main thread. A pair that is planned twice (a queue change before the overlap) counts twice.
 */
class TransitionTally {
    private val lock = Any()
    private val techniques = linkedMapOf<String, Int>()
    private val fallThroughs = linkedMapOf<FallThrough, Int>()

    /** One planned transition: [technique] won; [why] is set when it is the crossfade and a technique said why not. */
    fun record(
        technique: String,
        why: FallThrough? = null,
    ) = synchronized(lock) {
        techniques.merge(technique, 1, Int::plus)
        if (technique == TallySnapshot.CROSSFADE) fallThroughs.merge(why ?: FallThrough.OTHER, 1, Int::plus)
    }

    fun snapshot(): TallySnapshot = synchronized(lock) { TallySnapshot(techniques.toMap(), fallThroughs.toMap()) }

    fun reset() =
        synchronized(lock) {
            techniques.clear()
            fallThroughs.clear()
        }
}
