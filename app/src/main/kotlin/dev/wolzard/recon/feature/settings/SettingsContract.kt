package dev.wolzard.recon.feature.settings

import dev.wolzard.recon.data.update.UpdateInfo
import dev.wolzard.recon.data.update.UpdateResult

data class SettingsState(
    val signedIn: Boolean = false,
    /** The account address Gemini's page showed at sign-in; null = unknown, shown as "Google account". */
    val email: String? = null,
    val hasGitHubToken: Boolean = false,
    val checkingUpdate: Boolean = false,
    /** Result of the last "Check for updates" tap, for the inline status; null until tapped. */
    val update: UpdateResult? = null,
    /** The banner's update: newer than this build and not put off with "Later". */
    val available: UpdateInfo? = null,
    val lastChecked: Long? = null,
    /** SHA-256 of this APK's signing certificate, "AB:CD:…"; null if Android wouldn't say. */
    val certSha256: String? = null,
    /** yt-dlp downloads its own updates (code); opt-in, see YtDlp. */
    val ytDlpAutoUpdate: Boolean = false,
)

sealed interface SettingsIntent {
    data object Back : SettingsIntent
    data object SignIn : SettingsIntent
    data object SignOut : SettingsIntent
    data class SaveGitHubToken(val token: String?) : SettingsIntent
    data object CheckForUpdates : SettingsIntent
    data class SetYtDlpAutoUpdate(val on: Boolean) : SettingsIntent
    data class OpenUpdate(val url: String) : SettingsIntent
    data class DismissUpdate(val version: String) : SettingsIntent
    data object OpenSource : SettingsIntent
    data object OpenNotice : SettingsIntent
}

sealed interface SettingsEffect {
    data object NavigateBack : SettingsEffect
    data object NavigateToSignIn : SettingsEffect
    data object NavigateToNotice : SettingsEffect
    /** The sign-in WebView keeps its own copy of the Google cookies and storage; the route clears them (D25). */
    data object ClearWebViewCookies : SettingsEffect
    data class OpenInBrowser(val url: String) : SettingsEffect
    data class OpenCustomTab(val url: String) : SettingsEffect
}
