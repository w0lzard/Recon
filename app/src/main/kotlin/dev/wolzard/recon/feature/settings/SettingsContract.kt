package dev.wolzard.recon.feature.settings

import dev.wolzard.recon.data.update.UpdateResult

data class SettingsState(
    val signedIn: Boolean = false,
    val hasGitHubToken: Boolean = false,
    val checkingUpdate: Boolean = false,
    val update: UpdateResult? = null,
)

sealed interface SettingsIntent {
    data object SignIn : SettingsIntent
    data object SignOut : SettingsIntent
    data class SaveGitHubToken(val token: String?) : SettingsIntent
    data object CheckForUpdates : SettingsIntent
    data class OpenUpdate(val url: String) : SettingsIntent
    data object OpenReleases : SettingsIntent
    data object OpenNotice : SettingsIntent
}

sealed interface SettingsEffect {
    data object NavigateToSignIn : SettingsEffect
    data object NavigateToNotice : SettingsEffect
    /** The sign-in WebView keeps its own copy of the Google cookies; the route clears it. */
    data object ClearWebViewCookies : SettingsEffect
    data class OpenInBrowser(val url: String) : SettingsEffect
    data class OpenCustomTab(val url: String) : SettingsEffect
}
