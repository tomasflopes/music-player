package dev.tomasflopes.musicplayer.playback.queue

import dev.tomasflopes.musicplayer.core.model.ArtistId
import dev.tomasflopes.musicplayer.core.model.SongId
import dev.tomasflopes.musicplayer.core.model.SongRef
import dev.tomasflopes.musicplayer.core.model.log.PlaySource
import java.nio.ByteBuffer
import java.util.zip.CRC32

/**
 * The saved queue as bytes (ADR 0036). Compact: each distinct song is written once as a (song id, artist id) pair and
 * the entries and the original order point at it by number, so 16 000 songs take about 400 KB.
 *
 * Layout, big-endian: magic, version, refs (count, then song id and artist id each), the source names used (count,
 * then length-prefixed UTF-8; permanent `wire` values, so enum order may change), entries (count, then ref number,
 * source number and manual flag each), original order (count, then ref numbers), current index, shuffled flag,
 * repeat mode, position, and a CRC-32 of everything before it. Version 2 (ADR 0042) adds the playlist table (count,
 * then the ids) and a playlist number on each entry (-1 for none). Version 3 (ADR 0076) adds the same for mixes: a mix
 * table after the playlist table and a mix number after the playlist number on each entry. Version 4 (ADR 0078) adds
 * the solo seeds the same way: a table of song ids after the mix table and a number after the mix number on each
 * entry (-1 for none). A mix seed is not stored: it is the entry's mix id. Version 1 to 3 files are ignored.
 * Anything that does not match decodes to `null`: a state from another [VERSION] is ignored, never guessed at.
 */
object QueueCodec {
    const val VERSION = 4

    private const val MAGIC = 0x4D505153
    private const val CHECKSUM_BYTES = 4
    private const val COUNT_BYTES = 4
    private const val HEADER_BYTES = 8
    private const val REF_BYTES = 16
    private const val ENTRY_BYTES = 12
    private const val NO_PLAYLIST: Short = -1
    private const val NO_MIX: Short = -1
    private const val NO_SOLO: Short = -1
    private const val PLAYLIST_BYTES = 8
    private const val MIX_BYTES = 8
    private const val SOLO_BYTES = 8
    private const val ORIGINAL_BYTES = 4
    private const val TAIL_BYTES = 4 + 1 + 1 + 8

    fun encode(saved: SavedQueue): ByteArray {
        val snapshot = saved.snapshot
        val tables = tables(snapshot)
        val size = encodedSize(snapshot, tables)
        val buffer = ByteBuffer.allocate(size)
        writeHeader(buffer, tables.refs)
        writeSources(buffer, tables.names)
        writeIds(buffer, tables.playlists)
        writeIds(buffer, tables.mixes)
        writeIds(buffer, tables.solos)
        writeEntries(buffer, snapshot, tables)
        writeTail(buffer, snapshot, saved.positionMs, tables.refs)
        buffer.putInt(checksum(buffer.array(), buffer.position()))
        return buffer.array()
    }

    private data class EncodeTables(
        val refs: LinkedHashMap<SongRef, Int>,
        val sources: LinkedHashMap<PlaySource, Int>,
        val playlists: LinkedHashMap<Long, Int>,
        val mixes: LinkedHashMap<Long, Int>,
        val solos: LinkedHashMap<Long, Int>,
        val names: List<ByteArray>,
    )

