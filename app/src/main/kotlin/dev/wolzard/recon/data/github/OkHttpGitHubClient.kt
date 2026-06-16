package dev.wolzard.recon.data.github

import dev.wolzard.recon.core.model.GhRepo
import dev.wolzard.recon.core.port.GitHubClient
import dev.wolzard.recon.core.port.GitHubRateLimited
import dev.wolzard.recon.core.port.Secret
import dev.wolzard.recon.core.port.SecretStore
import dev.wolzard.recon.data.gemini.HttpResult
import dev.wolzard.recon.data.gemini.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** GitHub REST, unauthenticated unless the user saved a token (60/h → 5,000/h; search 10/min). */
class OkHttpGitHubClient(
    private val http: OkHttpClient,
    private val secrets: SecretStore,
    private val baseUrl: String = "https://api.github.com",
) : GitHubClient {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class SearchPage(val items: List<GhRepo> = emptyList())

    override suspend fun repo(owner: String, name: String): GhRepo? =
        get("/repos/$owner/$name")?.let { json.decodeFromString(GhRepo.serializer(), it.body) }

    override suspend fun readme(owner: String, name: String): String? =
        get("/repos/$owner/$name/readme", accept = "application/vnd.github.raw")?.body?.take(README_CHARS)

    override suspend fun search(query: String): List<GhRepo> {
        val url = "$baseUrl/search/repositories".toHttpUrl().newBuilder()
            .addQueryParameter("q", "$query in:name").addQueryParameter("per_page", "10").build()
        val r = get(url.toString(), absolute = true) ?: return emptyList()
        return json.decodeFromString(SearchPage.serializer(), r.body).items
    }

    /** null on 404; throws [GitHubRateLimited] or [IOException] on other failures. */
    private suspend fun get(path: String, accept: String = "application/vnd.github+json", absolute: Boolean = false): HttpResult? {
        val request = Request.Builder().url(if (absolute) path else "$baseUrl$path")
            .header("Accept", accept)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Recon")
            .apply { secrets.get(Secret.GITHUB_TOKEN)?.let { header("Authorization", "Bearer $it") } }
            .get().build()
        val r = http.newCall(request).await()
        return when {
            r.code in 200..299 -> r
            r.code == 404 -> null
            r.code == 429 || (r.code == 403 && r.headers["X-RateLimit-Remaining"] == "0") ->
                throw GitHubRateLimited(r.headers["X-RateLimit-Reset"]?.toLongOrNull()?.times(1000))
            else -> throw IOException("GitHub HTTP ${r.code}")
        }
    }

    private companion object { const val README_CHARS = 8_000 }
}
