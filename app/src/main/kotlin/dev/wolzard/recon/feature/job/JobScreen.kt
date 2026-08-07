package dev.wolzard.recon.feature.job

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
fun JobScreen(job: Job, result: JobResult?, now: Long, onIntent: (JobIntent) -> Unit) {
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
            if (question != null) YourQuestion(question)
            when {
                job.state == JobState.FAILED -> Failed(job, now, onIntent)
                job.state == JobState.DONE && result != null -> Done(result, question != null, onIntent)
                else -> Running(job, onIntent)
            }
        }
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
    InfoCard("TARGET", job.displayHost())
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
private fun InfoCard(label: String, value: String) {
    Column(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(label)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun YourQuestion(text: String) {
    SectionLabel("YOUR QUESTION")
    Text(text, Modifier.testTag("your_question"), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun AnswerCard(answer: Answer?) {
    val kind = when (answer?.status) {
        AnswerStatus.VERIFIED -> ChipKind.Ok
        AnswerStatus.UNVERIFIED -> ChipKind.Warn
        else -> ChipKind.Unclear
    }
    OutlinedCard(Modifier.fillMaxWidth().testTag("answer_card"), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("ANSWER", Modifier.weight(1f))
                StatusChip(JobCopy.answerStatus(answer?.status ?: AnswerStatus.UNCLEAR), kind)
                answer?.let { StatusChip("${(it.confidence * 100).roundToInt()}%", ChipKind.Neutral) }
            }
            Text(answer?.text ?: JobCopy.NO_ANSWER, style = MaterialTheme.typography.bodyLarge)
            answer?.sources?.forEach { Text("• $it", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun Done(result: JobResult, asked: Boolean, onIntent: (JobIntent) -> Unit) {
    if (asked) AnswerCard(result.analysis.answer)
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

    if (result.sources.isNotEmpty()) {
        SectionLabel("Sources")
        Column {
            result.sources.forEachIndexed { i, src ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onIntent(JobIntent.Open(src.uri)) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("${i + 1}", Modifier.width(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(src.title, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Icon(StatusIcons.OpenInNew, contentDescription = "Opens in browser", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
                Icon(icon(f.entity.type), contentDescription = f.entity.type.wire, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(f.entity.type.wire.uppercase(), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                StatusChip(JobCopy.verdict(f.verdict), verdictKind)
            }
            Text(f.title, style = MaterialTheme.typography.titleMedium)
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

private fun icon(t: EntityType): ImageVector = when (t) {
    EntityType.REPO -> Icons.Filled.Build
    EntityType.APP -> Icons.Filled.Phone
    EntityType.PRODUCT -> Icons.Filled.ShoppingCart
    EntityType.BOOK -> Icons.Filled.List
    EntityType.PERSON -> Icons.Filled.Person
    EntityType.PLACE -> Icons.Filled.Place
    EntityType.RECIPE -> Icons.Filled.Favorite
    EntityType.MEDIA -> Icons.Filled.PlayArrow
    EntityType.CLAIM -> Icons.Filled.Warning
    EntityType.OTHER -> Icons.Filled.Info
}
