package dev.tomasflopes.musicplayer.core.analysis

import java.text.Normalizer
import java.util.Random

/**
 * The fixed-seed song sample of the timing spike. The Mac and the phone both sort the NFC-normalised paths (relative
 * to the music root) and run the same shuffle, so on a byte-for-byte copy of the library they time the same songs.
 */
object BenchSample {
    const val DEFAULT_SEED = 20_261_114L

    /** Up to [count] of [relativePaths] (NFC-normalised, sorted, without duplicates), chosen by [seed]. */
    fun pick(
        relativePaths: Collection<String>,
        count: Int,
        seed: Long = DEFAULT_SEED,
    ): List<String> {
        val pool =
            relativePaths
                .map { Normalizer.normalize(it, Normalizer.Form.NFC) }
                .distinct()
                .sorted()
                .toMutableList()
        if (count >= pool.size) return pool
        val random = Random(seed)
        for (i in 0 until count) {
            val j = i + random.nextInt(pool.size - i)
            val taken = pool[j]
            pool[j] = pool[i]
            pool[i] = taken
        }
        return pool.subList(0, count).sorted()
    }
}
