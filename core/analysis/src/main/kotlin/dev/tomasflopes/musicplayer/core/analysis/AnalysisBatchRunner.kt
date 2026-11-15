package dev.tomasflopes.musicplayer.core.analysis

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One song the phone has to analyse. [fileSize] is the song row's, written with the profile (ADR 0058). */
data class AnalysisTarget(
    val songId: Long,
    val location: String,
    val fileSize: Long,
    val attempts: Int,
)

/** The queue of ADR 0058: a query, not a table. Offers only songs with few enough failed attempts (see runner). */
interface AnalysisQueue {
    /** Up to [limit] songs that still need a profile, newest first, leaving out the ids in [skip]. */
    suspend fun next(
        limit: Int,
        skip: Set<Long>,
    ): List<AnalysisTarget>
}

/** Where the results of a run go. */
interface AnalysisStore {
    /** Keeps [profile] for [target] (source phone) and clears its failures. */
    suspend fun saveProfile(
        target: AnalysisTarget,
        profile: SoundProfile,
    )

    /** Counts one more failed attempt for [target] and remembers [message]. */
    suspend fun recordFailure(
        target: AnalysisTarget,
        message: String,
    )
}

/** Wall-clock milliseconds, injected so the deadline can be tested. */
fun interface AnalysisClock {
    fun nowMs(): Long
}

/** What one run did. [finished] is false when the deadline stopped it before the queue was empty. */
data class BatchResult(
    val analysed: Int,
    val failed: Int,
    val finished: Boolean,
)

/**
 * Analyses queued songs one at a time until the queue is empty or the deadline passes (ADR 0059). Pure: the decoder,
 * the profiler, the queue, the store and the clock are all ports. A song that fails is recorded and not tried again in
 * the same run; after [MAX_ATTEMPTS] recorded failures the queue stops offering it (ADR 0058). The deadline is checked
 * between songs, so a song that has started is finished.
 */
class AnalysisBatchRunner(
    private val source: PcmSource,
    private val profiler: SoundProfiler,
    private val queue: AnalysisQueue,
    private val store: AnalysisStore,
    private val clock: AnalysisClock,
    private val batchSize: Int = BATCH_SIZE,
) {
    suspend fun run(deadlineMs: Long): BatchResult {
        val tally = Tally()
        val finished = drain(deadlineMs, tally)
        return BatchResult(tally.analysed, tally.failed, finished)
    }

    private class Tally {
        val seen = mutableSetOf<Long>()
        var analysed = 0
        var failed = 0
    }

    /** True when the queue ran dry, false when the deadline stopped the run first. */
    private suspend fun drain(
        deadlineMs: Long,
        tally: Tally,
    ): Boolean {
        var dry = false
        while (!dry && clock.nowMs() < deadlineMs) {
            val batch = queue.next(batchSize, tally.seen)
            dry = batch.isEmpty()
            if (!batch.all { step(it, deadlineMs, tally) }) return false
        }
        return dry
    }

    /** Handles one song; false means the deadline passed before it could start. */
    private suspend fun step(
        target: AnalysisTarget,
        deadlineMs: Long,
        tally: Tally,
    ): Boolean {
        tally.seen += target.songId
        val givenUp = target.attempts >= MAX_ATTEMPTS
        val late = !givenUp && clock.nowMs() >= deadlineMs
        if (!givenUp && !late) {
            currentCoroutineContext().ensureActive()
            val profile = analyse(target)
            if (profile != null) store.saveProfile(target, profile)
            if (profile != null) tally.analysed++ else tally.failed++
        }
        return !late
    }

    /** The profile, or null after the failure was recorded. A broken song must not stop the others. */
    private suspend fun analyse(target: AnalysisTarget): SoundProfile? {
        val failure =
            try {
                return profiler.analyseStream(source, target.location)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                e
            }
        store.recordFailure(target, failure.message ?: failure::class.java.simpleName)
        return null
    }

    companion object {
        /** A song that failed this many times is given up on (ADR 0058, 0059). */
        const val MAX_ATTEMPTS = 3
        const val BATCH_SIZE = 20
    }
}
