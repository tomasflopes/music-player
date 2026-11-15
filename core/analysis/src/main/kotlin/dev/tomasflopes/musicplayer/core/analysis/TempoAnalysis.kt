package dev.tomasflopes.musicplayer.core.analysis

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val SCAN_MIN_TENTHS = 500
private const val SCAN_MAX_TENTHS = 2_200
private const val TENTHS = 10.0
private const val HARMONIC_WEIGHT = 0.5
private const val DISTINCT_SHARE = 0.02
private const val MIN_ONSET_SECONDS = 2.0
private const val FOLD_LOW = 60.0
private const val FOLD_HIGH = 180.0
private const val EVEN_SPLIT = 1.0 / 3.0
private const val CONFIDENCE_SCALE = 1.5
private const val PEAK_NEIGHBOURS = 2
private const val ONSET_THRESHOLD = 1.5
private const val MIN_PEAK_GAP_FRAMES = 3
private const val EPSILON = 1e-12
private const val SALIENCE_FLOOR = 0.15
private const val SALIENCE_FULL = 0.35
private const val LEVEL_FLOOR = 0.05
private const val LEVEL_FULL = 0.5

/** What the onset envelope says about the beat (ADR 0065). Times are frame offsets, converted by the caller. */
internal class TempoResult(
    val tempoBpm: Double,
    val confidence: Double,
    val candidates: List<Pair<Double, Double>>,
    val beatStrength: Double,
    val beatOffsetFrames: Double,
    val onsetCount: Int,
) {
    companion object {
        val NONE = TempoResult(0.0, 0.0, emptyList(), 0.0, 0.0, 0)
    }
}

/** Tempo, beat phase, pulse strength and onset count from one onset-strength envelope (one value per frame). */
internal object TempoAnalysis {
    fun analyse(
        onset: FloatArray,
        framesPerSecond: Double,
    ): TempoResult {
        if (onset.size < MIN_ONSET_SECONDS * framesPerSecond) return TempoResult.NONE
        val y = detrend(onset, framesPerSecond.roundToInt())
        val peaks = if (y.sumOf { it * it } < EPSILON) emptyList() else topPeaks(scan(y, framesPerSecond))
        return if (peaks.isEmpty() ||
            peaks[0].second <= 0.0
        ) {
            TempoResult.NONE
        } else {
            describe(y, onset, peaks, framesPerSecond)
        }
    }

    private fun describe(
        y: DoubleArray,
        onset: FloatArray,
        peaks: List<Pair<Double, Double>>,
        framesPerSecond: Double,
    ): TempoResult {
        val tempo = fold(peaks[0].first)
        val period = 60.0 * framesPerSecond / tempo
        val (re, im) = fourier(y, framesPerSecond, tempo)
        val phase = (-atan2(im, re) / (2.0 * PI)).let { it - floor(it) }
        val pulse = pulseFactor(y, onset)
        return TempoResult(
            tempoBpm = tempo,
            confidence = confidence(peaks.map { it.second }) * pulse,
            candidates = peaks.map { it.first to it.second / peaks[0].second },
            beatStrength = autocorrelation(y, period) * pulse,
            beatOffsetFrames = phase * period,
            onsetCount = countOnsets(y),
        )
    }

    /**
     * 0 to 1: how much of the onset envelope is made of pulses rather than a steady level. A click track reads about
     * 0.9, a held tone 0.1 to 0.2, noise 0.03 (measured on the test files). Below 0.15 there is no beat to be sure
     * about; from 0.35 up the beat measures count in full. An envelope whose mean level is below 0.05 (a mathematically
     * pure tone) counts for nothing whatever its shape, and one above 0.5 counts in full.
     */
    private fun pulseFactor(
        y: DoubleArray,
        onset: FloatArray,
    ): Double {
        val level = onset.average()
        val salience = y.average() / max(EPSILON, level)
        val peaky = ((salience - SALIENCE_FLOOR) / (SALIENCE_FULL - SALIENCE_FLOOR)).coerceIn(0.0, 1.0)
        val audible = ((level - LEVEL_FLOOR) / (LEVEL_FULL - LEVEL_FLOOR)).coerceIn(0.0, 1.0)
        return peaky * audible
    }

    /** The onset envelope minus its own 1 s centred moving mean, never below zero. */
    private fun detrend(
        onset: FloatArray,
        window: Int,
    ): DoubleArray {
        val prefix = DoubleArray(onset.size + 1)
        for (i in onset.indices) prefix[i + 1] = prefix[i] + onset[i]
        val half = window / 2
        return DoubleArray(onset.size) {
            val from = max(0, it - half)
            val to = min(onset.size, it + half + 1)
            max(0.0, onset[it] - (prefix[to] - prefix[from]) / (to - from))
        }
    }

