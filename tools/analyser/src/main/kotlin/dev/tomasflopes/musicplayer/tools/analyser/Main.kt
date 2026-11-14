package dev.tomasflopes.musicplayer.tools.analyser

import kotlin.system.exitProcess

private const val COMMANDS = "commands: bench"

/** The Mac analyser (ADR 0003, 0053). Slice 1 has one command, `bench`; `analyse` arrives with slice 2. */
fun main(args: Array<String>) {
    exitProcess(execute(args.toList()))
}

internal fun execute(args: List<String>): Int =
    try {
        when (args.firstOrNull()) {
            "bench" -> BenchCommand(FfmpegPcmSource(), System.out).run(BenchOptions.parse(args.drop(1)))
            else -> throw UsageException("$COMMANDS\n${BenchOptions.USAGE}")
        }
    } catch (e: UsageException) {
        System.err.println(e.message)
        2
    }
