package dev.wolzard.recon

import androidx.compose.runtime.CompositionLocalProvider
import dev.wolzard.recon.data.link.ThumbnailLoader
import dev.wolzard.recon.designsystem.LocalThumbnails
import dev.wolzard.recon.data.notice.Notice
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.wolzard.recon.core.port.SecretStore
import dev.wolzard.recon.data.gemini.GeminiSession
import dev.wolzard.recon.data.update.UpdateChecker
import dev.wolzard.recon.designsystem.openCustomTab
import dev.wolzard.recon.designsystem.openInBrowser
import dev.wolzard.recon.designsystem.UpdateBanner
import androidx.compose.ui.unit.dp
import dev.wolzard.recon.feature.home.HomeScreen
import dev.wolzard.recon.feature.home.HomeViewModel
import dev.wolzard.recon.feature.job.JobIntent
import dev.wolzard.recon.feature.job.JobScreen
import dev.wolzard.recon.feature.job.JobViewModel
import dev.wolzard.recon.feature.settings.SettingsScreen
import dev.wolzard.recon.feature.signin.SignInScreen
import dev.wolzard.recon.work.ShareGate
import androidx.core.os.BundleCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.wolzard.recon.designsystem.ReconTheme
import dev.wolzard.recon.navigation.ReconNavHost
import dev.wolzard.recon.navigation.startDestination
import dev.wolzard.recon.work.ShareIntake
import dev.wolzard.recon.work.ShareOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_JOB_ID = "dev.wolzard.recon.JOB_ID"
        private const val HELD_SHARE = "heldShare"
    }

    private val secrets: SecretStore by inject()
    private val intake: ShareIntake by inject()
    private val clock: () -> Long by inject()
    private val updates: UpdateChecker by inject()
    private val thumbnails: ThumbnailLoader by inject()
    private val notice by lazy { Notice(getSharedPreferences(Notice.PREFS, MODE_PRIVATE)) }
    private val shares by lazy { ShareGate(notice, intake::accept) }
    /** A job to open: from a share or a notification's deep link. */
    private val openJob = MutableStateFlow<String?>(null)
    /** A valid link waiting for its optional context (D23). */
    private val openContext = MutableStateFlow<String?>(null)

    private fun show(outcome: ShareOutcome?) = when (outcome) {
        is ShareOutcome.Started -> openJob.value = outcome.jobId
        is ShareOutcome.NeedsContext -> openContext.value = outcome.link
        null -> Unit
    }

    /** True once the first Compose frame is up; the splash stays until then so there is no blank flash. */
    private var firstFrameDrawn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !firstFrameDrawn }
        splash.setOnExitAnimationListener { screen ->
            screen.view.animate().alpha(0f).setDuration(200).withEndAction { screen.remove() }.start()
        }
        enableEdgeToEdge()
        shares.held = savedInstanceState?.let { BundleCompat.getParcelable(it, HELD_SHARE, Intent::class.java) }
        if (savedInstanceState == null) handle(intent)
        lifecycleScope.launch { updates.check() } // throttled to once per 24 h; errors stay silent
        val signedIn = { GeminiSession.signedIn(secrets) }
        val start = startDestination(notice.needed, GeminiSession.atLaunch(secrets))
        setContent {
            ReconTheme {
                LaunchedEffect(Unit) { firstFrameDrawn = true }
                CompositionLocalProvider(LocalThumbnails provides thumbnails::load) {
                    ReconNavHost(
                        start, openJob, openContext, clock,
                        noticeNeeded = { notice.needed },
                        signedIn = signedIn,
                        onNoticeAccepted = { lifecycleScope.launch { show(shares.accepted()) } },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        shares.held?.let { outState.putParcelable(HELD_SHARE, it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent?.getStringExtra(EXTRA_JOB_ID)?.let { openJob.value = it; return }
        if (intent?.action != Intent.ACTION_SEND) return
        // The job runs regardless of the answer; notifications are optional.
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        // Copy/create right away: a shared video's URI grant is temporary (R14).
        lifecycleScope.launch { show(runCatching { shares.share(intent) }.getOrNull()) }
    }
}
