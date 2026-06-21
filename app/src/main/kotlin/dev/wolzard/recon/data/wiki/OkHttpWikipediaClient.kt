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

    override suspend fun search(lang: String, query: String): List<WikiHit> {
        val url = "${baseUrl(safe(lang))}/w/rest.php/v1/search/page".toHttpUrl().newBuilder()
            .addQueryParameter("q", query).addQueryParameter("limit", "3").build()
        val body = get(url.toString()) ?: return emptyList()
        return json.decodeFromString(SearchPage.serializer(), body).pages.map { WikiHit(it.key, it.title) }
    }

    override suspend fun summary(lang: String, key: String): WikiSummary? {
        val url = "${baseUrl(safe(lang))}/api/rest_v1/page/summary/".toHttpUrl().newBuilder().addPathSegment(key).build()
        val body = get(url.toString()) ?: return null
        val s = json.decodeFromString(Summary.serializer(), body)
        return WikiSummary(s.title, s.extract, s.contentUrls?.mobile?.page ?: s.contentUrls?.desktop?.page, s.type == "disambiguation")
    }

    private suspend fun get(url: String): String? {
        val r = http.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "Recon/${BuildConfig.VERSION_NAME} (https://github.com/${BuildConfig.RELEASES_REPO})")
                .header("Accept", "application/json")
                .get().build(),
        ).await()
        return when (r.code) {
            in 200..299 -> r.body
            404 -> null
            else -> throw IOException("Wikipedia HTTP ${r.code}")
        }
    }

    private fun safe(lang: String) = lang.takeIf { it.matches(Regex("^[a-z]{2,3}$")) } ?: "en"
}
