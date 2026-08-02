package dev.wolzard.recon.feature.settings

import android.webkit.CookieManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.wolzard.recon.designsystem.openCustomTab
import dev.wolzard.recon.designsystem.openInBrowser
import dev.wolzard.recon.mvi.CollectEffects

/** Wires [SettingsViewModel] to [SettingsScreen]; navigation is the caller's. */
@Composable
fun SettingsRoute(vm: SettingsViewModel, onNotice: () -> Unit, onSignIn: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    CollectEffects(vm.effects) { effect ->
        when (effect) {
            SettingsEffect.NavigateToSignIn -> onSignIn()
            SettingsEffect.NavigateToNotice -> onNotice()
            SettingsEffect.ClearWebViewCookies -> CookieManager.getInstance().removeAllCookies(null)
            is SettingsEffect.OpenInBrowser -> ctx.openInBrowser(effect.url)
            is SettingsEffect.OpenCustomTab -> ctx.openCustomTab(effect.url)
        }
    }
    SettingsScreen(state, vm::onIntent)
}
