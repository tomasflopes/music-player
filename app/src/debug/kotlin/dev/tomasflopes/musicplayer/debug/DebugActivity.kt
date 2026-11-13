package dev.tomasflopes.musicplayer.debug

import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.tomasflopes.musicplayer.ui.theme.MusicPlayerTheme

@AndroidEntryPoint
class DebugActivity : ComponentActivity() {
    private val viewModel: DebugViewModel by viewModels()

    private val picker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri ?: return@registerForActivityResult
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            viewModel.folderPicked(uri.toString(), DocumentsContract.getTreeDocumentId(uri))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MusicPlayerTheme { DebugScreen(viewModel) { picker.launch(null) } } }
    }
}
