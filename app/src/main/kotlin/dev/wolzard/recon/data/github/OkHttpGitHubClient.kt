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
