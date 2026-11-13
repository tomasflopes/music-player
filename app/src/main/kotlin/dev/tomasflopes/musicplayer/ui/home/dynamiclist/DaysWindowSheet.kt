package dev.tomasflopes.musicplayer.ui.home.dynamiclist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import dev.tomasflopes.musicplayer.ui.theme.Spacing

/** What the chip says: "Last 20 days". */
internal fun daysLabel(days: Int): String = if (days == 1) "Last day" else "Last $days days"

/**
 * The quick options plus a custom number. [onQuickOption] and [onCustom] return whether the value was applied;
 * the sheet closes itself only then, so a rejected custom value stays open with its error.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DaysWindowSheet(
    window: DaysWindow,
    onQuickOption: (Int) -> Boolean,
    onCustom: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var custom by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text("Show songs added in the last", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                window.quickOptions.forEach { days ->
                    FilterChip(
                        selected = days == window.days,
                        onClick = { if (onQuickOption(days)) onDismiss() },
                        label = { Text("$days days") },
                    )
                }
            }
            val errorText: (@Composable () -> Unit)? =
                window.error?.let { error -> { Text(error.message(window.range)) } }
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it },
                label = { Text("Custom number of days") },
                singleLine = true,
                isError = window.error != null,
                supportingText = errorText,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { if (onCustom(custom)) onDismiss() },
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s),
            ) { Text("Apply") }
        }
    }
}

private fun DaysError.message(range: IntRange): String =
    when (this) {
        DaysError.NOT_A_NUMBER -> "Enter a whole number of days."
        DaysError.OUT_OF_RANGE -> "Enter a number from ${range.first} to ${range.last}."
    }
