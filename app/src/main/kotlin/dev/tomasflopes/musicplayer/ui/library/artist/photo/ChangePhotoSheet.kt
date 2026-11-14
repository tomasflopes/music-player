package dev.tomasflopes.musicplayer.ui.library.artist.photo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomasflopes.musicplayer.core.artistimage.ArtistImageCandidate
import dev.tomasflopes.musicplayer.core.artistimage.CandidateResult
import dev.tomasflopes.musicplayer.core.model.Artist
import dev.tomasflopes.musicplayer.ui.art.LocalArtistImageHost
import dev.tomasflopes.musicplayer.ui.components.RemotePhoto
import dev.tomasflopes.musicplayer.ui.components.SettingRow
import dev.tomasflopes.musicplayer.ui.theme.ArtSize
import dev.tomasflopes.musicplayer.ui.theme.Spacing

private enum class SheetPage { MENU, ONLINE }

/**
 * The "Change photo" sheet: choose from other Deezer artists with the name, pick an image from the gallery, use the
 * generated image, or go back to automatic. The gallery picker needs no permission; the image is copied once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePhotoSheet(
    artist: Artist,
    onDismiss: () -> Unit,
    viewModel: ArtistPhotoViewModel = hiltViewModel(),
) {
    var page by rememberSaveable { mutableStateOf(SheetPage.MENU) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) viewModel.useGallery(artist.id, uri)
            onDismiss()
        }
    val pickImage = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    ModalBottomSheet(
        onDismissRequest = {
            viewModel.clearOnline()
            onDismiss()
        },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Spacing.s)) {
            when (page) {
                SheetPage.MENU ->
                    PhotoMenu(
                        onOnline = { page = SheetPage.ONLINE },
                        onGallery = pickImage,
                        onGenerated = {
                            viewModel.useGenerated(artist.id)
                            onDismiss()
                        },
                        onAutomatic = {
                            viewModel.useAutomatic(artist.id)
                            onDismiss()
                        },
                    )
                SheetPage.ONLINE ->
                    OnlinePhotoPicker(artist, viewModel) { url ->
                        viewModel.useOnline(artist.id, url)
                        viewModel.clearOnline()
                        onDismiss()
                    }
            }
        }
    }
}

@Composable
private fun PhotoMenu(
    onOnline: () -> Unit,
    onGallery: () -> Unit,
    onGenerated: () -> Unit,
    onAutomatic: () -> Unit,
) {
    Text(
        text = "Change photo",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
    )
    SettingRow("Choose an online photo", "Other artists with this name on Deezer", onOnline)
    SettingRow("Pick from gallery", "Use one of your own images", onGallery)
    SettingRow("Use generated image", "The album covers or the artist's initials, no online photo", onGenerated)
    SettingRow("Reset to automatic", "Let the app find a photo again", onAutomatic)
}

@Composable
private fun OnlinePhotoPicker(
    artist: Artist,
    viewModel: ArtistPhotoViewModel,
    onPick: (String) -> Unit,
) {
    val state by viewModel.online.collectAsStateWithLifecycle()
    LaunchedEffect(artist.id) { viewModel.loadOnline(artist.name) }
    Text(
        text = "Photos for \"${artist.name}\"",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
    )
    when (val shown = state) {
        OnlinePhotos.Idle, OnlinePhotos.Loading -> CircularProgressIndicator(Modifier.padding(Spacing.s))
        is OnlinePhotos.Loaded -> LoadedPhotos(shown.result, onPick, onRetry = { viewModel.loadOnline(artist.name) })
    }
}

@Composable
private fun LoadedPhotos(
    result: CandidateResult,
    onPick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when (result) {
        CandidateResult.Disabled -> Note("Online photos are off right now. Change this in Settings.")
        CandidateResult.Unavailable -> {
            Note("Could not reach Deezer.")
            TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = Spacing.xxs)) { Text("Try again") }
        }
        is CandidateResult.Found ->
            if (result.candidates.isEmpty()) {
                Note("No photos found for this name.")
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.s),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    items(result.candidates, key = { it.pictureUrl }) { Candidate(it, onPick) }
                }
            }
    }
}

@Composable
private fun Candidate(
    candidate: ArtistImageCandidate,
    onPick: (String) -> Unit,
) {
    val host = LocalArtistImageHost.current
    Column(
        modifier = Modifier.clickable { onPick(candidate.pictureUrl) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (host != null) {
            RemotePhoto(host, candidate.pictureUrl, Modifier.size(ArtSize.card).clip(CircleShape))
        }
        Text(
            text = candidate.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(ArtSize.card),
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
    )
}
