package dev.tomasflopes.musicplayer.ui.home.sections

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import dev.tomasflopes.musicplayer.ui.home.HomeSection
import dev.tomasflopes.musicplayer.ui.home.HomeViewModel
import dev.tomasflopes.musicplayer.ui.theme.Spacing
import javax.inject.Inject

/** Plays the whole library shuffled. It asks the same [HomeViewModel] as Home (one instance per back stack entry). */
class ShuffleAllSection
    @Inject
    constructor() : HomeSection {
        override val id = "shuffle_all"
        override val order = 0

        @Composable
        override fun Content(onNavigate: (String) -> Unit) {
            val viewModel: HomeViewModel = hiltViewModel()
            ShuffleAllButton(viewModel::shuffleAll)
        }
    }

@Composable
private fun ShuffleAllButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s),
    ) {
        Icon(Icons.Default.Shuffle, contentDescription = null)
        Spacer(Modifier.width(Spacing.xxs))
        Text("Shuffle all")
    }
}
