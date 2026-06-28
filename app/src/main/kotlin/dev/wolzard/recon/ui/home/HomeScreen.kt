package dev.wolzard.recon.ui.home

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
import dev.wolzard.recon.ui.common.ChipKind
import dev.wolzard.recon.ui.common.Reticle
import dev.wolzard.recon.ui.common.StatusChip
import dev.wolzard.recon.ui.common.Wordmark
import dev.wolzard.recon.ui.common.dotGrid
import dev.wolzard.recon.ui.theme.Mono
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
import dev.wolzard.recon.core.link.LinkRouter
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class HomeRow(val id: String, val state: String, val host: String, val summary: String?, val time: String, val kind: ChipKind = ChipKind.Neutral) {
    companion object {
        private val fmt = DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault())

        fun from(job: Job): HomeRow {
            val host = if (job.localUri != null) "Shared video" else
                (job.url ?: LinkRouter.extractUrl(job.sharedText))?.let { runCatching { URI(it).host?.removePrefix("www.")?.removePrefix("m.") }.getOrNull() } ?: "Link"
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
