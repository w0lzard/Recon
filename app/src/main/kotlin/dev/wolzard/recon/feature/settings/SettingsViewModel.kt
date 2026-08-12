package dev.wolzard.recon.feature.settings

import androidx.lifecycle.viewModelScope
import dev.wolzard.recon.BuildConfig
import dev.wolzard.recon.core.port.Secret
import dev.wolzard.recon.core.port.SecretStore
import dev.wolzard.recon.data.gemini.GeminiSession
import dev.wolzard.recon.data.update.UpdateChecker
import dev.wolzard.recon.mvi.MviViewModel
import dev.wolzard.recon.data.update.UpdateResult
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val secrets: SecretStore,
    private val updates: UpdateChecker? = null,
) : MviViewModel<SettingsState, SettingsIntent, SettingsEffect>(SettingsState()) {
    init { refresh() }

    override fun onIntent(intent: SettingsIntent) = when (intent) {
        SettingsIntent.SignIn -> send(SettingsEffect.NavigateToSignIn)
        SettingsIntent.SignOut -> signOut()
        is SettingsIntent.SaveGitHubToken -> saveGitHubToken(intent.token)
        SettingsIntent.CheckForUpdates -> checkForUpdates()
        is SettingsIntent.OpenUpdate -> send(SettingsEffect.OpenInBrowser(intent.url))
        SettingsIntent.OpenReleases -> send(SettingsEffect.OpenCustomTab("https://github.com/${BuildConfig.RELEASES_REPO}/releases"))
        SettingsIntent.OpenNotice -> send(SettingsEffect.NavigateToNotice)
    }

    private fun refresh() {
        setState {
            copy(
                signedIn = GeminiSession.signedIn(secrets),
                hasGitHubToken = secrets.get(Secret.GITHUB_TOKEN) != null,
            )
        }
    }

    /** Forgets the saved Google cookies; the WebView's own copy is cleared by the route. */
    private fun signOut() { GeminiSession.clear(secrets); refresh(); send(SettingsEffect.ClearWebViewCookies) }

    private fun saveGitHubToken(token: String?) { secrets.put(Secret.GITHUB_TOKEN, token?.trim()?.ifEmpty { null }); refresh() }

    /** About → "Check for updates": forced, result shown inline. */
    private fun checkForUpdates() {
        val u = updates ?: return
        setState { copy(checkingUpdate = true, update = null) }
        viewModelScope.launch {
            val r = u.checkNow()
            if (r is UpdateResult.Newer) u.check() // refresh Home's banner from the stored result
            setState { copy(checkingUpdate = false, update = r) }
        }
    }
}
