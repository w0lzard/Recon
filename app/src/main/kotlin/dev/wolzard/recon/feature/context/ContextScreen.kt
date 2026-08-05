package dev.wolzard.recon.feature.context

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.wolzard.recon.designsystem.ChipKind
import dev.wolzard.recon.designsystem.StatusChip

/** Shown after a valid link is shared or pasted (D23). Skip starts with no context; Start sends the trimmed text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextScreen(state: ContextState, onIntent: (ContextIntent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {},
            navigationIcon = { IconButton(onClick = { onIntent(ContextIntent.Back) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            windowInsets = WindowInsets(0), // MainActivity's Scaffold already pads for the status bar
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("LINK", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            StatusChip(state.host, ChipKind.Neutral, Modifier.testTag("context_host"))
            Text(ContextCopy.TITLE, style = MaterialTheme.typography.titleLarge)
            Text(ContextCopy.HINT, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.text, onValueChange = { onIntent(ContextIntent.TextChanged(it)) },
                modifier = Modifier.fillMaxWidth().testTag("context_field"), shape = MaterialTheme.shapes.small,
                minLines = 3, maxLines = 6, label = { Text("Your question or note") },
                supportingText = { Text("${state.text.length}/300") },
                isError = state.error != null,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ContextCopy.SUGGESTIONS.forEach { s ->
                    SuggestionChip(
                        onClick = { onIntent(ContextIntent.Suggest(s)) }, label = { Text(s, style = MaterialTheme.typography.bodyMedium) },
                        shape = MaterialTheme.shapes.small,
                    )
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { onIntent(ContextIntent.Start) }, Modifier.fillMaxWidth().height(48.dp),
                enabled = !state.starting, shape = MaterialTheme.shapes.small,
            ) { Text("Start") }
            OutlinedButton(
                onClick = { onIntent(ContextIntent.Skip) }, Modifier.fillMaxWidth().height(48.dp),
                enabled = !state.starting, shape = MaterialTheme.shapes.small,
            ) { Text("Skip") }
        }
    }
}
