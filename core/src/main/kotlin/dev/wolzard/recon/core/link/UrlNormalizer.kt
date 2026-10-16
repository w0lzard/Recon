package dev.wolzard.recon.core.link

import dev.wolzard.recon.core.port.LinkResolver
import java.net.URI

class UrlNormalizer(private val resolver: LinkResolver) {

    suspend fun normalize(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return "web:$trimmed"
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removeSuffix(".") ?: return "web:$trimmed"

        // YouTube
        if (host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be") {
            val id = extractYouTubeId(host, uri)
            if (id != null) return "youtube:$id"
        }

        // Instagram
        if (host == "instagram.com" || host.endsWith(".instagram.com")) {
            val id = extractInstagramShortcode(uri)
            if (id != null) return "instagram:$id"
        }

        // TikTok
        if (host == "tiktok.com" || host.endsWith(".tiktok.com")) {
            var target = trimmed
            if (host == "vm.tiktok.com" || host == "vt.tiktok.com") {
                target = resolver.resolve(trimmed)
            }
            val id = extractTikTokId(target)
            if (id != null) return "tiktok:$id"
        }

        // X (Twitter)
        if (host == "x.com" || host.endsWith(".x.com") || host == "twitter.com" || host.endsWith(".twitter.com")) {
            val id = extractXStatusId(uri)
            if (id != null) return "x:$id"
        }

        // Generic fallback
        return genericFallback(uri, host)
    }

    private fun extractYouTubeId(host: String, uri: URI): String? {
        val path = uri.path.orEmpty().trim('/')
        if (host == "youtu.be") {
            return path.split('/').firstOrNull()?.takeIf { it.isNotEmpty() }
        }
        val segments = path.split('/')
        if (segments.size >= 2 && (segments[0] == "shorts" || segments[0] == "live")) {
            return segments[1].takeIf { it.isNotEmpty() }
        }
        val query = parseQuery(uri.rawQuery)
        return query["v"]?.takeIf { it.isNotEmpty() }
    }

    private fun extractInstagramShortcode(uri: URI): String? {
        val path = uri.path.orEmpty().trim('/')
        val segments = path.split('/')
        if (segments.size >= 2 && (segments[0] == "reel" || segments[0] == "p" || segments[0] == "tv")) {
            return segments[1].takeIf { it.isNotEmpty() }
        }
        return null
    }

    private fun extractTikTokId(url: String): String? {
        val match = TIKTOK_VIDEO_RE.find(url)
        return match?.groupValues?.get(1)
    }

    private fun extractXStatusId(uri: URI): String? {
        val path = uri.path.orEmpty().trim('/')
        val segments = path.split('/')
        val statusIndex = segments.indexOf("status")
        if (statusIndex >= 0 && statusIndex + 1 < segments.size) {
            return segments[statusIndex + 1].takeIf { it.isNotEmpty() }
        }
        return null
    }

    private fun genericFallback(uri: URI, cleanHost: String): String {
        val scheme = uri.scheme?.lowercase() ?: "https"
        val rawPath = uri.rawPath.orEmpty().trimEnd('/')
        val query = parseQuery(uri.rawQuery)
            .filterKeys { !TRACKING_PARAMS.contains(it.lowercase()) && !it.lowercase().startsWith("utm_") }

        val queryString = if (query.isNotEmpty()) {
            "?" + query.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
        } else ""

        return "web:$scheme://$cleanHost$rawPath$queryString"
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        rawQuery.split('&').forEach { param ->
            val eq = param.indexOf('=')
            if (eq > 0) {
                result[param.substring(0, eq)] = param.substring(eq + 1)
            } else if (param.isNotEmpty()) {
                result[param] = ""
            }
        }
        return result
    }

    companion object {
        private val TIKTOK_VIDEO_RE = Regex("""/video/(\d+)""")
        private val TRACKING_PARAMS = setOf(
            "ref", "fbclid", "si", "igsh", "feature", "source", "s", "t", "spm",
        )
    }
}
