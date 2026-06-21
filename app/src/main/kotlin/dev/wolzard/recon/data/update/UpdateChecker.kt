package dev.wolzard.recon.data.update

import android.content.SharedPreferences
import dev.wolzard.recon.core.update.Version
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

data class UpdateInfo(val version: String, val apkUrl: String, val releasePageUrl: String)

sealed interface UpdateResult {
    data class Newer(val info: UpdateInfo) : UpdateResult
    data object UpToDate : UpdateResult
    data object Failed : UpdateResult
}

/**
 * T15: GitHub Releases update check (unauthenticated; the API skips drafts and pre-releases). At most one request per
 * 24 h unless forced; the last result is kept in [prefs]. Links are only ever https://github.com/…, else the release page.
 */
class UpdateChecker(
    private val http: OkHttpClient,
    private val prefs: SharedPreferences,
    private val currentVersion: String,
    private val repo: String,
    private val abis: List<String>,
    private val clock: () -> Long,
    private val baseUrl: String = "https://api.github.com",
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _available = MutableStateFlow<UpdateInfo?>(null)
    /** The banner's update: newer than this build and not dismissed. */
    val available: StateFlow<UpdateInfo?> = _available.asStateFlow()

    /** Automatic check: never reports errors, only a newer, non-dismissed release. */
    suspend fun check(force: Boolean = false): UpdateInfo? {
        val info = if (!force && clock() - prefs.getLong(LAST_CHECK, 0) < DAY_MS) cached() else (checkNow() as? UpdateResult.Newer)?.info
        return info?.takeIf { isNewer(it.version) && it.version != prefs.getString(DISMISSED, null) }.also { _available.value = it }
    }

    /** Settings → "Check for updates": always asks GitHub and tells "up to date" apart from "couldn't check". */
    suspend fun checkNow(): UpdateResult {
        prefs.edit().putLong(LAST_CHECK, clock()).apply()
        val result = try { fetch() } catch (e: CancellationException) { throw e } catch (_: Exception) { UpdateResult.Failed }
        when (result) {
            is UpdateResult.Newer -> prefs.edit().putString(VERSION, result.info.version).putString(APK, result.info.apkUrl)
                .putString(PAGE, result.info.releasePageUrl).apply()
            UpdateResult.UpToDate -> prefs.edit().remove(VERSION).remove(APK).remove(PAGE).apply()
            UpdateResult.Failed -> Unit // keep the last good result
        }
        return result
    }

    fun dismiss(version: String) {
        prefs.edit().putString(DISMISSED, version).apply()
        if (_available.value?.version == version) _available.value = null
    }

    private suspend fun fetch(): UpdateResult {
        val request = Request.Builder().url("$baseUrl/repos/$repo/releases/latest")
            .header("User-Agent", "Recon").header("Accept", "application/vnd.github+json").build()
        val body = withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { if (it.isSuccessful) it.body.string() else null }
        } ?: return UpdateResult.Failed
        val release = json.decodeFromString(Release.serializer(), body)
        val version = Version.parse(release.tag)?.let { release.tag.removePrefix("v") } ?: return UpdateResult.Failed
        if (!isNewer(version)) return UpdateResult.UpToDate
        val page = release.htmlUrl?.takeIf(::isGitHubHttps) ?: "https://github.com/$repo/releases"
        val abi = abis.firstOrNull { it in SHIPPED_ABIS }
