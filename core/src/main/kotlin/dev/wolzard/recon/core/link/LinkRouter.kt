package dev.wolzard.recon.core.link

import java.net.URI

sealed interface Route {
    data class YouTube(val url: String) : Route
    data class Other(val url: String) : Route
}

/** Pure link handling for shared text (Review Focus #1). */
object LinkRouter {
    private val urlRe = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private const val TRAILING = ".,;:!?…'\"]}>*"
    private val youTubeHosts = setOf("youtube.com", "youtu.be")
    private val knownPlatforms = setOf(
        "youtube.com", "youtu.be", "instagram.com", "tiktok.com", "x.com", "twitter.com",
        "facebook.com", "fb.watch", "reddit.com",
    )

    /** First valid http(s) URL in [text], with trailing punctuation trimmed; null if none. */
    fun extractUrl(text: String): String? = urlRe.findAll(text)
        .map { trim(it.value) }
        .firstOrNull { host(it) != null }

    /** The one check for a share or paste (D23): the first http(s) link in [text], or null. Run before the context screen and again at Start. */
    fun validLink(text: String): String? = extractUrl(text)?.takeIf(::isWebUrl)

    /** Only absolute http(s) URLs with a real host may reach the network or yt-dlp (no file:, intent:, javascript:, …). */
    fun isWebUrl(url: String): Boolean = runCatching { URI(url) }.getOrNull()
        ?.let { it.scheme?.lowercase() in setOf("http", "https") && host(url) != null } ?: false

    fun classify(url: String): Route = if (matches(host(url), youTubeHosts)) Route.YouTube(url) else Route.Other(url)

    /** Short links and unknown hosts are resolved (redirects followed) before classifying. */
    fun needsResolving(url: String): Boolean = !matches(host(url), knownPlatforms)

    private fun trim(raw: String): String {
        var s = raw
        while (s.isNotEmpty()) {
            val c = s.last()
            s = when {
                c in TRAILING -> s.dropLast(1)
                c == ')' && s.count { it == ')' } > s.count { it == '(' } -> s.dropLast(1)
                else -> return s
            }
        }
        return s
    }

    private fun host(url: String): String? =
        runCatching { URI(url).host?.lowercase()?.removeSuffix(".") }.getOrNull()?.takeIf { it.contains('.') }

    // Exact host or any subdomain of it (www., m., music., vm., …).
    private fun matches(host: String?, set: Set<String>) = host != null && set.any { host == it || host.endsWith(".$it") }
}
