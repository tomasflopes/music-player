package dev.tomasflopes.musicplayer

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import dagger.hilt.android.AndroidEntryPoint
import dev.tomasflopes.musicplayer.ui.art.ArtistImageHost
import dev.tomasflopes.musicplayer.ui.art.CoverArtLoader
import dev.tomasflopes.musicplayer.ui.art.LocalArtistImageHost
import dev.tomasflopes.musicplayer.ui.art.LocalCoverArtLoader
import dev.tomasflopes.musicplayer.ui.navigation.AppShell
import dev.tomasflopes.musicplayer.ui.shell.ShellViewModel
import dev.tomasflopes.musicplayer.ui.theme.MusicPlayerTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val shell: ShellViewModel by viewModels()

    @Inject lateinit var coverArtLoader: CoverArtLoader

    @Inject lateinit var artistImageHost: ArtistImageHost

    private val audioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val picker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri ?: return@registerForActivityResult
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            shell.folderPicked(uri.toString(), DocumentsContract.getTreeDocumentId(uri))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app has one dark theme, so the system bars are always drawn for a dark background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        audioPermission.launch(Manifest.permission.READ_MEDIA_AUDIO)
        setContent {
            MusicPlayerTheme {
                CompositionLocalProvider(
                    LocalCoverArtLoader provides coverArtLoader,
                    LocalArtistImageHost provides artistImageHost,
                ) {
                    AppShell(shell, onPickFolder = { picker.launch(null) })
                }
            }
        }
    }
}