    private fun tables(snapshot: QueueSnapshot): EncodeTables {
        val refs = LinkedHashMap<SongRef, Int>()
        (snapshot.entries.map { it.ref } + snapshot.originalOrder)
            .forEach { refs.getOrPut(it) { refs.size } }
        val sources = LinkedHashMap<PlaySource, Int>()
        snapshot.entries.forEach { sources.getOrPut(it.source) { sources.size } }
        val playlists = LinkedHashMap<Long, Int>()
        snapshot.entries.forEach { entry ->
            entry.playlistId?.let { playlists.getOrPut(it) { playlists.size } }
        }
        val mixes = LinkedHashMap<Long, Int>()
        snapshot.entries.forEach { entry ->
            entry.mixId?.let { mixes.getOrPut(it) { mixes.size } }
        }
        val solos = LinkedHashMap<Long, Int>()
        snapshot.entries.forEach { entry ->
            entry.soloSeed?.let { solos.getOrPut(it) { solos.size } }
        }
        return EncodeTables(
            refs,
            sources,
            playlists,
            mixes,
            solos,
            sources.keys.map { it.wire.toByteArray() },
        )
    }

    private fun encodedSize(
        snapshot: QueueSnapshot,
        tables: EncodeTables,
    ): Int =
        HEADER_BYTES + COUNT_BYTES + tables.refs.size * REF_BYTES + COUNT_BYTES +
            tables.names.sumOf { 1 + it.size } + COUNT_BYTES + tables.playlists.size * PLAYLIST_BYTES +
            COUNT_BYTES + tables.mixes.size * MIX_BYTES + COUNT_BYTES + tables.solos.size * SOLO_BYTES +
            COUNT_BYTES + snapshot.entries.size * ENTRY_BYTES +
            COUNT_BYTES + snapshot.originalOrder.size * ORIGINAL_BYTES +
            TAIL_BYTES + CHECKSUM_BYTES

    private fun writeHeader(
        buffer: ByteBuffer,
        refs: LinkedHashMap<SongRef, Int>,
    ) {
        buffer.putInt(MAGIC).putInt(VERSION).putInt(refs.size)
        refs.keys.forEach { buffer.putLong(it.songId.value).putLong(it.mainArtistId.value) }
    }

    private fun writeSources(
        buffer: ByteBuffer,
        names: List<ByteArray>,
    ) {
        buffer.putInt(names.size)
        names.forEach { buffer.put(it.size.toByte()).put(it) }
    }

    private fun writeIds(
        buffer: ByteBuffer,
        ids: LinkedHashMap<Long, Int>,
    ) {
        buffer.putInt(ids.size)
        ids.keys.forEach { buffer.putLong(it) }
    }

    private fun writeEntries(
        buffer: ByteBuffer,
        snapshot: QueueSnapshot,
        tables: EncodeTables,
    ) {
        buffer.putInt(snapshot.entries.size)
        snapshot.entries.forEach { entry ->
            buffer
                .putInt(tables.refs.getValue(entry.ref))
                .put(tables.sources.getValue(entry.source).toByte())
                .put(flag(entry.manual))
                .putShort(entry.playlistNumber(tables.playlists))
                .putShort(entry.mixId?.let { tables.mixes.getValue(it).toShort() } ?: NO_MIX)
                .putShort(entry.soloSeed?.let { tables.solos.getValue(it).toShort() } ?: NO_SOLO)
        }
    }

    private fun QueueEntry.playlistNumber(ids: Map<Long, Int>): Short =
        playlistId?.let { ids.getValue(it).toShort() } ?: NO_PLAYLIST

    private fun writeTail(
        buffer: ByteBuffer,
        snapshot: QueueSnapshot,
        positionMs: Long,
        refs: Map<SongRef, Int>,
    ) {
        buffer.putInt(snapshot.originalOrder.size)
        snapshot.originalOrder.forEach { buffer.putInt(refs.getValue(it)) }
        buffer
            .putInt(snapshot.index)
            .put(flag(snapshot.shuffled))
            .put(repeatCode(snapshot.repeatMode))
        buffer.putLong(positionMs)
    }

    /** The saved queue, or `null` for anything that is empty, damaged, cut short or from another version. */
    fun decode(bytes: ByteArray): SavedQueue? = runCatching { parse(bytes) }.getOrNull()