    /** Score of every tempo from 50.0 to 220.0 BPM in 0.1 steps: its pulse plus half the pulse at double tempo. */
    private fun scan(
        y: DoubleArray,
        framesPerSecond: Double,
    ): DoubleArray {
        val magnitude = DoubleArray(2 * SCAN_MAX_TENTHS - SCAN_MIN_TENTHS + 1)
        for (j in magnitude.indices) {
            val (re, im) = fourier(y, framesPerSecond, (SCAN_MIN_TENTHS + j) / TENTHS)
            magnitude[j] = sqrt(re * re + im * im)
        }
        return DoubleArray(SCAN_MAX_TENTHS - SCAN_MIN_TENTHS + 1) {
            val tenths = SCAN_MIN_TENTHS + it
            magnitude[it] + HARMONIC_WEIGHT * magnitude[2 * tenths - SCAN_MIN_TENTHS]
        }
    }

    /** Sum of y[n] * e^(-i * 2 * pi * f * n) at [bpm], rotated step by step (deterministic, no trig per frame). */
    private fun fourier(
        y: DoubleArray,
        framesPerSecond: Double,
        bpm: Double,
    ): Pair<Double, Double> {
        val angle = 2.0 * PI * bpm / 60.0 / framesPerSecond
        val stepRe = cos(angle)
        val stepIm = -sin(angle)
        var re = 0.0
        var im = 0.0
        var phaseRe = 1.0
        var phaseIm = 0.0
        for (value in y) {
            re += value * phaseRe
            im += value * phaseIm
            val nextRe = phaseRe * stepRe - phaseIm * stepIm
            phaseIm = phaseRe * stepIm + phaseIm * stepRe
            phaseRe = nextRe
        }
        return re to im
    }

    /** The three best scores that are the maximum within 2 % in BPM, best first, as (BPM, score). */
    private fun topPeaks(scores: DoubleArray): List<Pair<Double, Double>> {
        val peaks = mutableListOf<Pair<Double, Double>>()
        for (j in scores.indices) {
            val bpm = (SCAN_MIN_TENTHS + j) / TENTHS
            val reach = max(1, (bpm * DISTINCT_SHARE * TENTHS).roundToInt())
            val from = max(0, j - reach)
            val to = min(scores.size - 1, j + reach)
            if (isPeak(scores, j, from, to)) peaks += bpm to scores[j]
        }
        return peaks.sortedByDescending { it.second }.take(SoundProfile.CANDIDATE_SLOTS)
    }

    private fun isPeak(
        scores: DoubleArray,
        at: Int,
        from: Int,
        to: Int,
    ): Boolean =
        scores[at] > 0.0 &&
            (from until at).all { scores[it] < scores[at] } &&
            (at + 1..to).all { scores[it] <= scores[at] }

    private fun fold(bpm: Double): Double {
        var folded = bpm
        while (folded < FOLD_LOW) folded *= 2.0
        while (folded >= FOLD_HIGH) folded /= 2.0
        return (folded * TENTHS).roundToInt() / TENTHS
    }

    /** The best peak's share of the candidate scores, rescaled so an even split reads 0 and a lone peak reads 1. */
    private fun confidence(scores: List<Double>): Double {
        val share = scores[0] / scores.sum()
        return ((share - EVEN_SPLIT) * CONFIDENCE_SCALE).coerceIn(0.0, 1.0)
    }

    /** Largest normalised autocorrelation at the two whole lags around the beat [period] (in frames). */
    private fun autocorrelation(
        y: DoubleArray,
        period: Double,
    ): Double {
        val energy = y.sumOf { it * it }
        val lags = listOf(floor(period).toInt(), ceil(period).toInt()).filter { it in 1 until y.size }
        val best =
            lags.maxOfOrNull { lag ->
                var sum = 0.0
                for (i in 0 until y.size - lag) sum += y[i] * y[i + lag]
                sum / energy
            } ?: 0.0
        return best.coerceIn(0.0, 1.0)
    }

    /** Peaks of the detrended envelope above 1.5 times its mean, at least three frames apart. */
    private fun countOnsets(y: DoubleArray): Int {
        val threshold = ONSET_THRESHOLD * y.average()
        var count = 0
        var last = -MIN_PEAK_GAP_FRAMES
        for (i in y.indices) {
            if (y[i] <= threshold || i - last < MIN_PEAK_GAP_FRAMES) continue
            if (isLocalMax(y, i)) {
                count++
                last = i
            }
        }
        return count
    }

    private fun isLocalMax(
        y: DoubleArray,
        at: Int,
    ): Boolean =
        (max(0, at - PEAK_NEIGHBOURS)..min(y.size - 1, at + PEAK_NEIGHBOURS)).all { k ->
            k == at || if (k < at) y[k] < y[at] else y[k] <= y[at]
        }
}
