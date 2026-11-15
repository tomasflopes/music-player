package dev.tomasflopes.musicplayer.tools.analyser

import dev.tomasflopes.musicplayer.core.analysis.BenchSample
import java.nio.file.Path
import java.nio.file.Paths

/** The command line was wrong; [message] is what to tell the owner. */
class UsageException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** `analyser bench --root <music folder> [--sample 60] [--survey 500] [--seed N] [--threads N]`. */
data class BenchOptions(
    val root: Path,
    val sample: Int = DEFAULT_SAMPLE,
    val survey: Int = DEFAULT_SURVEY,
    val seed: Long = BenchSample.DEFAULT_SEED,
    val threads: Int = Runtime.getRuntime().availableProcessors(),
) {
    companion object {
        const val DEFAULT_SAMPLE = 60
        const val DEFAULT_SURVEY = 500
        const val USAGE = "usage: analyser bench --root <folder> [--sample 60] [--survey 500] [--seed N] [--threads N]"

        private val known = setOf("root", "sample", "survey", "seed", "threads")

        fun parse(args: List<String>): BenchOptions {
            val values = pairs(args)
            val unknown = values.keys - known
            if (unknown.isNotEmpty()) usage("Unknown option --${unknown.first()}")
            val root = values["root"] ?: usage("--root is required")
            return BenchOptions(
                root = Paths.get(root),
                sample = positive(values, "sample", DEFAULT_SAMPLE),
                survey = positive(values, "survey", DEFAULT_SURVEY),
                seed = values["seed"]?.toLongOrNull() ?: BenchSample.DEFAULT_SEED,
                threads = positive(values, "threads", Runtime.getRuntime().availableProcessors()),
            )
        }

        private fun pairs(args: List<String>): Map<String, String> {
            val pairs = args.chunked(2)
            if (pairs.any { it.size != 2 || !it[0].startsWith("--") }) usage("Options come as '--name value' pairs")
            return pairs.associate { (name, value) -> name.removePrefix("--") to value }
        }

        private fun positive(
            values: Map<String, String>,
            name: String,
            default: Int,
        ): Int {
            val raw = values[name] ?: return default
            return raw.toIntOrNull()?.takeIf { it > 0 } ?: usage("--$name must be a positive number")
        }

        private fun usage(problem: String): Nothing = throw UsageException("$problem\n$USAGE")
    }
}
