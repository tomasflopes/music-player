package dev.tomasflopes.musicplayer.radio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** A one-line note for the owner that needs no action, such as "No sound profile for this song yet". */
fun interface ShortMessages {
    fun show(text: String)
}

/** [ShortMessages] as a short toast, which also shows over the Now playing sheet and the song menu. */
@Singleton
class ToastMessages
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ShortMessages {
        private val main = Handler(Looper.getMainLooper())

        override fun show(text: String) {
            main.post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
        }
    }
