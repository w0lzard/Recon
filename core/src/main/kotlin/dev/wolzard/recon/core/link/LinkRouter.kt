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

    /** Only absolute http(s) URLs with a real host may reach the network or yt-dlp (no file:, intent:, javascript:, …). */
    fun isWebUrl(url: String): Boolean = runCatching { URI(url) }.getOrNull()
