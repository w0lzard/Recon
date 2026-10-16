package dev.wolzard.recon.feature.job

import dev.wolzard.recon.core.model.LinkPreview
import dev.wolzard.recon.designsystem.Thumb
import dev.wolzard.recon.core.model.MediaType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import dev.wolzard.recon.designsystem.dotGrid
import dev.wolzard.recon.designsystem.status
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedCard
import androidx.compose.ui.text.font.FontWeight
import dev.wolzard.recon.designsystem.ChipKind
import dev.wolzard.recon.designsystem.Reticle
import dev.wolzard.recon.designsystem.StatusChip
import dev.wolzard.recon.designsystem.StatusIcons
import dev.wolzard.recon.designsystem.Mono
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
import dev.wolzard.recon.core.link.displayHost
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.wolzard.recon.core.link.LinkRouter
import dev.wolzard.recon.core.media.VideoQualitySelector
import dev.wolzard.recon.core.media.VideoSaveOption
import dev.wolzard.recon.data.media.MediaStoreSaver
import dev.wolzard.recon.data.media.NetworkHelper
import dev.wolzard.recon.work.SaveVideoWorker
import dev.wolzard.recon.core.model.Answer
import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.AnswerStatus
import kotlin.math.roundToInt
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Evidence
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.Finding
import dev.wolzard.recon.core.model.GhRepo
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobResult
import dev.wolzard.recon.core.model.JobState
import dev.wolzard.recon.core.model.Verdict

@OptIn(ExperimentalMaterial3Api::class)
/** Stateless: everything comes from [job]/[result]; [now] decides whether Retry is enabled yet. */
@Composable
fun JobScreen(
    job: Job,
    result: JobResult?,
    now: Long,
    isProbingFormats: Boolean = false,
    saveOptions: List<VideoSaveOption>? = null,
    saveError: String? = null,
    onIntent: (JobIntent) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(job.displayHost(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono, fontWeight = FontWeight.Medium)) },
            navigationIcon = { IconButton(onClick = { onIntent(JobIntent.Back) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            windowInsets = WindowInsets(0), // MainActivity's Scaffold already pads for the status bar
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val question = job.userContext
            // D23: the question is echoed in every state; once done, the answer card comes before the summary.
            val done = job.state == JobState.DONE && result != null
            if (done) VideoHeader(job)
            if (question != null) YourQuestion(question, large = done)
            val url = job.url
            if (done && url != null && LinkRouter.isWebUrl(url)) {
                SaveVideoSection(job, isProbingFormats, saveOptions, saveError, onIntent)
            }
            when {
                job.state == JobState.FAILED -> Failed(job, now, onIntent)
                job.state == JobState.DONE && result != null -> Done(result, question != null, onIntent)
                else -> Running(job, onIntent)
            }
        }
    }
}

