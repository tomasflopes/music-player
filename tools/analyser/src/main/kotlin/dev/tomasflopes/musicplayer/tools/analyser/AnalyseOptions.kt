package dev.tomasflopes.musicplayer.tools.analyser

import java.nio.file.Path
import java.nio.file.Paths

/** `analyser analyse --root <music folder> --out <profiles.jsonl> [--threads N]` (ADR 0065). */
data class AnalyseOptions(
    val root: Path,
    val out: Path,
    val threads: Int = Runtime.getRuntime().availableProcessors(),
) {
    companion object {
        const val USAGE = "usage: analyser analyse --root <folder> --out <profiles.jsonl> [--threads N]"

        private val known = setOf("root", "out", "threads")

        fun parse(args: List<String>): AnalyseOptions {
            val pairs = args.chunked(2)
            if (pairs.any { it.size != 2 || !it[0].startsWith("--") }) usage("Options come as '--name value' pairs")
            val values = pairs.associate { (name, value) -> name.removePrefix("--") to value }
            val unknown = values.keys - known
            if (unknown.isNotEmpty()) usage("Unknown option --${unknown.first()}")
            val threads =
                values["threads"]?.let {
                    it.toIntOrNull()?.takeIf { n -> n > 0 }
                        ?: usage("--threads must be a positive number")
                }
            return AnalyseOptions(
                root = Paths.get(values["root"] ?: usage("--root is required")),
                out = Paths.get(values["out"] ?: usage("--out is required")),
                threads = threads ?: Runtime.getRuntime().availableProcessors(),
            )
        }

        private fun usage(problem: String): Nothing = throw UsageException("$problem\n$USAGE")
    }
}
