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
