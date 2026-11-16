package dev.tomasflopes.musicplayer.tools.analyser

import dev.tomasflopes.musicplayer.core.analysis.ExportFormatException
import dev.tomasflopes.musicplayer.core.analysis.ExportRecord
import dev.tomasflopes.musicplayer.core.analysis.ProfileExportCodec
import dev.tomasflopes.musicplayer.core.analysis.RawSoundProfile
import dev.tomasflopes.musicplayer.core.analysis.SoundVectorizer
import dev.tomasflopes.musicplayer.core.analysis.config.SoundVectorizerConfig
import dev.tomasflopes.musicplayer.core.model.sound.SoundNeighbours
import java.io.IOException
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * `analyser similar` (ADR 0073): the songs that sound closest to a seed, from a Mac export. It builds the space with
 * the same [SoundVectorizer] and the same nearest search as the app, so what it prints is what Discover will see.
 */
class SimilarCommand(
    private val out: PrintStream,
) {
    fun run(options: SimilarOptions): Int {
        val lines =
            try {
                Files.readAllLines(options.profiles, StandardCharsets.UTF_8)
            } catch (e: IOException) {
                throw UsageException("Cannot read ${options.profiles}: ${e.message}", e)
            }
        val contents =
            try {
                ProfileExportCodec.read(lines)
            } catch (e: ExportFormatException) {
                throw UsageException("${options.profiles} is not a profile export: ${e.message}", e)
            }
        out.print(render(contents.records, options))
        return 0
    }

    companion object {
        /** The text for [options]; a song's id in the space is its index in [records]. */
        fun render(
            records: List<ExportRecord>,
            options: SimilarOptions,
            config: SoundVectorizerConfig = SoundVectorizerConfig(),
        ): String {
            val seed = SeedMatcher.find(records.map { it.path }, options.seed)
            val raw = records.mapIndexed { index, record -> RawSoundProfile(index.toLong(), record.profile.fixed) }
            val space = SoundVectorizer(config).vectorize(raw)
            val seedRow = space.indexOf(seed.toLong())
            if (seedRow < 0) {
                throw UsageException("${records[seed].path} has no usable sound vector (a feature is not a number)")
            }
            val keyOf = { id: Long -> SoundNeighbours.fileKey(records[id.toInt()].path) }
            val found = SoundNeighbours.nearest(space, seedRow, options.k, keyOf)
            val rows = found.map { SimilarRow(records[it.songId.toInt()].path, it.distance, space.vector(it.row)) }
            return SimilarFormat.format(records[seed].path, space.vector(seedRow), rows)
        }
    }
}
