package dev.wolzard.recon.feature.context

import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import dev.wolzard.recon.core.model.LinkPreview
import dev.wolzard.recon.designsystem.Thumb
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.wolzard.recon.designsystem.Mono
import dev.wolzard.recon.designsystem.StatusIcons
import java.net.URI

/**
 * Shown after a valid link is shared or pasted (D23). Skip starts with no context; Start sends the trimmed text.
 * Layout from the design's 3a/3b: the Skip/Start bar is pinned under the scroll area, and imePadding() keeps it above the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextScreen(state: ContextState, compact: Boolean = false, onIntent: (ContextIntent) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val line = MaterialTheme.colorScheme.outlineVariant
    // compact (share sheet): as tall as the content, so the sheet fits it. The sheet itself sits above the keyboard,
    // so only the navigation bar pads the bottom here (and none while the keyboard is up: the sheet consumed it).
    val outer = if (compact) Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
    else Modifier.fillMaxSize().imePadding()
    Column(outer) {
        TopAppBar(
            title = { Text(ContextCopy.TITLE, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
            navigationIcon = { IconButton(onClick = { onIntent(ContextIntent.Back) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { Text("OPTIONAL", Modifier.padding(end = 12.dp), color = muted, style = MaterialTheme.typography.labelSmall) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            windowInsets = WindowInsets(0), // MainActivity's Scaffold already pads for the status bar
        )
        Column(
            Modifier.weight(1f, fill = !compact).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LinkCard(state.link, state.host, state.preview)
            OutlinedTextField(
                value = state.text, onValueChange = { onIntent(ContextIntent.TextChanged(it)) },
                modifier = Modifier.fillMaxWidth().testTag("context_field"), shape = RoundedField,
                minLines = 3, maxLines = 6, textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text(ContextCopy.PLACEHOLDER, style = MaterialTheme.typography.bodyLarge) },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer, focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedBorderColor = line, focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                ),
                isError = state.error != null,
            )
            Row(Modifier.fillMaxWidth().padding(top = 0.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(ContextCopy.SENT_WITH_VIDEO, color = muted, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal, letterSpacing = MaterialTheme.typography.bodySmall.letterSpacing))
                Text("${state.text.length}/300", color = muted, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal, letterSpacing = MaterialTheme.typography.bodySmall.letterSpacing))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 0.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ContextCopy.SUGGESTIONS.forEach { s ->
                    OutlinedButton(
                        onClick = { onIntent(ContextIntent.Suggest(s)) }, Modifier.height(36.dp), shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, line),
                    ) { Text(s, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface) }
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        HorizontalDivider(color = line)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onIntent(ContextIntent.Skip) }, Modifier.weight(1f).height(48.dp),
                enabled = !state.starting, shape = MaterialTheme.shapes.small,
            ) { Text("Skip") }
            Button(
                onClick = { onIntent(ContextIntent.Start) }, Modifier.weight(1f).height(48.dp),
                enabled = !state.starting, shape = MaterialTheme.shapes.small,
            ) { Text("Start") }
        }
    }
}

private val RoundedField = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)

/** Host, path and length inside the Recon corner ticks; the link glyph shows until the thumbnail arrives, and stays if it never does (D26). */
@Composable
private fun LinkCard(link: String, host: String, preview: LinkPreview?) {
    val tick = MaterialTheme.colorScheme.onSurface
    val path = runCatching { URI(link).rawPath }.getOrNull()?.takeIf { it.isNotEmpty() && it != "/" }
    val length = LinkPreview.formatDuration(preview?.durationSec)?.let { "$it video" }
    val detail = listOfNotNull(path, length).joinToString(" · ").ifEmpty { null }
    Row(
        Modifier.fillMaxWidth().cornerTicks(tick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Thumb(preview?.thumbnailUrl, 40.dp, 40.dp)
        Column(Modifier.weight(1f)) {
            Text(host, Modifier.testTag("context_host"), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono, fontWeight = FontWeight.Medium))
            detail?.let { Text(it, Modifier.testTag("context_detail"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Four 10 dp corner ticks, 1.5 dp stroke, as on the design's link card. */
private fun Modifier.cornerTicks(c: Color) = drawBehind {
    val t = 10.dp.toPx(); val s = 1.5.dp.toPx(); val h = s / 2
    val (w, ht) = size.width to size.height
    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        drawLine(c, Offset(x, y), Offset(x + dx * t, y), s)
        drawLine(c, Offset(x, y), Offset(x, y + dy * t), s)
    }
    corner(h, h, 1f, 1f); corner(w - h, h, -1f, 1f); corner(h, ht - h, 1f, -1f); corner(w - h, ht - h, -1f, -1f)
}
