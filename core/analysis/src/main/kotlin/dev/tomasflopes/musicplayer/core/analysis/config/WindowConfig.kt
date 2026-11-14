package dev.tomasflopes.musicplayer.core.analysis.config

/** Where the analysed windows sit: [centres] as fractions of the song, each [lengthMs] long (phase 3, §7.5). */
data class WindowConfig(
    val centres: List<Double> = listOf(0.25, 0.50, 0.75),
    val lengthMs: Long = 20_000L,
)
