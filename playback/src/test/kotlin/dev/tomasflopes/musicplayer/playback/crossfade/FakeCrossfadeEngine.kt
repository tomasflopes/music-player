package dev.tomasflopes.musicplayer.playback.crossfade

/** Records what the playback engine tells a crossfade engine; a test plays the engine's part by calling [host]. */
internal class FakeCrossfadeEngine : CrossfadeEngine {
    lateinit var host: CrossfadeHost
    var invalidations = 0
    val playing = mutableListOf<Boolean>()
    var released = false

    override fun attach(host: CrossfadeHost) {
        this.host = host
    }

    override fun invalidate() {
        invalidations++
    }

    override fun onPlayingChanged(playing: Boolean) {
        this.playing += playing
    }

    override fun release() {
        released = true
    }
}