    private fun parse(bytes: ByteArray): SavedQueue {
        val body = bytes.size - CHECKSUM_BYTES
        require(body >= HEADER_BYTES) { "too short" }
        val buffer = ByteBuffer.wrap(bytes)
        require(buffer.getInt(body) == checksum(bytes, body)) { "checksum" }
        buffer.limit(body)
        require(buffer.int == MAGIC && buffer.int == VERSION) { "magic or version" }
        val refs = readRefs(buffer)
        val sources = readSources(buffer)
        val playlists = List(count(buffer, PLAYLIST_BYTES)) { buffer.long }
        val mixes = List(count(buffer, MIX_BYTES)) { buffer.long }
        val solos = List(count(buffer, SOLO_BYTES)) { buffer.long }
        val entries = readEntries(buffer, refs, sources, Seeds(playlists, mixes, solos))
        val original = List(count(buffer, ORIGINAL_BYTES)) { refs[buffer.int] }
        val index = buffer.int
        require(index in -1..entries.lastIndex) { "index" }
        val shuffled = readFlag(buffer.get())
        val repeat = repeatMode(buffer.get())
        val position = buffer.long
        require(position >= 0 && !buffer.hasRemaining()) { "tail" }
        return SavedQueue(QueueSnapshot(entries, original, index, shuffled, repeat), position)
    }

    private fun readRefs(buffer: ByteBuffer): List<SongRef> =
        List(count(buffer, REF_BYTES)) { SongRef(SongId(buffer.long), ArtistId(buffer.long)) }

    private fun readSources(buffer: ByteBuffer): List<PlaySource> =
        List(count(buffer, 1)) {
            val wire = String(ByteArray(buffer.get().toInt() and 0xFF).also(buffer::get))
            requireNotNull(PlaySource.entries.firstOrNull { it.wire == wire }) { "source $wire" }
        }

    private fun readEntries(
        buffer: ByteBuffer,
        refs: List<SongRef>,
        sources: List<PlaySource>,
        seeds: Seeds,
    ): List<QueueEntry> =
        List(count(buffer, ENTRY_BYTES)) {
            val ref = refs[buffer.int]
            val source = sources[buffer.get().toInt()]
            val manual = readFlag(buffer.get())
            val playlist = buffer.short
            val mix = buffer.short
            val solo = buffer.short
            QueueEntry(
                ref,
                source,
                manual,
                if (playlist == NO_PLAYLIST) null else seeds.playlists[playlist.toInt()],
                if (mix == NO_MIX) null else seeds.mixes[mix.toInt()],
                if (solo == NO_SOLO) null else seeds.solos[solo.toInt()],
            )
        }

    /** The id tables of a saved queue that entries point into by number. */
    private class Seeds(
        val playlists: List<Long>,
        val mixes: List<Long>,
        val solos: List<Long>,
    )

    /** A count that the bytes left could hold; a damaged count cannot make a huge list. */
    private fun count(
        buffer: ByteBuffer,
        bytesEach: Int,
    ): Int {
        val count = buffer.int
        require(count >= 0 && count.toLong() * bytesEach <= buffer.remaining()) { "count" }
        return count
    }

    private fun checksum(
        bytes: ByteArray,
        length: Int,
    ): Int = CRC32().apply { update(bytes, 0, length) }.value.toInt()

    private fun flag(on: Boolean): Byte = if (on) 1 else 0

    private fun readFlag(value: Byte): Boolean {
        require(value == 0.toByte() || value == 1.toByte()) { "flag" }
        return value == 1.toByte()
    }

    private fun repeatCode(mode: RepeatMode): Byte =
        when (mode) {
            RepeatMode.OFF -> 0
            RepeatMode.ALL -> 1
            RepeatMode.ONE -> 2
        }

    private fun repeatMode(code: Byte): RepeatMode =
        when (code.toInt()) {
            0 -> RepeatMode.OFF
            1 -> RepeatMode.ALL
            2 -> RepeatMode.ONE
            else -> error("repeat $code")
        }
}