@Composable
private fun SaveVideoSection(
    job: Job,
    isProbingFormats: Boolean,
    saveOptions: List<VideoSaveOption>?,
    saveError: String?,
    onIntent: (JobIntent) -> Unit,
) {
    val context = LocalContext.current
    val workManager = remember(context) { WorkManager.getInstance(context) }
    val saveWorkInfos by workManager.getWorkInfosForUniqueWorkFlow(SaveVideoWorker.workName(job.id))
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val currentSave = saveWorkInfos.firstOrNull()

    var selectedOption by remember(saveOptions) {
        mutableStateOf(saveOptions?.firstOrNull { it.isDefault } ?: saveOptions?.firstOrNull())
    }
    var storageError by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingOptionForMobileData by remember { mutableStateOf<VideoSaveOption?>(null) }
    var pendingOptionForSaf by remember { mutableStateOf<VideoSaveOption?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4"),
    ) { uri: Uri? ->
        val opt = pendingOptionForSaf
        pendingOptionForSaf = null
        if (uri != null && opt != null) {
            SaveVideoWorker.enqueue(
                context = context,
                jobId = job.id,
                url = job.url ?: "",
                formatId = opt.formatId,
                title = job.videoTitle ?: "video",
                targetUri = uri.toString(),
            )
            onIntent(JobIntent.DismissSaveDialog)
        }
    }

    fun executeSave(option: VideoSaveOption) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            SaveVideoWorker.enqueue(
                context = context,
                jobId = job.id,
                url = job.url ?: "",
                formatId = option.formatId,
                title = job.videoTitle ?: "video",
            )
            onIntent(JobIntent.DismissSaveDialog)
        } else {
            pendingOptionForSaf = option
            val clean = MediaStoreSaver.cleanFileName(job.videoTitle ?: "video")
            createDocumentLauncher.launch("$clean.mp4")
        }
    }

    fun startSaving(option: VideoSaveOption) {
        val freeBytes = context.cacheDir.usableSpace
        if (!VideoQualitySelector.hasEnoughStorage(freeBytes, option.estimatedSizeBytes)) {
            val needed = VideoQualitySelector.formatBytes(VideoQualitySelector.requiredStorage(option.estimatedSizeBytes))
            val free = VideoQualitySelector.formatBytes(freeBytes)
            storageError = "Not enough free storage. Merging requires at least $needed free space (2x estimate), but only $free is available."
            return
        }

        if (NetworkHelper.isMobileData(context)) {
            pendingOptionForMobileData = option
            return
        }

        executeSave(option)
    }

    when (currentSave?.state) {
        WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
            val progress = currentSave.progress.getFloat(SaveVideoWorker.KEY_PROGRESS, 0f)
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().testTag("save_running"),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Reticle(Modifier.size(18.dp))
                            Text("Saving video...", style = MaterialTheme.typography.titleSmall)
                        }
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono))
                    }
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(
                            onClick = { SaveVideoWorker.cancel(context, job.id) },
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
        WorkInfo.State.SUCCEEDED -> {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().testTag("save_succeeded"),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.status.verified),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(StatusIcons.Check, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.status.verified)
                        Column {
                            Text("Video saved", style = MaterialTheme.typography.titleSmall)
                            Text("Saved to Movies/Recon", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val savedUriStr = currentSave.outputData.getString(SaveVideoWorker.KEY_SAVED_URI)
                    if (savedUriStr != null) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(savedUriStr), "video/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            },
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text("Play")
                        }
                    }
                }
            }
        }
        WorkInfo.State.FAILED -> {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth().testTag("save_failed"),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.status.unverified),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(StatusIcons.Error, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.status.unverified)
                        Text("Save failed", style = MaterialTheme.typography.titleSmall)
                    }
                    val errorMsg = currentSave.outputData.getString(SaveVideoWorker.KEY_ERROR) ?: "Could not download or merge video"
                    Text(errorMsg, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(
                            onClick = { onIntent(JobIntent.RequestSaveVideo) },
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text("Try again")
                        }
                    }
                }
            }
        }
        else -> {
            OutlinedButton(
                onClick = { onIntent(JobIntent.RequestSaveVideo) },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_video_button"),
                shape = MaterialTheme.shapes.small,
                enabled = !isProbingFormats,
            ) {
                if (isProbingFormats) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Checking formats...")
                } else {
                    Icon(StatusIcons.Download, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save video")
                }
            }
        }
    }

    if (saveError != null) {
        AlertDialog(
            onDismissRequest = { onIntent(JobIntent.DismissSaveDialog) },
            title = { Text("Cannot save video") },
            text = { Text(saveError) },
            confirmButton = { TextButton(onClick = { onIntent(JobIntent.DismissSaveDialog) }) { Text("OK") } },
        )
    }

    if (storageError != null) {
        AlertDialog(
            onDismissRequest = { storageError = null },
            title = { Text("Not enough storage") },
            text = { Text(storageError ?: "") },
            confirmButton = { TextButton(onClick = { storageError = null }) { Text("OK") } },
        )
    }

    if (pendingOptionForMobileData != null) {
        val opt = pendingOptionForMobileData!!
        AlertDialog(
            onDismissRequest = { pendingOptionForMobileData = null },
            title = { Text("Mobile data warning") },
            text = { Text("You are on mobile data. Saving in ${opt.resolutionLabel} will use approximately ${opt.formattedSize}. Continue?") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingOptionForMobileData = null
                        executeSave(opt)
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Save anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingOptionForMobileData = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (saveOptions != null) {
        AlertDialog(
            onDismissRequest = { onIntent(JobIntent.DismissSaveDialog) },
            title = { Text("Choose quality") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    saveOptions.forEach { opt ->
                        Row(
                            Modifier.fillMaxWidth().selectable(
                                selected = (opt == selectedOption),
                                onClick = { selectedOption = opt },
                                role = Role.RadioButton,
                            ).padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = (opt == selectedOption), onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(opt.resolutionLabel, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                Text(
                                    "${opt.vcodec} + ${opt.acodec} · ${opt.formattedSize}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedOption?.let { startSaving(it) } },
                    shape = MaterialTheme.shapes.small,
                    enabled = selectedOption != null,
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(JobIntent.DismissSaveDialog) }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun Running(job: Job, onIntent: (JobIntent) -> Unit) {
    if (job.state == JobState.WAITING_QUOTA) {
        StatusChip("Waiting for quota", ChipKind.Warn, icon = StatusIcons.Waiting)
        Text("Waiting for your free AI quota", style = MaterialTheme.typography.titleLarge)
        Text("Link: ${job.displayHost()}")
        // A time is shown only when the backend gave one (retryAtEpochMs); never a guess.
        val resetAt = job.retryAtEpochMs?.let(JobCopy::time)
        Text(if (resetAt != null) "Quota resets at $resetAt; this video starts then." else "${JobCopy.TRY_LATER}. This video starts when your usage limit lifts.")
        InfoCard("EXPECTED RESET", resetAt ?: JobCopy.TRY_LATER)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { onIntent(JobIntent.Cancel) }) { Text("Cancel") }
            Button(shape = MaterialTheme.shapes.small, onClick = { onIntent(JobIntent.SignIn) }) { Text("Switch account") }
        }
        SectionLabel("WHAT YOU CAN DO")
        Text("• Leave it queued. You get a notification when it finishes.", style = MaterialTheme.typography.bodyMedium)
        Text("• Switch to another Google account and resume now.", style = MaterialTheme.typography.bodyMedium)
        Text("• Cancel the job and share the link again later.", style = MaterialTheme.typography.bodyMedium)
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
                        i < current -> Icon(StatusIcons.Check, contentDescription = "Done", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.status.verified)
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
private fun Failed(job: Job, now: Long, onIntent: (JobIntent) -> Unit) {
    val reason = job.failure ?: FailureReason.UNKNOWN
    var help by rememberSaveable { mutableStateOf(false) }
    StatusChip("Failed", ChipKind.Bad, icon = StatusIcons.Error)
    Text(JobCopy.failureMessage(reason, job.retryAtEpochMs, job.failureDetail), style = MaterialTheme.typography.titleLarge)
    InfoCard("TARGET", job.displayHost(), job.thumbnailUrl)
    val retryEnabled = job.retryAtEpochMs?.let { now >= it } ?: true
    val sharedFile = job.localUri != null
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (reason) {
            FailureReason.UNSUPPORTED_LINK -> Unit
            FailureReason.DOWNLOAD_FAILED -> {
                OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { help = true }) { Text("Share the video file instead") }
                if (!sharedFile) Button(shape = MaterialTheme.shapes.small, onClick = { onIntent(JobIntent.Retry) }) { Text("Retry") }
            }
            FailureReason.VIDEO_UNREADABLE -> OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { help = true }) { Text("Share a different file") }
            FailureReason.API_KEY_MISSING, FailureReason.API_KEY_INVALID -> Button(shape = MaterialTheme.shapes.small, onClick = { onIntent(JobIntent.SignIn) }) {
                Text(if (reason == FailureReason.API_KEY_INVALID) "Sign in again" else "Sign in")
            }
            else -> if (!sharedFile) Button(shape = MaterialTheme.shapes.small, onClick = { onIntent(JobIntent.Retry) }, enabled = retryEnabled) { Text("Retry") }
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

/** Mono label over a mono value on a hairline card (design: TARGET, EXPECTED RESET). */
@Composable
private fun InfoCard(label: String, value: String, thumbnailUrl: String? = null) {
    Row(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (thumbnailUrl != null) Thumb(thumbnailUrl, 48.dp, 64.dp) // D26, design 1f
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel(label)
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono, fontWeight = FontWeight.Medium))
        }
    }
}

/** D26, design 1g: the video's thumbnail, title and length head the result when Add context fetched them. */
@Composable
private fun VideoHeader(job: Job) {
    if (job.videoTitle == null && job.thumbnailUrl == null) return
    Row(Modifier.fillMaxWidth().testTag("video_header"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Thumb(job.thumbnailUrl, 40.dp, 54.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            job.videoTitle?.let { Text(it, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp), maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Text(listOfNotNull(LinkPreview.formatDuration(job.videoDurationSec), job.displayHost()).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Design 3c: a hairline left rule while the job runs; 3d: a larger quote heading the result. */
@Composable
private fun YourQuestion(text: String, large: Boolean) {
    val quoted = "“$text”"
    if (large) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("YOUR QUESTION")
        Text(quoted, Modifier.testTag("your_question"), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 26.sp))
    } else {
        val rule = MaterialTheme.colorScheme.outlineVariant
        Column(
            Modifier.fillMaxWidth().drawBehind { drawLine(rule, Offset(0f, 0f), Offset(0f, size.height), 1.5.dp.toPx()) }.padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionLabel("YOUR QUESTION")
            Text(quoted, Modifier.testTag("your_question"), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Design 3d/3f/3g. Answer sources are plain text (D23), numbered after the job's own sources in one shared list. */
@Composable
private fun AnswerCard(answer: Answer?, firstSourceNumber: Int) {
    val status = answer?.status ?: AnswerStatus.UNCLEAR
    val kind = when (status) {
        AnswerStatus.VERIFIED -> ChipKind.Ok
        AnswerStatus.UNVERIFIED -> ChipKind.Warn
        AnswerStatus.UNCLEAR -> ChipKind.Unclear
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val line = MaterialTheme.colorScheme.outlineVariant
    OutlinedCard(
        Modifier.fillMaxWidth().testTag("answer_card"), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), border = BorderStroke(1.dp, line),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                answer?.kind?.let { Icon(icon(it, answer.subtype), contentDescription = null, Modifier.size(18.dp), tint = muted) }
                SectionLabel(JobCopy.answerLabel(answer), Modifier.weight(1f).testTag("answer_label"))
                StatusChip(JobCopy.answerStatus(status), kind)
                answer?.let { StatusChip("${(it.confidence * 100).roundToInt()}%", ChipKind.Neutral) }
            }
            // Design 3d: with a title, the title line leads and the reply reads as its muted description.
            val title = answer?.title
            if (title != null) Text(title, Modifier.testTag("answer_title"), style = MaterialTheme.typography.titleMedium)
            Text(answer?.text ?: JobCopy.NO_ANSWER, color = if (title != null) muted else Color.Unspecified, style = MaterialTheme.typography.bodyLarge)
            val sources = answer?.sources.orEmpty()
            if (sources.isNotEmpty()) Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(color = line)
                sources.forEachIndexed { k, src ->
                    if (k > 0) HorizontalDivider(color = line)
                    SourceLine(firstSourceNumber + k, src, minHeight = 44.dp)
                }
            } else if (status != AnswerStatus.UNCLEAR) Row(
                Modifier.heightIn(min = 24.dp).testTag("no_source"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(StatusIcons.LinkOff, contentDescription = null, Modifier.size(16.dp), tint = muted)
                Text(JobCopy.NO_SOURCE, color = muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** One numbered source row; [onOpen] null = plain text, never opened as a link. */
@Composable
private fun SourceLine(number: Int, text: String, minHeight: androidx.compose.ui.unit.Dp = 48.dp, onOpen: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = minHeight).then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("$number", Modifier.width(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = SourceText)
        Text(text, Modifier.weight(1f), style = SourceText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onOpen != null) Icon(StatusIcons.OpenInNew, contentDescription = "Opens in browser", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val SourceText = androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 13.sp, lineHeight = 18.sp)

@Composable
private fun Done(result: JobResult, asked: Boolean, onIntent: (JobIntent) -> Unit) {
    val answerSources = if (asked) result.analysis.answer?.sources.orEmpty() else emptyList()
    if (asked) AnswerCard(result.analysis.answer, firstSourceNumber = result.sources.size + 1)
    SectionLabel("SUMMARY")
    Text(result.analysis.summary, style = MaterialTheme.typography.bodyLarge)
    result.degraded.forEach { (id, retryAt) ->
        val text = JobCopy.degradedBanner(id, retryAt) ?: return@forEach
        OutlinedCard(border = BorderStroke(1.dp, MaterialTheme.colorScheme.status.unverified)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (id) {
                        "budget" -> Button(shape = MaterialTheme.shapes.small, onClick = { onIntent(JobIntent.LookUp) }) { Text("Look up") }
                        "github" -> {
                            OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { onIntent(JobIntent.OpenSettings) }) { Text("Add GitHub token") }
                            Button(shape = MaterialTheme.shapes.small, onClick = { onIntent(JobIntent.CheckAgain) }) { Text("Check again") }
                        }
                        else -> Button(shape = MaterialTheme.shapes.small, onClick = { onIntent(JobIntent.CheckAgain) }) { Text("Check again") }
                    }
                }
            }
        }
    }
    val sharperOffered = result.path != "youtube-url" && result.analysis.entities.all { it.evidence == Evidence.INFERRED }
    if (result.findings.isEmpty()) Box(
        Modifier.fillMaxWidth().heightIn(min = 200.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).dotGrid(MaterialTheme.colorScheme.outlineVariant).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.background(MaterialTheme.colorScheme.background), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(JobCopy.NOTHING_FOUND, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(JobCopy.NOTHING_FOUND_DETAIL, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
    val indexed = result.findings.withIndex()
    val entities = indexed.filter { it.value.entity.type != EntityType.CLAIM }
    val claims = indexed.filter { it.value.entity.type == EntityType.CLAIM }
    if (entities.isNotEmpty()) SectionLabel("ENTITIES FOUND · ${entities.size}")
    entities.forEach { (i, f) -> FindingCard(i, f, onIntent) }
    if (claims.isNotEmpty()) {
        SectionLabel("CLAIMS · ${claims.size}")
        Column(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)) {
            claims.forEachIndexed { n, (_, f) ->
                if (n > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ClaimRow(f)
            }
        }
        if (claims.any { it.value.entity.domain?.sensitive == true }) Text(JobCopy.NOT_ADVICE, Modifier.testTag("advice_footer"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
    if (sharperOffered) OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Text hard to read?", style = MaterialTheme.typography.labelLarge)
            Text("Run again with more frames at higher resolution.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { onIntent(JobIntent.TrySharper) }, Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.small) { Text("Try sharper video") }
        }
    }

    val sourceCount = result.sources.size + answerSources.size
    if (sourceCount > 0) {
        SectionLabel("SOURCES · $sourceCount")
        Column {
            result.sources.forEachIndexed { i, src ->
                SourceLine(i + 1, src.title) { onIntent(JobIntent.Open(src.uri)) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            answerSources.forEachIndexed { k, src ->
                SourceLine(result.sources.size + k + 1, src)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    result.searchSuggestionsHtml?.let { SearchSuggestions(it) }
    OnScreenText(result.analysis.onScreenText)
    val models = listOfNotNull(result.models["analyse"]?.let { "Analysed by $it" }, result.models["research"]?.let { "researched by $it" })
    if (models.isNotEmpty()) Text(models.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun OnScreenText(lines: List<String>) {
    if (lines.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) { Text(if (open) "Hide on-screen text" else "Show on-screen text") }
    if (open) lines.forEach { Text("“$it”", style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun FindingCard(index: Int, f: Finding, onIntent: (JobIntent) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val verdictKind = verdictKind(f)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val typeLabel = JobCopy.typeLabel(f.entity.type, f.entity.subtype)
                Icon(icon(f.entity.type, f.entity.subtype), contentDescription = typeLabel, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(typeLabel, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                StatusChip(JobCopy.verdict(f.verdict), verdictKind)
            }
            // Design 3e: a media title carries its year (and maker) beside it, in the muted ink.
            val qualifier = f.entity.qualifier?.takeIf { f.entity.type == EntityType.MEDIA && it.isNotBlank() }
            Text(
                if (qualifier == null) AnnotatedString(f.title) else buildAnnotatedString {
                    append(f.title); append(" ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(qualifier) }
                },
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(f.entity.evidence.wire.replaceFirstChar(Char::uppercase), ChipKind.Neutral)
                if (f.entity.evidence == Evidence.INFERRED) StatusChip("Guess", ChipKind.Warn)
                StatusChip(JobCopy.basis(f.basis), ChipKind.Neutral)
                f.stars?.let { StatusChip("★ $it", ChipKind.Neutral) }
            }
            if (f.candidates.isNotEmpty()) Candidates(index, f.candidates, onIntent)
            if (f.whatItIs.isNotBlank()) Text(f.whatItIs, color = MaterialTheme.colorScheme.onSurfaceVariant)
            f.keyFacts.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            if (f.howToTry.isNotBlank()) Text("${JobCopy.howToTryLabel(f.entity.type)}: ${f.howToTry}", style = MaterialTheme.typography.bodyMedium)
            if (f.whyInVideo.isNotBlank()) Text(f.whyInVideo, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            f.primaryLink?.takeIf { it.startsWith("https://") }?.let { link ->
                OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { onIntent(JobIntent.Open(link)) }) { Text(if (link.startsWith("https://github.com/")) "Open on GitHub" else "Open on Wikipedia") }
            }
        }
    }
}

@Composable
private fun ClaimRow(f: Finding) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusChip(JobCopy.verdict(f.verdict), verdictKind(f))
        Text(f.title, style = MaterialTheme.typography.titleMedium)
        if (f.whatItIs.isNotBlank()) Text(f.whatItIs, color = MaterialTheme.colorScheme.onSurfaceVariant)
        f.source?.let { Text("Source: $it", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        Text(JobCopy.basis(f.basis), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        if (f.entity.domain?.sensitive == true) Row(
            Modifier.fillMaxWidth().testTag("advice_line").background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(StatusIcons.Info, contentDescription = null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(JobCopy.NOT_ADVICE, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Candidates(index: Int, repos: List<GhRepo>, onIntent: (JobIntent) -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth().border(1.dp, line, MaterialTheme.shapes.small).clip(MaterialTheme.shapes.small)) {
        Text("Which repo did the video mean?", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        repos.forEach { r ->
            HorizontalDivider(color = line)
            Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) { onIntent(JobIntent.Pick(index, r)) }.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RadioButton(selected = false, onClick = null) // the whole row picks; the radio only says "choose one"
                    Column {
                        Text(r.fullName, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono, fontWeight = FontWeight.Medium))
                        Text(listOfNotNull("★ ${r.stars}", r.description).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        HorizontalDivider(color = line)
        TextButton(onClick = { onIntent(JobIntent.Pick(index, null)) }) { Text("None of these") }
    }
}

// From AI memory alone nothing is verified: amber ink, as in the design.
private fun verdictKind(f: Finding) = when (f.verdict) {
    Verdict.DISPUTED -> ChipKind.Bad
    Verdict.CONFIRMED, Verdict.SUPPORTED -> ChipKind.Ok
    else -> if (f.basis == Basis.AI_KNOWLEDGE && f.verdict != Verdict.NOT_FOUND) ChipKind.Warn else ChipKind.Unclear
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) = Text(text, modifier, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)

private fun icon(t: EntityType, subtype: MediaType? = null): ImageVector = when (t) {
    EntityType.REPO -> Icons.Filled.Build
    EntityType.APP -> Icons.Filled.Phone
    EntityType.PRODUCT -> Icons.Filled.ShoppingCart
    EntityType.BOOK -> Icons.Filled.List
    EntityType.PERSON -> Icons.Filled.Person
    EntityType.PLACE -> Icons.Filled.Place
    EntityType.RECIPE -> Icons.Filled.Favorite
    EntityType.MEDIA -> when (subtype) {
        MediaType.MOVIE -> StatusIcons.Movie
        MediaType.SERIES -> StatusIcons.Series
        MediaType.SONG -> StatusIcons.Song
        MediaType.GAME -> StatusIcons.Game
        MediaType.ANIME -> StatusIcons.Anime
        MediaType.OTHER, null -> Icons.Filled.PlayArrow
    }
    EntityType.CLAIM -> Icons.Filled.Warning
    EntityType.OTHER -> Icons.Filled.Info
}
