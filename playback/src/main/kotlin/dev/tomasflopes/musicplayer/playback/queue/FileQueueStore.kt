package dev.tomasflopes.musicplayer.playback.queue

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** The saved queue as one file, replaced whole through a temporary one so a crash never leaves half (ADR 0036). */
class FileQueueStore(
    private val file: File,
) : QueueStore {
    override fun load(): SavedQueue? = runCatching { QueueCodec.decode(file.readBytes()) }.getOrNull()

    override fun save(saved: SavedQueue?) {
        if (saved == null) {
            file.delete()
            return
        }
        file.absoluteFile.parentFile?.mkdirs()
        val temporary = File(file.absoluteFile.parentFile, "${file.name}.tmp")
        temporary.writeBytes(QueueCodec.encode(saved))
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }
}
