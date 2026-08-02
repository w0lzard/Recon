package dev.wolzard.recon.feature.job

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.wolzard.recon.designsystem.openCustomTab
import dev.wolzard.recon.mvi.CollectEffects

/** Wires [JobViewModel] to [JobScreen]. [clock] decides whether Retry is enabled yet. */
@Composable
fun JobRoute(vm: JobViewModel, clock: () -> Long, onBack: () -> Unit, onSignIn: () -> Unit, onSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    CollectEffects(vm.effects) { effect ->
        when (effect) {
            JobEffect.NavigateBack -> onBack()
            JobEffect.NavigateToSignIn -> onSignIn()
            JobEffect.NavigateToSettings -> onSettings()
            is JobEffect.OpenUrl -> if (effect.url.startsWith("https://")) ctx.openCustomTab(effect.url)
        }
    }
    state.job?.let { JobScreen(it, state.result, clock(), vm::onIntent) }
}
