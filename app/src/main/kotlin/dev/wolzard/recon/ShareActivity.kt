package dev.wolzard.recon

import kotlinx.coroutines.flow.drop
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.BottomSheetScaffold
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import dev.wolzard.recon.core.port.SecretStore
import dev.wolzard.recon.data.gemini.GeminiSession
import dev.wolzard.recon.data.link.ThumbnailLoader
import dev.wolzard.recon.data.notice.Notice
import dev.wolzard.recon.designsystem.LocalThumbnails
import dev.wolzard.recon.designsystem.ReconTheme
import dev.wolzard.recon.feature.context.ContextRoute
import dev.wolzard.recon.feature.job.JobRoute
import dev.wolzard.recon.work.ShareIntake
import dev.wolzard.recon.work.ShareOutcome
import dev.wolzard.recon.work.Visible
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import java.io.File

/** What the share sheet shows. */
sealed interface SheetStep {
    data object Loading : SheetStep
    data class Link(val link: String) : SheetStep
    data class VideoFile(val localUri: String, val sharedText: String) : SheetStep
    data class Job(val id: String) : SheetStep
    data object Nothing : SheetStep
}

class ShareSheetViewModel(private val intake: ShareIntake) : ViewModel() {
    val step = MutableStateFlow<SheetStep>(SheetStep.Loading)

    fun accept(intent: Intent) = viewModelScope.launch {
        step.value = when (val o = runCatching { intake.accept(intent, fileContext = true) }.getOrNull()) {
            is ShareOutcome.NeedsContext -> SheetStep.Link(o.link)
            is ShareOutcome.FileNeedsContext -> SheetStep.VideoFile(o.localUri, o.sharedText)
            is ShareOutcome.Started -> SheetStep.Job(o.jobId)
            null -> SheetStep.Nothing
        }
    }

    fun open(jobId: String) { step.value = SheetStep.Job(jobId) }
}

/**
 * Share sheet: a share (link or video file) opens a bottom sheet over the app it came from, with Add context and
 * then the job and its result. Closing the sheet leaves the job running; ResultPresenter brings the result back.
 * Until the notice is accepted and the user is signed in, a share goes to the full app as before.
 */
class ShareActivity : ComponentActivity() {
    companion object {
        const val EXTRA_JOB_ID = "dev.wolzard.recon.SHEET_JOB_ID"
        fun openJob(context: Context, jobId: String): Intent = Intent(context, ShareActivity::class.java).putExtra(EXTRA_JOB_ID, jobId)
    }

