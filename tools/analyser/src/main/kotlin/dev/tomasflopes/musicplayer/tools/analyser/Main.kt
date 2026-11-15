package dev.tomasflopes.musicplayer.tools.analyser

import dev.tomasflopes.musicplayer.core.analysis.HandBuiltProfilerV1
import kotlin.system.exitProcess

private const val COMMANDS = "commands: bench, analyse"

/** The Mac analyser (ADR 0003, 0053, 0065): `bench` times the decoders, `analyse` profiles the library. */
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
            else -> throw UsageException("$COMMANDS\n${BenchOptions.USAGE}\n${AnalyseOptions.USAGE}")
        }
    } catch (e: UsageException) {
        System.err.println(e.message)
        2
    }
