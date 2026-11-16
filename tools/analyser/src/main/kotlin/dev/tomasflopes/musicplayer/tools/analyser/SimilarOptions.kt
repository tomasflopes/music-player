package dev.tomasflopes.musicplayer.tools.analyser

import java.nio.file.Path
import java.nio.file.Paths

/** `analyser similar --profiles <jsonl> --seed "<path substring>" [--k 10]` (ADR 0073). */
data class SimilarOptions(
    val profiles: Path,
    val seed: String,
    val k: Int = DEFAULT_K,
) {
    companion object {
        const val DEFAULT_K = 10
        const val USAGE = "usage: analyser similar --profiles <profiles.jsonl> --seed \"<path substring>\" [--k 10]"

        private val known = setOf("profiles", "seed", "k")

        fun parse(args: List<String>): SimilarOptions {
            val pairs = args.chunked(2)
            if (pairs.any { it.size != 2 || !it[0].startsWith("--") }) usage("Options come as '--name value' pairs")
            val values = pairs.associate { (name, value) -> name.removePrefix("--") to value }
            val unknown = values.keys - known
            if (unknown.isNotEmpty()) usage("Unknown option --${unknown.first()}")
            val k =
                values["k"]?.let { it.toIntOrNull()?.takeIf { n -> n > 0 } ?: usage("--k must be a positive number") }
            return SimilarOptions(
                profiles = Paths.get(values["profiles"] ?: usage("--profiles is required")),
                seed = values["seed"] ?: usage("--seed is required"),
                k = k ?: DEFAULT_K,
            )
        }

        private fun usage(problem: String): Nothing = throw UsageException("$problem\n$USAGE")
    }
}
