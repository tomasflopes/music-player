package dev.tomasflopes.musicplayer.tools.analyser

import dev.tomasflopes.musicplayer.core.analysis.HandBuiltProfilerV1
import kotlin.system.exitProcess

private const val COMMANDS = "commands: bench, analyse, similar"

/**
 * The Mac analyser (ADR 0003, 0053, 0065, 0073): `bench` times the decoders, `analyse` profiles the library,
 * `similar` lists the songs closest to a seed.
 */
fun main(args: Array<String>) {
    exitProcess(execute(args.toList()))
}

internal fun execute(args: List<String>): Int =
    try {
        when (args.firstOrNull()) {
            "bench" -> BenchCommand(FfmpegPcmSource(), System.out).run(BenchOptions.parse(args.drop(1)))
            "analyse" ->
                AnalyseCommand(
                    FfmpegPcmSource(),
                    HandBuiltProfilerV1(),
                    System.out,
                ).run(AnalyseOptions.parse(args.drop(1)))
            "similar" -> SimilarCommand(System.out).run(SimilarOptions.parse(args.drop(1)))
            else ->
                throw UsageException(
                    "$COMMANDS\n${BenchOptions.USAGE}\n${AnalyseOptions.USAGE}\n${SimilarOptions.USAGE}",
                )
        }
    } catch (e: UsageException) {
        System.err.println(e.message)
        2
    }
