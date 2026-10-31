package dev.wolzard.recon.feature.library

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import dev.wolzard.recon.core.link.hostOf
import dev.wolzard.recon.core.research.LinkType
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
                        maturityChip(e.maturity)?.let { (label, kind) -> StatusChip(label, kind, Modifier.testTag("entry_maturity")) }
                        e.stars?.let { Text("★ ${stars(it)}", color = muted, style = MaterialTheme.typography.labelSmall) }
                    }
                    factsLine(e, System.currentTimeMillis())?.let { Text(it, color = muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("entry_facts")) }
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
                if (hasStructuredFields(e)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp).testTag("entry_structured"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        e.problemSolved?.let { Field("PROBLEM SOLVED", muted) { Text(it, style = MaterialTheme.typography.bodyLarge) } }
                        if (e.keyFeatures.isNotEmpty()) Field("KEY FEATURES", muted) { Bullets(e.keyFeatures) }
                        if (e.useCases.isNotEmpty()) Field("USE CASES", muted) { Bullets(e.useCases) }
                        if (e.whenToUse != null || e.whenNotToUse != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            e.whenToUse?.let { WhenCard("WHEN TO USE", it, Modifier.weight(1f).testTag("entry_when_to_use"), line, muted) }
                            e.whenNotToUse?.let { WhenCard("WHEN NOT TO USE", it, Modifier.weight(1f).testTag("entry_when_not_to_use"), line, muted) }
                        }
                    }
                    HorizontalDivider(color = line)
                }
                if (e.links.isNotEmpty()) {
                    Text("LINKS", Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp), color = muted, style = MaterialTheme.typography.labelSmall)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        e.links.forEach { l ->
                            Row(
                                Modifier.heightIn(min = 48.dp).border(1.dp, line, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp))
                                    .clickable(onClickLabel = "Open ${linkLabel(l.type)} link") { onIntent(EntryIntent.OpenLink(l.url)) }.padding(horizontal = 12.dp).testTag("entry_link_${l.type.name}"),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(linkIcon(l.type), contentDescription = null, Modifier.size(16.dp))
                                Text("${linkLabel(l.type)} · ${hostOf(l.url)}", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    HorizontalDivider(color = line)
                }
                Text("VIDEOS · ${state.videos.size}", Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp), color = muted, style = MaterialTheme.typography.labelSmall)
            }
            items(state.videos, key = { it.id }) { r ->
                JobRow(r) { onIntent(EntryIntent.OpenVideo(r.id)) }
                HorizontalDivider(color = line)
            }
        }
    }
}

private fun linkIcon(t: LinkType) = when (t) {
    LinkType.REPO -> Symbols.Code
    LinkType.DOCS -> Symbols.MenuBook
    else -> Symbols.Link
}

@Composable
private fun Field(label: String, muted: androidx.compose.ui.graphics.Color, body: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = muted, style = MaterialTheme.typography.labelSmall)
        body()
    }
}

@Composable
private fun Bullets(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("•"); Text(it, style = MaterialTheme.typography.bodyLarge) } }
    }
}

/** A hairline card with a small label; "when to use" and "when not to use" sit side by side. */
@Composable
private fun WhenCard(label: String, text: String, modifier: Modifier, line: androidx.compose.ui.graphics.Color, muted: androidx.compose.ui.graphics.Color) {
    Column(modifier.border(1.dp, line, RoundedCornerShape(6.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = muted, style = MaterialTheme.typography.labelSmall)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
