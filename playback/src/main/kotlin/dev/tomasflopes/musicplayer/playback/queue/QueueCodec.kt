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
 * then the ids) and a playlist number on each entry (-1 for none); version 1 files are ignored.
 * Anything that does not match decodes to `null`: a state from another [VERSION] is ignored, never guessed at.
 */
object QueueCodec {
    const val VERSION = 2

    private const val MAGIC = 0x4D505153
    private const val CHECKSUM_BYTES = 4
    private const val HEADER_BYTES = 8
    private const val REF_BYTES = 16
    private const val ENTRY_BYTES = 8
    private const val NO_PLAYLIST: Short = -1
    private const val PLAYLIST_BYTES = 8
    private const val ORIGINAL_BYTES = 4
    private const val TAIL_BYTES = 4 + 1 + 1 + 8

    fun encode(saved: SavedQueue): ByteArray {
        val snapshot = saved.snapshot
        val refs = LinkedHashMap<SongRef, Int>()
        (snapshot.entries.map { it.ref } + snapshot.originalOrder).forEach { refs.getOrPut(it) { refs.size } }
        val sources = LinkedHashMap<PlaySource, Int>()
        snapshot.entries.forEach { sources.getOrPut(it.source) { sources.size } }
        val playlists = LinkedHashMap<Long, Int>()
        snapshot.entries.forEach { entry -> entry.playlistId?.let { playlists.getOrPut(it) { playlists.size } } }
        val names = sources.keys.map { it.wire.toByteArray() }
        val size =
            HEADER_BYTES + 4 + refs.size * REF_BYTES + 4 + names.sumOf { 1 + it.size } +
                4 + playlists.size * PLAYLIST_BYTES + 4 +
                snapshot.entries.size * ENTRY_BYTES + 4 + snapshot.originalOrder.size * ORIGINAL_BYTES +
                TAIL_BYTES + CHECKSUM_BYTES
        val buffer = ByteBuffer.allocate(size)
        buffer.putInt(MAGIC).putInt(VERSION).putInt(refs.size)
        refs.keys.forEach { buffer.putLong(it.songId.value).putLong(it.mainArtistId.value) }
        buffer.putInt(names.size)
        names.forEach { buffer.put(it.size.toByte()).put(it) }
        buffer.putInt(playlists.size)
        playlists.keys.forEach { buffer.putLong(it) }
        buffer.putInt(snapshot.entries.size)
        snapshot.entries.forEach {
            buffer.putInt(refs.getValue(it.ref)).put(sources.getValue(it.source).toByte()).put(flag(it.manual))
            buffer.putShort(it.playlistId?.let { id -> playlists.getValue(id).toShort() } ?: NO_PLAYLIST)
        }
        buffer.putInt(snapshot.originalOrder.size)
        snapshot.originalOrder.forEach { buffer.putInt(refs.getValue(it)) }
        buffer.putInt(snapshot.index).put(flag(snapshot.shuffled)).put(repeatCode(snapshot.repeatMode))
        buffer.putLong(saved.positionMs)
        buffer.putInt(checksum(buffer.array(), buffer.position()))
        return buffer.array()
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
        val entries = readEntries(buffer, refs, sources, playlists)
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
        playlists: List<Long>,
    ): List<QueueEntry> =
        List(count(buffer, ENTRY_BYTES)) {
            val ref = refs[buffer.int]
            val source = sources[buffer.get().toInt()]
            val manual = readFlag(buffer.get())
            val playlist = buffer.short
            QueueEntry(ref, source, manual, if (playlist == NO_PLAYLIST) null else playlists[playlist.toInt()])
        }

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