    private val vm: ShareSheetViewModel by viewModel()
    private val secrets: SecretStore by inject()
    private val thumbnails: ThumbnailLoader by inject()
    private val notice by lazy { Notice(getSharedPreferences(Notice.PREFS, MODE_PRIVATE)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null && !route(intent)) return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    vm.step.collect { step -> Visible.sheetJob = (step as? SheetStep.Job)?.id?.also { Visible.lastShownJob = it } }
                } finally { Visible.sheetJob = null }
            }
        }
        setContent {
            ReconTheme {
                CompositionLocalProvider(LocalThumbnails provides thumbnails::load) {
                    val step by vm.step.collectAsStateWithLifecycle()
                    if (step == SheetStep.Nothing) {
                        Toast.makeText(this, "Recon found nothing to look up in that.", Toast.LENGTH_SHORT).show()
                        finish()
                    } else ShareSheet(step, onDismiss = ::dismiss, onJob = vm::open, onOpenApp = ::openApp)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        route(intent)
    }

    /** @return false if the share went to the full app instead. */
    private fun route(intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_SEND) {
            // Only Recon starts the activity directly (notifications, ResultPresenter); shares arrive via the alias.
            intent.getStringExtra(EXTRA_JOB_ID)?.let { vm.open(it); return true }
            finish(); return false
        }
        if (notice.needed || !GeminiSession.signedIn(secrets)) {
            // Setup first: the full app takes the share as before (held until the notice is accepted).
            startActivity(Intent(intent).setClass(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
            finish()
            return false
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        vm.accept(intent)
        return true
    }

    /** Closing before Start: a copied video file nobody will analyse is deleted. The job, if any, keeps running. */
    private fun dismiss() {
        (vm.step.value as? SheetStep.VideoFile)?.let { File(it.localUri).parentFile?.deleteRecursively() }
        finish()
    }

    private fun openApp(jobId: String?) {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply { jobId?.let { putExtra(MainActivity.EXTRA_JOB_ID, it) } })
        finish()
    }
}

/** Resting height of the sheet, as a share of the screen; dragging up opens it to [EXPANDED]. */
const val PEEK = 0.4f
private const val EXPANDED = 0.92f

/**
 * The sheet rests at [PEEK] of the screen and drags up to [EXPANDED]. Its content is laid out in the part that is
 * actually visible, so Skip/Start and the job screen fit the resting height. It expands by itself when the keyboard
 * opens, and closes when dragged below the resting height or when the dimmed area above it is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShareSheet(step: SheetStep, onDismiss: () -> Unit, onJob: (String) -> Unit, onOpenApp: (String?) -> Unit) {
    val sheet = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = false)
    val scaffold = rememberBottomSheetScaffoldState(bottomSheetState = sheet)
    LaunchedEffect(sheet) { snapshotFlow { sheet.currentValue }.drop(1).collect { if (it == SheetValue.Hidden) onDismiss() } }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) { if (imeVisible) sheet.expand() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val full = maxHeight
        val density = LocalDensity.current
        BottomSheetScaffold(
            scaffoldState = scaffold,
            sheetPeekHeight = full * PEEK,
            sheetContainerColor = MaterialTheme.colorScheme.background,
            containerColor = Color.Transparent,
            modifier = Modifier.testTag("share_sheet"),
            sheetContent = {
                // Fixed height for the sheet (its anchors depend on it); the content inside follows the visible part.
                Box(Modifier.fillMaxWidth().height(full * EXPANDED)) {
                    val visible = runCatching { with(density) { (full.toPx() - sheet.requireOffset()).toDp() } }.getOrDefault(full * PEEK)
                    Box(Modifier.fillMaxWidth().height((visible - 48.dp).coerceIn(0.dp, full * EXPANDED)).navigationBarsPadding()) {
                        SheetContent(step, onDismiss, onJob, onOpenApp)
                    }
                }
            },
        ) {
            // The app behind shows through, dimmed; a tap above the sheet closes it.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).clickable(onClick = onDismiss, indication = null, interactionSource = null))
        }
    }
}

@Composable
private fun SheetContent(step: SheetStep, onDismiss: () -> Unit, onJob: (String) -> Unit, onOpenApp: (String?) -> Unit) = when (step) {
    is SheetStep.Link -> ContextRoute(koinViewModel(key = "ctx-${step.link}") { parametersOf(step.link) }, onOpenJob = onJob, onBack = onDismiss)
    is SheetStep.VideoFile -> ContextRoute(
        koinViewModel(key = "ctx-${step.localUri}") { parametersOf(step.sharedText, step.localUri) }, onOpenJob = onJob, onBack = onDismiss,
    )
    is SheetStep.Job -> Column {
        OverlayPrompt()
        JobRoute(
            koinViewModel(key = step.id) { parametersOf(step.id) }, koinInject(),
            onBack = onDismiss, onSignIn = { onOpenApp(step.id) }, onSettings = { onOpenApp(null) },
        )
    }
    SheetStep.Loading, SheetStep.Nothing -> Unit
}

/**
 * Asked once, while a job runs: with "Display over other apps", a result that arrives after the sheet is closed
 * pops up by itself instead of waiting in a notification (ResultPresenter).
 */
@Composable
private fun OverlayPrompt() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("sheet", Context.MODE_PRIVATE) }
    var allowed by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var dismissed by remember { mutableStateOf(prefs.getBoolean("overlayAsked", false)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) allowed = Settings.canDrawOverlays(ctx) }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    if (allowed || dismissed) return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).padding(12.dp).testTag("overlay_prompt"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("You can close this. To have the result pop up here when it's ready, allow Recon to display over other apps.", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(shape = MaterialTheme.shapes.small, onClick = {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            }) { Text("Allow") }
            TextButton(onClick = { prefs.edit().putBoolean("overlayAsked", true).apply(); dismissed = true }) { Text("Not now") }
        }
    }
}
