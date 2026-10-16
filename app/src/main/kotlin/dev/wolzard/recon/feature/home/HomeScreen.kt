package dev.wolzard.recon.feature.home

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wolzard.recon.core.link.displayHost
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.designsystem.ChipKind
import dev.wolzard.recon.designsystem.Mono
import dev.wolzard.recon.designsystem.Reticle
import dev.wolzard.recon.designsystem.StatusChip
import dev.wolzard.recon.designsystem.StatusIcons
import dev.wolzard.recon.designsystem.Symbols
import dev.wolzard.recon.designsystem.Thumb
import dev.wolzard.recon.designsystem.Wordmark
import dev.wolzard.recon.designsystem.dotGrid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One Recent row (design 1d). With a [title] the row reads title over "host · time"; without one, the host leads in mono
 * over "state · time". [thumbnailUrl]/[hasVideo] pick the striped video tile or the icon tile.
 */
data class HomeRow(
    val id: String, val state: String, val host: String, val title: String?, val detail: String, val kind: ChipKind = ChipKind.Neutral,
    val icon: ImageVector? = null, val thumbnailUrl: String? = null, val tileIcon: ImageVector? = null,
) {
    companion object {
        private val dayMonth = DateTimeFormatter.ofPattern("d MMM").withZone(ZoneId.systemDefault())

        fun from(job: Job, now: Long = System.currentTimeMillis()): HomeRow {
            val host = job.displayHost()
            val summary = job.resultJson?.let {
                runCatching { Json.parseToJsonElement(it).jsonObject["analysis"]!!.jsonObject["summary"]!!.jsonPrimitive.content.lineSequence().first() }.getOrNull()
            }
            // D26: the video's own title, when Add context fetched one, leads the row as in the design.
            val title = job.videoTitle ?: summary
            val time = relative(job.createdAt, now)
            val detail = if (title != null) "$host · $time" else "${progressLabel(job.state)} · $time"
            // No title yet: a clapper while the video is being worked on, a link once it failed (design: tiktok.com / x.com).
            val tile = if (title != null) null else if (job.state == JobState.FAILED) Symbols.Link else Symbols.Movie
            return HomeRow(job.id, stateLabel(job.state), host, title, detail, chipKind(job.state), stateIcon(job.state), job.thumbnailUrl, tile)
        }

        /** "now", "2 min", "1 h", "Yesterday", then "3 Oct". */
        fun relative(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
            val s = (now - at).coerceAtLeast(0) / 1000
            val day = { t: Long -> Instant.ofEpochMilli(t).atZone(zone).toLocalDate() }
            return when {
                s < 60 -> "now"
                s < 3600 -> "${s / 60} min"
                day(at) == day(now) -> "${s / 3600} h"
                day(at) == day(now).minusDays(1) -> "Yesterday"
                else -> dayMonth.withZone(zone).format(Instant.ofEpochMilli(at))
            }
        }

        fun stateIcon(s: JobState) = when (s) {
            JobState.DONE -> StatusIcons.Done
            JobState.FAILED -> StatusIcons.Error
            JobState.WAITING_QUOTA -> StatusIcons.Waiting
            else -> StatusIcons.Running
        }

        fun chipKind(s: JobState) = when (s) {
            JobState.WAITING_QUOTA -> ChipKind.Warn
            JobState.FAILED -> ChipKind.Bad
            else -> ChipKind.Neutral
        }

        fun stateLabel(s: JobState) = when (s) {
            JobState.DONE -> "Done"
            JobState.FAILED -> "Failed"
            JobState.WAITING_QUOTA -> "Waiting"
            else -> "Running"
        }

        private fun progressLabel(s: JobState) = when (s) {
            JobState.QUEUED -> "Queued"
            JobState.DOWNLOADING -> "Downloading"
            JobState.ANALYSING -> "Analysing"
            JobState.RESEARCHING -> "Researching"
            JobState.WAITING_QUOTA -> "Usage limit"
            JobState.DONE -> "Done"
            JobState.FAILED -> "Failed"
        }
    }
}

/** The "pasted" way in (D23): type or paste a link, or fill the field from the clipboard; Start recon validates it. */
@Composable
private fun LinkField(error: String?, onIntent: (HomeIntent) -> Unit) {
    val ctx = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = text, onValueChange = { text = it }, singleLine = true, isError = error != null,
            placeholder = { Text("Paste a video link", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)) },
            leadingIcon = { Icon(Symbols.Link, contentDescription = null, Modifier.size(20.dp), tint = muted) },
            trailingIcon = {
                IconButton(onClick = { text = clipboardText(ctx) }) { Icon(Symbols.ContentPaste, contentDescription = "Paste from clipboard", Modifier.size(22.dp)) }
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onGo = { onIntent(HomeIntent.PasteLink(text)) }),
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer, focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.fillMaxWidth().height(58.dp),
        )
        error?.let { Text(it, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onIntent(HomeIntent.PasteLink(text)) }, Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.small) {
            Text("Start recon", style = MaterialTheme.typography.labelLarge)
        }
        Text("Or share a link to Recon from any app.", Modifier.padding(top = 12.dp), color = muted, style = MaterialTheme.typography.bodySmall)
    }
}

private fun clipboardText(ctx: Context): String =
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip
        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim().orEmpty()

@Composable
fun HomeScreen(state: HomeState, onIntent: (HomeIntent) -> Unit) {
    val rows = state.rows
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Wordmark(Modifier.weight(1f))
            IconButton(onClick = { onIntent(HomeIntent.OpenSettings) }) { Icon(Symbols.Settings, contentDescription = "Settings") }
        }
        // ponytail: the update banner lives in Settings now (design 1l); HomeState.update stays for the next layout pass.
        LinkField(state.pasteError, onIntent)
        if (rows.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)
                    .border(1.dp, line, MaterialTheme.shapes.medium).dotGrid(line).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(Modifier.background(MaterialTheme.colorScheme.background), horizontalAlignment = Alignment.CenterHorizontally) {
                    Reticle(Modifier.size(48.dp), tick = MaterialTheme.colorScheme.onSurfaceVariant, dot = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Text("No jobs yet", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("Share a video from any app, or paste a link above.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
            }
            return
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = line)
        Text("RECENT · ${rows.size}", Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        LazyColumn {
            items(rows, key = { it.id }) { r ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 88.dp).clickable { onIntent(HomeIntent.OpenJob(r.id)) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Thumb(r.thumbnailUrl, 50.dp, 50.dp, shape = MaterialTheme.shapes.small, fallback = r.tileIcon)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (r.title != null) Text(r.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        else Text(r.host, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 14.sp))
                        Text(r.detail, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    StatusChip(r.state, r.kind, icon = r.icon)
                }
                HorizontalDivider(color = line)
            }
        }
    }
}
