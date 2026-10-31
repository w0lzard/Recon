package dev.wolzard.recon.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wolzard.recon.core.model.Entry
import dev.wolzard.recon.designsystem.Mono
import dev.wolzard.recon.designsystem.Reticle
import dev.wolzard.recon.designsystem.StatusChip
import dev.wolzard.recon.designsystem.StatusIcons
import dev.wolzard.recon.designsystem.Symbols
import dev.wolzard.recon.designsystem.dotGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(state: LibraryState, onIntent: (LibraryIntent) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Library", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = { onIntent(LibraryIntent.Back) }) { Icon(Symbols.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            windowInsets = WindowInsets(0), // MainActivity's Scaffold already pads for the status bar
        )
        OutlinedTextField(
            value = state.query, onValueChange = { onIntent(LibraryIntent.QueryChanged(it)) }, singleLine = true,
            placeholder = { Text("Search by name or by need", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)) },
            leadingIcon = { Icon(StatusIcons.Search, contentDescription = null, Modifier.size(20.dp), tint = muted) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono, fontSize = 15.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer, focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedBorderColor = line,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp).testTag("library_search"),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryFilter.entries.forEach { f ->
                FilterChip(selected = state.filter == f, onClick = { onIntent(LibraryIntent.FilterChanged(f)) }, label = { Text(f.label) }, shape = MaterialTheme.shapes.small)
            }
        }
        HorizontalDivider(color = line)
        if (state.entries.isEmpty()) {
            if (state.loaded) Empty(searching = state.query.isNotBlank() || state.filter != LibraryFilter.ALL)
            return
        }
        LazyColumn(Modifier.testTag("library_list")) {
            items(state.entries, key = { it.id }) { e ->
                EntryCard(e) { onIntent(LibraryIntent.OpenEntry(e.id)) }
                HorizontalDivider(color = line)
            }
            if (state.hasMore) item {
                TextButton(onClick = { onIntent(LibraryIntent.ShowMore) }, Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("library_more")) { Text("Show more") }
            }
        }
    }
}

@Composable
private fun Empty(searching: Boolean) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp).border(1.dp, line, MaterialTheme.shapes.medium).dotGrid(line).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.background(MaterialTheme.colorScheme.background), horizontalAlignment = Alignment.CenterHorizontally) {
            Reticle(Modifier.size(48.dp), tick = MaterialTheme.colorScheme.onSurfaceVariant, dot = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (searching) "No matches" else "Nothing saved yet", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                if (searching) "Try fewer words, or another filter." else "Repos, apps and more that Recon finds in your videos are kept here.",
                Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            )
        }
    }
}

/** One entry: name over its summary line, then the type, status, stars and video count. Meaning is the word plus the icon, not colour. */
@Composable
internal fun EntryCard(e: Entry, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp).testTag("entry_${e.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        if (e.summary.isNotBlank()) Text(e.summary, maxLines = 2, overflow = TextOverflow.Ellipsis, color = muted, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusChip(typeLabel(e.type), dev.wolzard.recon.designsystem.ChipKind.Neutral)
            StatusChip(e.status, statusKind(e.status))
            e.stars?.let { Text("★ ${stars(it)}", color = muted, style = MaterialTheme.typography.labelSmall) }
            Text(if (e.videos == 1) "1 video" else "${e.videos} videos", color = muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** 4100 → "4.1k". */
internal fun stars(n: Int) = if (n >= 1000) "${n / 1000}.${(n % 1000) / 100}k" else n.toString()
