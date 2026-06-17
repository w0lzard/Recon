package dev.wolzard.recon.data.link

import dev.wolzard.recon.core.port.LinkResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/** HEAD, then GET if HEAD fails; ≤ 5 redirects, [timeout] per request; the input URL on any failure. */
class OkHttpLinkResolver(client: OkHttpClient, timeout: Duration = 5.seconds) : LinkResolver {
    private val http = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(timeout.toJavaDuration())
        .build()

    override suspend fun resolve(url: String): String = withContext(Dispatchers.IO) {
        runCatching { follow(url, "HEAD") }.recoverCatching { follow(url, "GET") }.getOrDefault(url)
    }

    private fun follow(url: String, method: String): String {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            val request = Request.Builder().url(current).method(method, null).build()
            http.newCall(request).execute().use { resp ->
                when {
                    resp.isRedirect -> {
                        val location = resp.header("Location") ?: throw IOException("redirect without Location")
                        current = resp.request.url.resolve(location)?.toString() ?: throw IOException("bad Location")
                    }
                    resp.isSuccessful -> return current
                    else -> throw IOException("HTTP ${resp.code}")
                }
            }
        }
        throw IOException("more than $MAX_REDIRECTS redirects")
    }

    private companion object { const val MAX_REDIRECTS = 5 }
}
