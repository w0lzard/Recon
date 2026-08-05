package dev.wolzard.recon.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.wolzard.recon.feature.context.ContextRoute
import dev.wolzard.recon.feature.home.HomeRoute
import dev.wolzard.recon.feature.job.JobRoute
import dev.wolzard.recon.feature.notice.NoticeScreen
import dev.wolzard.recon.feature.settings.SettingsRoute
import dev.wolzard.recon.feature.signin.SignInRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable object HomeDestination
@Serializable object SettingsDestination
@Serializable object SignInDestination
@Serializable object NoticeDestination
@Serializable data class JobDestination(val id: String)
@Serializable data class ContextDestination(val link: String)

/** T16: the personal-use notice comes before everything, then Gemini sign-in if signed out (D22). */
fun startDestination(noticeNeeded: Boolean, signedIn: Boolean): Any = when {
    noticeNeeded -> NoticeDestination
    !signedIn -> SignInDestination
    else -> HomeDestination
}

/**
 * The app's navigation graph. Screens are wired by their feature's Route; this only decides where each one leads.
 * [pendingJob] is a job to open (from a share or a notification); [pendingLink] a valid link to add context to (D23); [onNoticeAccepted] records the acceptance.
 */
@Composable
fun ReconNavHost(
    start: Any,
    pendingJob: MutableStateFlow<String?>,
    pendingLink: MutableStateFlow<String?>,
    clock: () -> Long,
    noticeNeeded: () -> Boolean,
    signedIn: () -> Boolean,
    onNoticeAccepted: () -> Unit,
) {
    val nav = rememberNavController()
    val pending by pendingJob.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let { nav.navigate(JobDestination(it)) { launchSingleTop = true }; pendingJob.value = null }
    }
    val link by pendingLink.collectAsStateWithLifecycle()
    LaunchedEffect(link) {
        link?.let { nav.navigate(ContextDestination(it)); pendingLink.value = null }
    }
    Scaffold { padding ->
        NavHost(nav, startDestination = start, modifier = Modifier.padding(padding)) {
            composable<HomeDestination> {
                HomeRoute(koinViewModel(), onOpenJob = { nav.navigate(JobDestination(it)) }, onOpenSettings = { nav.navigate(SettingsDestination) }, onAddContext = { nav.navigate(ContextDestination(it)) })
            }
            composable<JobDestination> { entry ->
                val id = entry.toRoute<JobDestination>().id
                JobRoute(
                    koinViewModel(key = id) { parametersOf(id) }, clock,
                    onBack = { nav.popBackStack() },
                    onSignIn = { nav.navigate(SignInDestination) },
                    onSettings = { nav.navigate(SettingsDestination) },
                )
            }
            composable<ContextDestination> { entry ->
                val link = entry.toRoute<ContextDestination>().link
                ContextRoute(
                    koinViewModel(key = link) { parametersOf(link) },
                    onOpenJob = { id -> nav.navigate(JobDestination(id)) { popUpTo<ContextDestination> { inclusive = true } } },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<NoticeDestination> {
                NoticeScreen {
                    if (!noticeNeeded()) { nav.popBackStack(); return@NoticeScreen } // re-read from Settings
                    onNoticeAccepted()
                    nav.navigate(startDestination(false, signedIn())) { popUpTo(nav.graph.id) { inclusive = true } }
                }
            }
            composable<SignInDestination> {
                SignInRoute(koinViewModel()) { nav.navigate(HomeDestination) { popUpTo(nav.graph.id) { inclusive = true } } }
            }
            composable<SettingsDestination> {
                SettingsRoute(koinViewModel(), onNotice = { nav.navigate(NoticeDestination) }, onSignIn = { nav.navigate(SignInDestination) })
            }
        }
    }
}
