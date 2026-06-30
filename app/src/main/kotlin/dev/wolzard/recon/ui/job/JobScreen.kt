package dev.wolzard.recon.ui.job

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import dev.wolzard.recon.ui.common.dotGrid
import dev.wolzard.recon.ui.theme.status
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedCard
import androidx.compose.ui.text.font.FontWeight
import dev.wolzard.recon.ui.common.ChipKind
import dev.wolzard.recon.ui.common.Reticle
import dev.wolzard.recon.ui.common.StatusChip
import dev.wolzard.recon.ui.theme.Mono
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.style.TextOverflow
import dev.wolzard.recon.ui.home.HomeRow
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Evidence
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.Finding
import dev.wolzard.recon.core.model.GhRepo
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobResult
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.model.Verdict

sealed interface JobAction {
    data object Retry : JobAction
    data object CheckAgain : JobAction
    data object LookUp : JobAction
    data object TrySharper : JobAction
    data object SignIn : JobAction
    data object OpenSettings : JobAction
    data object Cancel : JobAction
    data object Back : JobAction
    data class Pick(val findingIndex: Int, val repo: GhRepo?) : JobAction   // null = "None of these"
    data class Open(val url: String) : JobAction
}

@OptIn(ExperimentalMaterial3Api::class)
/** Stateless: everything comes from [job]/[result]; [now] decides whether Retry is enabled yet. */
@Composable
fun JobScreen(job: Job, result: JobResult?, now: Long, onAction: (JobAction) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(HomeRow.from(job).host, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono, fontWeight = FontWeight.Medium)) },
            navigationIcon = { IconButton(onClick = { onAction(JobAction.Back) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            windowInsets = WindowInsets(0), // MainActivity's Scaffold already pads for the status bar
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                job.state == JobState.FAILED -> Failed(job, now, onAction)
                job.state == JobState.DONE && result != null -> Done(result, onAction)
                else -> Running(job, onAction)
            }
        }
    }
}

@Composable
private fun Running(job: Job, onAction: (JobAction) -> Unit) {
    if (job.state == JobState.WAITING_QUOTA) {
        StatusChip("Waiting for quota", ChipKind.Warn)
        Text("Waiting for your free AI quota", style = MaterialTheme.typography.titleLarge)
        Text("Link: ${HomeRow.from(job).host}")
        Text("Quota resets at ${job.retryAtEpochMs?.let(JobCopy::time) ?: "the next daily reset"}; this video starts then.")
        OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { onAction(JobAction.Cancel) }) { Text("Cancel") }
        return
    }
    val steps = listOf(JobState.QUEUED to "Queued", JobState.DOWNLOADING to "Downloading", JobState.ANALYSING to "Analysing", JobState.RESEARCHING to "Looking things up")
    val current = steps.indexOfFirst { it.first == job.state }
    Column(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)) {
        steps.forEachIndexed { i, (_, label) ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        i < current -> Text("✓", color = MaterialTheme.colorScheme.primary)
                        i == current -> Reticle(Modifier.size(18.dp))
                        else -> Text("·")
                    }
                    Text(label, color = if (i > current) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, style = if (i == current) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
                }
                // ponytail: the design's 1.6 s sweep is the stock indeterminate bar
                if (i == current) LinearProgressIndicator(Modifier.padding(top = 12.dp).fillMaxWidth().height(2.dp), trackColor = MaterialTheme.colorScheme.outlineVariant)
                else Spacer(Modifier.height(12.dp))
            }
        }
    }
    if (job.state == JobState.DOWNLOADING) job.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
}

@Composable
private fun Failed(job: Job, now: Long, onAction: (JobAction) -> Unit) {
    val reason = job.failure ?: FailureReason.UNKNOWN
    var help by rememberSaveable { mutableStateOf(false) }
    StatusChip("Failed", ChipKind.Bad)
    Text(JobCopy.failureMessage(reason, job.retryAtEpochMs, job.failureDetail), style = MaterialTheme.typography.titleLarge)
    val retryEnabled = job.retryAtEpochMs?.let { now >= it } ?: true
    val sharedFile = job.localUri != null
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (reason) {
            FailureReason.UNSUPPORTED_LINK -> Unit
            FailureReason.DOWNLOAD_FAILED -> {
                OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { help = true }) { Text("Share the video file instead") }
                if (!sharedFile) Button(shape = MaterialTheme.shapes.small, onClick = { onAction(JobAction.Retry) }) { Text("Retry") }
            }
            FailureReason.VIDEO_UNREADABLE -> OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { help = true }) { Text("Share a different file") }
            FailureReason.API_KEY_MISSING, FailureReason.API_KEY_INVALID -> Button(shape = MaterialTheme.shapes.small, onClick = { onAction(JobAction.SignIn) }) { Text("Sign in") }
            else -> if (!sharedFile) Button(shape = MaterialTheme.shapes.small, onClick = { onAction(JobAction.Retry) }, enabled = retryEnabled) { Text("Retry") }
        }
    }
    if (sharedFile && reason != FailureReason.UNSUPPORTED_LINK) Text("Share the video again to try once more; Recon's copy is gone.", style = MaterialTheme.typography.bodySmall)
    if (help) AlertDialog(
        onDismissRequest = { help = false },
        confirmButton = { TextButton(onClick = { help = false }) { Text("OK") } },
        title = { Text("Share the video file") },
        text = { Text(JobCopy.SHARE_FILE_HELP) },
    )
}

@Composable
private fun Done(result: JobResult, onAction: (JobAction) -> Unit) {
    SectionLabel("SUMMARY")
    Text(result.analysis.summary, style = MaterialTheme.typography.bodyLarge)
    result.degraded.forEach { (id, retryAt) ->
        val text = JobCopy.degradedBanner(id, retryAt) ?: return@forEach
        OutlinedCard(border = BorderStroke(1.dp, MaterialTheme.colorScheme.status.unverified)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (id) {
                        "budget" -> Button(shape = MaterialTheme.shapes.small, onClick = { onAction(JobAction.LookUp) }) { Text("Look up") }
                        "github" -> {
                            OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { onAction(JobAction.OpenSettings) }) { Text("Add GitHub token") }
                            Button(shape = MaterialTheme.shapes.small, onClick = { onAction(JobAction.CheckAgain) }) { Text("Check again") }
                        }
                        else -> Button(shape = MaterialTheme.shapes.small, onClick = { onAction(JobAction.CheckAgain) }) { Text("Check again") }
                    }
                }
            }
        }
    }
    val sharperOffered = result.path != "youtube-url" && result.analysis.entities.all { it.evidence == Evidence.INFERRED }
    if (result.findings.isEmpty()) Box(
        Modifier.fillMaxWidth().heightIn(min = 200.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).dotGrid(MaterialTheme.colorScheme.outlineVariant).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { Text(JobCopy.NOTHING_FOUND, Modifier.background(MaterialTheme.colorScheme.background), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
    else SectionLabel("ENTITIES FOUND · ${result.findings.size}")
    result.findings.forEachIndexed { i, f -> FindingCard(i, f, onAction) }
    if (sharperOffered) OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Text hard to read?", style = MaterialTheme.typography.labelLarge)
            Text("Run again with more frames at higher resolution.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { onAction(JobAction.TrySharper) }, Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.small) { Text("Try sharper video") }
        }
    }

    if (result.sources.isNotEmpty()) {
        SectionLabel("Sources")
        Column {
            result.sources.forEachIndexed { i, src ->
