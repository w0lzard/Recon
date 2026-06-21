package dev.wolzard.recon.data.wiki

import dev.wolzard.recon.BuildConfig
import dev.wolzard.recon.core.port.WikiHit
import dev.wolzard.recon.core.port.WikiSummary
import dev.wolzard.recon.core.port.WikipediaClient
import dev.wolzard.recon.data.gemini.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Keyless Wikipedia REST (T10). The language tag is validated before it becomes part of a host name. */
class OkHttpWikipediaClient(
    private val http: OkHttpClient,
    private val baseUrl: (String) -> String = { lang -> "https://$lang.wikipedia.org" },
) : WikipediaClient {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class SearchPage(val pages: List<Hit> = emptyList())
    @Serializable private data class Hit(val key: String, val title: String)
    @Serializable private data class Summary(
        val type: String = "standard",
        val title: String = "",
        val extract: String = "",
        @SerialName("content_urls") val contentUrls: ContentUrls? = null,
    )
    @Serializable private data class ContentUrls(val mobile: Page? = null, val desktop: Page? = null)
    @Serializable private data class Page(val page: String? = null)
