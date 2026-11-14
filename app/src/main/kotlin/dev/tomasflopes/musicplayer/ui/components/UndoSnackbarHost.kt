package dev.tomasflopes.musicplayer.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The one snackbar of the shared undo (ADR 0046). It shows the pending offer with an "Undo" action: a tap runs it,
 * and when the snackbar goes away any other way the offer expires. A newer offer replaces the one on show.
 * Only one host at a time should be [enabled]: the sheet over the app has its own, so the shell's steps aside.
 */
@Composable
fun UndoSnackbarHost(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    viewModel: UndoViewModel = hiltViewModel(),
) {
    val host = remember { SnackbarHostState() }
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val offer = pending
    LaunchedEffect(offer?.id, enabled) {
        if (offer == null || !enabled) return@LaunchedEffect
        val result =
            host.showSnackbar(
                message = offer.message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Long,
            )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.undo(offer.id)
            SnackbarResult.Dismissed -> viewModel.expire(offer.id)
        }
    }
    SnackbarHost(host, modifier)
}
