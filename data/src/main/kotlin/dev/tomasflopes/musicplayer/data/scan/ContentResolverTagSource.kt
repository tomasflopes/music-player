package dev.tomasflopes.musicplayer.data.scan

import android.content.ContentResolver
import android.net.Uri
import dev.tomasflopes.musicplayer.core.tags.TagSource
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Reads a file's bytes through its MediaStore content URI (ADR 0005: faster than the raw path on Android 17,
 * and the only path that always works). Decision point: a file-path source can be added beside it.
 */
class ContentResolverTagSource(
    private val resolver: ContentResolver,
    private val file: MediaFile,
) : TagSource {
    override val fileName: String get() = file.path.substringAfterLast('/')

    override fun openStream(): InputStream =
        resolver.openInputStream(Uri.parse(file.contentUri)) ?: throw FileNotFoundException(file.contentUri)

    override fun readTail(byteCount: Int): ByteArray? =
        resolver.openFileDescriptor(Uri.parse(file.contentUri), "r")?.use { descriptor ->
            if (descriptor.statSize < byteCount) return null
            FileInputStream(descriptor.fileDescriptor).use { stream ->
                stream.channel.position(descriptor.statSize - byteCount)
                stream.readNBytes(byteCount).takeIf { it.size == byteCount }
            }
        }
}
