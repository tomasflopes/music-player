package dev.tomasflopes.musicplayer.core.analysis

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val SAMPLE_RATE = 22_050
private const val FFT_SIZE = 1_024
private const val HOP = 512
private const val HALF = 0.5
private const val BASS_LOW_HZ = 20.0
private const val BASS_HIGH_HZ = 250.0
private const val CHROMA_LOW_HZ = 129.0
private const val CHROMA_HIGH_HZ = 2_000.0
private const val REFERENCE_A_HZ = 440.0
private const val SEMITONES = 12
private const val A_PITCH_CLASS = 9
private const val LOG_GAIN = 10.0
private const val DEAD_BAND = 1e-3f
private const val BEAT_SLACK_SECONDS = 2.0 * HOP / SAMPLE_RATE

/** The profiler of ADR 0065: one pass, hand-written FFT 1024/512 at 22.05 kHz, no library. */
class HandBuiltProfilerV1 : SoundProfiler {
    override val version: Int = VERSION

    override fun newStream(sampleRate: Int): ProfileStream = Stream(sampleRate)

    companion object {
        const val VERSION = 1
    }

    private class Stream(
        sampleRate: Int,
    ) : ProfileStream {
        private val resampler = if (sampleRate == SAMPLE_RATE) null else StreamingResampler(sampleRate, SAMPLE_RATE)
        private val fft = Fft(FFT_SIZE)
        private val window = DoubleArray(FFT_SIZE) { HALF - HALF * cos(2.0 * PI * it / FFT_SIZE) }
        private val frame = FloatArray(FFT_SIZE)
        private var filled = 0
        private val re = DoubleArray(FFT_SIZE)
        private val im = DoubleArray(FFT_SIZE)
        private val previousLog = DoubleArray(FFT_SIZE / 2 + 1)
        private val chromaOf = IntArray(FFT_SIZE / 2 + 1) { chromaClass(it) }

        private val frameRms = FloatSink()
        private val onset = FloatSink()
        private val energyCurve = FloatSink()
        private var secondSum = 0.0
        private var secondCount = 0

        private var frames = 0
        private var weightSum = 0.0
        private var centroidSum = 0.0
        private var zeroCrossingSum = 0.0
        private var totalEnergy = 0.0
        private var bassEnergy = 0.0
        private val chroma = DoubleArray(SoundProfile.CHROMA_BINS)

        override fun feed(
            samples: FloatArray,
            count: Int,
        ) {
            consume(resampler?.push(samples, count) ?: samples.copyOf(count))
        }

        override fun finish(): SoundProfile {
            resampler?.let { consume(it.finish()) }
            if (secondCount > 0) energyCurve.add(sqrt(secondSum / secondCount).toFloat())
            return summarise()
        }

        private fun consume(samples: FloatArray) {
            for (value in samples) {
                secondSum += value.toDouble() * value
                if (++secondCount == SAMPLE_RATE) {
                    energyCurve.add(sqrt(secondSum / SAMPLE_RATE).toFloat())
                    secondSum = 0.0
                    secondCount = 0
                }
                frame[filled++] = value
                if (filled == FFT_SIZE) {
                    processFrame()
                    System.arraycopy(frame, HOP, frame, 0, FFT_SIZE - HOP)
                    filled = FFT_SIZE - HOP
                }
            }
        }

        private fun processFrame() {
            var sumSquares = 0.0
            var crossings = 0
            var lastSign = 0
            for (i in 0 until FFT_SIZE) {
                val value = frame[i]
                sumSquares += value.toDouble() * value
                val sign =
                    if (value >= DEAD_BAND) {
                        1
                    } else if (value <= -DEAD_BAND) {
                        -1
                    } else {
                        0
                    }
                if (sign != 0) {
                    if (sign == -lastSign) crossings++
                    lastSign = sign
                }
                re[i] = value * window[i]
                im[i] = 0.0
            }
            fft.transform(re, im)
            val rms = sqrt(sumSquares / FFT_SIZE)
            frameRms.add(rms.toFloat())
            onset.add(spectrumStep(rms, crossings).toFloat())
            frames++
        }

        /** Updates the spectrum accumulators with the current frame and returns its spectral flux. */
        private fun spectrumStep(
            rms: Double,
            crossings: Int,
        ): Double {
            var flux = 0.0
            var weighted = 0.0
            var sum = 0.0
            for (bin in 0..FFT_SIZE / 2) {
                val magnitude = sqrt(re[bin] * re[bin] + im[bin] * im[bin])
                val log = ln1p(LOG_GAIN * magnitude)
                if (frames > 0 && bin > 0 && log > previousLog[bin]) flux += log - previousLog[bin]
                previousLog[bin] = log
                if (bin == 0) continue
                val energy = magnitude * magnitude
                totalEnergy += energy
                if (bin in BASS_FIRST..BASS_LAST) bassEnergy += energy
                if (chromaOf[bin] >= 0) chroma[chromaOf[bin]] += energy
                weighted += bin * binHz() * magnitude
                sum += magnitude
            }
            if (sum > 0.0) {
                weightSum += rms
                centroidSum += rms * weighted / sum
                zeroCrossingSum += rms * crossings / (FFT_SIZE - 1)
            }
            return flux
        }

        private fun summarise(): SoundProfile {
            val rms = frameRms.toArray()
            val framesPerSecond = SAMPLE_RATE.toDouble() / HOP
            val tempo = TempoAnalysis.analyse(onset.toArray(), framesPerSecond)
            val span = IntroOutro.find(rms, framesPerSecond)
            val seconds = (frames.toLong() * HOP + FFT_SIZE - HOP).toDouble() / SAMPLE_RATE
            val fixed = FloatArray(SoundProfile.FIXED_SIZE)

            fun put(
                index: Int,
                value: Double,
            ) {
                fixed[index] = if (value.isFinite()) value.toFloat() else 0f
            }

            val mean = if (rms.isEmpty()) 0.0 else rms.average()
            put(SoundProfile.TEMPO, tempo.tempoBpm)
            put(SoundProfile.MEAN_LOUDNESS, mean)
            put(SoundProfile.LOUDNESS_VARIATION, sqrt(rms.sumOf { (it - mean) * (it - mean) } / max(1, rms.size)))
            put(SoundProfile.SPECTRAL_CENTROID, if (weightSum > 0.0) centroidSum / weightSum else 0.0)
            put(SoundProfile.BASS_RATIO, if (totalEnergy > 0.0) bassEnergy / totalEnergy else 0.0)
            put(SoundProfile.ONSET_DENSITY, if (seconds > 0.0) tempo.onsetCount / seconds else 0.0)
            put(SoundProfile.BEAT_STRENGTH, tempo.beatStrength)
            put(SoundProfile.ZERO_CROSSING_RATE, if (weightSum > 0.0) zeroCrossingSum / weightSum else 0.0)
            putBeatGrid(fixed, tempo, span)
            val chromaTotal = chroma.sum()
            for (c in chroma.indices) {
                put(
                    SoundProfile.CHROMA_FIRST + c,
                    if (chromaTotal >
                        0.0
                    ) {
                        chroma[c] / chromaTotal
                    } else {
                        0.0
                    },
                )
            }
            put(SoundProfile.MUSIC_START, frameCentre(span.musicStart, span))
            put(SoundProfile.INTRO_END, frameCentre(span.introEnd, span))
            put(SoundProfile.OUTRO_START, frameCentre(span.outroStart, span))
            put(SoundProfile.MUSIC_END, frameCentre(span.musicEnd, span))
            return SoundProfile(fixed, energyCurve.toArray())
        }

        private fun putBeatGrid(
            fixed: FloatArray,
            tempo: TempoResult,
            span: IntroOutro,
        ) {
            if (tempo.tempoBpm <= 0.0) return
            val period = 60.0 / tempo.tempoBpm
            // An onset shows up in the first frame whose window ends just after it, so beats are read at frame ends.
            var first = (tempo.beatOffsetFrames * HOP + FFT_SIZE) / SAMPLE_RATE
            val start = frameCentre(span.musicStart, span)
            if (first < start - BEAT_SLACK_SECONDS) first += ceil((start - first) / period) * period
            fixed[SoundProfile.FIRST_BEAT] = first.toFloat()
            fixed[SoundProfile.BEAT_TEMPO] = tempo.tempoBpm.toFloat()
            fixed[SoundProfile.TEMPO_CONFIDENCE] = tempo.confidence.toFloat()
            tempo.candidates.forEachIndexed { slot, (bpm, score) ->
                fixed[SoundProfile.CANDIDATES + 2 * slot] = bpm.toFloat()
                fixed[SoundProfile.CANDIDATES + 2 * slot + 1] = score.toFloat()
            }
        }

        private fun frameCentre(
            frame: Int,
            span: IntroOutro,
        ): Double = if (span === IntroOutro.NONE) 0.0 else ((frame + 1).toLong() * HOP).toDouble() / SAMPLE_RATE

        private fun binHz(): Double = SAMPLE_RATE.toDouble() / FFT_SIZE

        private fun chromaClass(bin: Int): Int {
            val hz = bin * SAMPLE_RATE.toDouble() / FFT_SIZE
            if (hz < CHROMA_LOW_HZ || hz > CHROMA_HIGH_HZ) return -1
            val semitone = (SEMITONES * ln(hz / REFERENCE_A_HZ) / ln(2.0)).roundToInt()
            return ((semitone + A_PITCH_CLASS) % SEMITONES + SEMITONES) % SEMITONES
        }
    }
}

private val BASS_FIRST = (BASS_LOW_HZ * FFT_SIZE / SAMPLE_RATE).roundToInt().coerceAtLeast(1)
private val BASS_LAST = (BASS_HIGH_HZ * FFT_SIZE / SAMPLE_RATE).toInt()
