package dev.wolzard.recon.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.wolzard.recon.designsystem.ChipKind
import dev.wolzard.recon.designsystem.StatusChip
import dev.wolzard.recon.designsystem.StatusIcons
import dev.wolzard.recon.designsystem.Symbols
import dev.wolzard.recon.feature.home.JobRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(state: EntryState, onIntent: (EntryIntent) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(state.entry?.name ?: "", maxLines = 1, style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = { onIntent(EntryIntent.Back) }) { Icon(Symbols.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            windowInsets = WindowInsets(0),
        )
        val e = state.entry
        if (e == null) {
            if (state.loaded) Text("This entry is gone.", Modifier.padding(16.dp), color = muted, style = MaterialTheme.typography.bodyLarge)
            return
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(typeLabel(e.type), ChipKind.Neutral)
                        StatusChip(e.status, statusKind(e.status))
                        e.stars?.let { Text("★ ${stars(it)}", color = muted, style = MaterialTheme.typography.labelSmall) }
                    }
                    if (e.summary.isNotBlank()) Text(e.summary, style = MaterialTheme.typography.bodyLarge)
                    e.primaryLink?.let { link ->
                        Button(onClick = { onIntent(EntryIntent.OpenLink(link)) }, Modifier.fillMaxWidth().height(48.dp).testTag("entry_link"), shape = MaterialTheme.shapes.small) {
                            Icon(StatusIcons.OpenInNew, contentDescription = null, Modifier.size(18.dp))
                            Text("Open link", Modifier.padding(start = 8.dp))
                        }
                    }
                    val also = e.aliases.filter { it != e.name.lowercase() }
                    if (also.isNotEmpty()) Text("Also known as ${also.joinToString(", ")}", color = muted, style = MaterialTheme.typography.bodySmall)
                    if (e.needsAttention) Text(e.attentionReason ?: "Needs attention", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("entry_attention"))
                }
                HorizontalDivider(color = line)
                Text("VIDEOS · ${state.videos.size}", Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp), color = muted, style = MaterialTheme.typography.labelSmall)
            }
            items(state.videos, key = { it.id }) { r ->
                JobRow(r) { onIntent(EntryIntent.OpenVideo(r.id)) }
                HorizontalDivider(color = line)
            }
        }
    }
}
