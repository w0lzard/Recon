package dev.wolzard.recon.feature.home

import dev.wolzard.recon.designsystem.UpdateBanner
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.wolzard.recon.designsystem.ChipKind
import dev.wolzard.recon.designsystem.Reticle
import dev.wolzard.recon.designsystem.StatusChip
import dev.wolzard.recon.designsystem.Wordmark
import dev.wolzard.recon.designsystem.dotGrid
import dev.wolzard.recon.designsystem.Mono
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.wolzard.recon.core.link.displayHost
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class HomeRow(val id: String, val state: String, val host: String, val summary: String?, val time: String, val kind: ChipKind = ChipKind.Neutral) {
    companion object {
        private val fmt = DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault())

        fun from(job: Job): HomeRow {
            val host = job.displayHost()
            val summary = job.resultJson?.let {
                runCatching { Json.parseToJsonElement(it).jsonObject["analysis"]!!.jsonObject["summary"]!!.jsonPrimitive.content.lineSequence().first() }.getOrNull()
            }
            return HomeRow(job.id, stateLabel(job.state), host, summary, fmt.format(Instant.ofEpochMilli(job.createdAt)), chipKind(job.state))
        }

        fun chipKind(s: JobState) = when (s) {
            JobState.WAITING_QUOTA -> ChipKind.Warn
            JobState.FAILED -> ChipKind.Bad
            else -> ChipKind.Neutral
        }

        fun stateLabel(s: JobState) = when (s) {
            JobState.QUEUED -> "Queued"
            JobState.WAITING_QUOTA -> "Waiting for quota"
            JobState.DOWNLOADING -> "Downloading"
            JobState.ANALYSING -> "Analysing"
            JobState.RESEARCHING -> "Looking up"
            JobState.DONE -> "Done"
            JobState.FAILED -> "Failed"
        }
    }
}

@Composable
fun HomeScreen(state: HomeState, onIntent: (HomeIntent) -> Unit) {
    val rows = state.rows
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Wordmark(Modifier.weight(1f))
            IconButton(onClick = { onIntent(HomeIntent.OpenSettings) }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
        }
        state.update?.let { u ->
            UpdateBanner(u, { onIntent(HomeIntent.OpenLink(it)) }, { onIntent(HomeIntent.DismissUpdate(u.version)) }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        if (rows.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(16.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).dotGrid(MaterialTheme.colorScheme.outlineVariant).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Reticle(Modifier.size(48.dp), tick = MaterialTheme.colorScheme.onSurfaceVariant, dot = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Share a video link to Recon from any app", Modifier.background(MaterialTheme.colorScheme.background), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                }
            }
            return
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn {
            items(rows, key = { it.id }) { r ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable { onIntent(HomeIntent.OpenJob(r.id)) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        if (r.summary != null) Text(r.summary, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                        Text(r.host, maxLines = 1, style = if (r.summary != null) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono, fontWeight = FontWeight.Medium))
                        Text(r.time, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    StatusChip(r.state, r.kind)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
