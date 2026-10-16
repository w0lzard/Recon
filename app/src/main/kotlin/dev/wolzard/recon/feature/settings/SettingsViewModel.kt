package dev.wolzard.recon.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.viewModelScope
import dev.wolzard.recon.BuildConfig
import dev.wolzard.recon.core.port.Secret
import dev.wolzard.recon.core.port.SecretStore
import dev.wolzard.recon.data.gemini.GeminiSession
import dev.wolzard.recon.data.update.UpdateChecker
import dev.wolzard.recon.mvi.MviViewModel
import dev.wolzard.recon.data.update.UpdateResult
import kotlinx.coroutines.launch
import java.security.MessageDigest

class SettingsViewModel(
    private val secrets: SecretStore,
    private val updates: UpdateChecker? = null,
    certSha256: String? = null,
    ytDlpAutoUpdate: Boolean = false,
    private val setYtDlpAutoUpdate: (Boolean) -> Unit = {},
) : MviViewModel<SettingsState, SettingsIntent, SettingsEffect>(SettingsState(certSha256 = certSha256, ytDlpAutoUpdate = ytDlpAutoUpdate)) {
    init {
        refresh()
        updates?.let { u -> viewModelScope.launch { u.available.collect { setState { copy(available = it) } } } }
    }

    override fun onIntent(intent: SettingsIntent) = when (intent) {
        SettingsIntent.Back -> send(SettingsEffect.NavigateBack)
        SettingsIntent.SignIn -> send(SettingsEffect.NavigateToSignIn)
        SettingsIntent.SignOut -> signOut()
        is SettingsIntent.SaveGitHubToken -> saveGitHubToken(intent.token)
        SettingsIntent.CheckForUpdates -> checkForUpdates()
        is SettingsIntent.SetYtDlpAutoUpdate -> { setYtDlpAutoUpdate(intent.on); setState { copy(ytDlpAutoUpdate = intent.on) } }
        is SettingsIntent.OpenUpdate -> send(SettingsEffect.OpenInBrowser(intent.url))
        is SettingsIntent.DismissUpdate -> updates?.dismiss(intent.version) ?: Unit
        SettingsIntent.OpenSource -> send(SettingsEffect.OpenCustomTab("https://github.com/${BuildConfig.RELEASES_REPO}"))
        SettingsIntent.OpenNotice -> send(SettingsEffect.NavigateToNotice)
    }

    private fun refresh() {
        val signedIn = GeminiSession.signedIn(secrets)
        setState {
            copy(
                signedIn = signedIn,
                email = secrets.get(Secret.GEMINI_EMAIL)?.takeIf { signedIn },
                hasGitHubToken = secrets.get(Secret.GITHUB_TOKEN) != null,
                lastChecked = updates?.lastChecked(),
            )
        }
    }

    /** Forgets the saved Google cookies and address; the WebView's own copy is cleared by the route. */
    private fun signOut() { GeminiSession.clear(secrets); refresh(); send(SettingsEffect.ClearWebViewCookies) }

    private fun saveGitHubToken(token: String?) { secrets.put(Secret.GITHUB_TOKEN, token?.trim()?.ifEmpty { null }); refresh() }

    /** "Check for updates": forced, result shown inline. */
    private fun checkForUpdates() {
        val u = updates ?: return
        setState { copy(checkingUpdate = true, update = null) }
        viewModelScope.launch {
            val r = u.checkNow()
            if (r is UpdateResult.Newer) u.check() // refresh the banner from the stored result
            setState { copy(checkingUpdate = false, update = r, lastChecked = u.lastChecked()) }
        }
    }
}

/** This APK's signing certificate as [fingerprint]; null if Android won't say. */
fun signingSha256(ctx: Context): String? = runCatching {
    val pm = ctx.packageManager
    val cert = if (Build.VERSION.SDK_INT >= 28) {
        pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()
    } else {
        @Suppress("DEPRECATION") pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
    }
    cert?.let { fingerprint(it.toByteArray()) }
}.getOrNull()

/** SHA-256 as colon-separated upper-case hex pairs, the form `apksigner` and the README print. */
fun fingerprint(der: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(der).joinToString(":") { "%02X".format(it) }
