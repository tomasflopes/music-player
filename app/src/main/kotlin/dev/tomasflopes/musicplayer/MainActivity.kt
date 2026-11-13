package dev.tomasflopes.musicplayer

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.tomasflopes.musicplayer.ui.library.LibraryScreen
import dev.tomasflopes.musicplayer.ui.library.LibraryViewModel
import dev.tomasflopes.musicplayer.ui.theme.MusicPlayerTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: LibraryViewModel by viewModels()

    private val audioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val picker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri ?: return@registerForActivityResult
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            viewModel.folderPicked(uri.toString(), DocumentsContract.getTreeDocumentId(uri))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioPermission.launch(Manifest.permission.READ_MEDIA_AUDIO)
        setContent {
            MusicPlayerTheme {
                LibraryScreen(onPickFolder = { picker.launch(null) }, viewModel = viewModel)
            }
        }
    }
}
