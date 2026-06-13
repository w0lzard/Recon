package dev.wolzard.recon

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
import dev.wolzard.recon.core.port.Secret
import dev.wolzard.recon.core.port.SecretStore
import dev.wolzard.recon.data.update.UpdateChecker
import dev.wolzard.recon.ui.common.openCustomTab
import dev.wolzard.recon.ui.common.openInBrowser
import dev.wolzard.recon.ui.home.UpdateBanner
import androidx.compose.ui.unit.dp
import dev.wolzard.recon.ui.home.HomeScreen
import dev.wolzard.recon.ui.home.HomeViewModel
import dev.wolzard.recon.ui.job.JobAction
import dev.wolzard.recon.ui.job.JobScreen
import dev.wolzard.recon.ui.job.JobViewModel
import dev.wolzard.recon.ui.settings.SettingsScreen
import dev.wolzard.recon.ui.setup.SignInScreen
import dev.wolzard.recon.ui.setup.NoticeScreen
import dev.wolzard.recon.work.ShareGate
import androidx.core.os.BundleCompat
import dev.wolzard.recon.ui.theme.ReconTheme
import dev.wolzard.recon.work.ShareIntake
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable object Home
@Serializable object Settings
@Serializable object SignIn
@Serializable object Notice
@Serializable data class JobRoute(val id: String)

/** T16: the personal-use notice comes before everything, then Gemini sign-in if signed out (D22). */
fun startDestination(noticeNeeded: Boolean, signedIn: Boolean): Any = when {
    noticeNeeded -> Notice
    !signedIn -> SignIn
    else -> Home
}

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_JOB_ID = "dev.wolzard.recon.JOB_ID"
        private const val HELD_SHARE = "heldShare"
    }

    private val secrets: SecretStore by inject()
    private val intake: ShareIntake by inject()
    private val clock: () -> Long by inject()
    private val updates: UpdateChecker by inject()
    private val notice by lazy { dev.wolzard.recon.ui.setup.Notice(getSharedPreferences(dev.wolzard.recon.ui.setup.Notice.PREFS, MODE_PRIVATE)) }
    private val shares by lazy { ShareGate(notice, intake::accept) }
    /** A job to open: from a share or a notification's deep link. */
    private val openJob = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        shares.held = savedInstanceState?.let { BundleCompat.getParcelable(it, HELD_SHARE, Intent::class.java) }
        if (savedInstanceState == null) handle(intent)
        lifecycleScope.launch { updates.check() } // throttled to once per 24 h; errors stay silent
        val signedIn = { secrets.get(Secret.GEMINI_COOKIES) != null }
        val start = startDestination(notice.needed, signedIn())
        setContent {
            ReconTheme {
                val nav = rememberNavController()
                val pending by openJob.collectAsStateWithLifecycle()
                LaunchedEffect(pending) {
                    pending?.let { nav.navigate(JobRoute(it)) { launchSingleTop = true }; openJob.value = null }
                }
                Scaffold { padding ->
                    NavHost(nav, startDestination = start, modifier = Modifier.padding(padding)) {
                        composable<Home> {
                            val vm: HomeViewModel = koinViewModel()
                            val rows by vm.rows.collectAsStateWithLifecycle()
                            val update by updates.available.collectAsStateWithLifecycle()
                            HomeScreen(rows, onOpen = { nav.navigate(JobRoute(it)) }, onSettings = { nav.navigate(Settings) }) {
                                update?.let { u ->
                                    UpdateBanner(u, ::openInBrowser, { updates.dismiss(u.version) }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                                }
                            }
                        }
                        composable<JobRoute> { entry ->
                            val id = entry.toRoute<JobRoute>().id
                            val vm: JobViewModel = koinViewModel(key = id) { parametersOf(id) }
                            val job by vm.job.collectAsStateWithLifecycle()
                            job?.let { j ->
                                JobScreen(j, vm.result(j), clock()) { a ->
                                    when (a) {
                                        JobAction.SignIn -> nav.navigate(SignIn)
                                        JobAction.OpenSettings -> nav.navigate(Settings)
                                        JobAction.Back -> nav.popBackStack()
                                        JobAction.Cancel -> { vm.act(a); nav.popBackStack() }
                                        is JobAction.Open -> if (a.url.startsWith("https://")) openCustomTab(a.url)
                                        else -> vm.act(a)
                                    }
                                }
                            }
                        }
                        composable<Notice> {
                            NoticeScreen {
                                if (!notice.needed) { nav.popBackStack(); return@NoticeScreen } // re-read from Settings
                                lifecycleScope.launch { shares.accepted()?.let { openJob.value = it } }
                                nav.navigate(startDestination(false, signedIn())) { popUpTo(nav.graph.id) { inclusive = true } }
                            }
                        }
                        composable<SignIn> {
                            SignInScreen(koinViewModel()) {
                                nav.navigate(Home) { popUpTo(nav.graph.id) { inclusive = true } }
                            }
                        }
                        composable<Settings> { SettingsScreen(koinViewModel(), onNotice = { nav.navigate(Notice) }) { nav.navigate(SignIn) } }
                    }
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
        lifecycleScope.launch { runCatching { shares.share(intent) }.getOrNull()?.let { openJob.value = it } }
    }
}
