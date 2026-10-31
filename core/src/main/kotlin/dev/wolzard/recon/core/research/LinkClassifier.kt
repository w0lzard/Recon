package dev.wolzard.recon.core.research

import java.net.URI

enum class LinkType { REPO, DOCS, PACKAGE, PAPER, WEBSITE }

/** Sorts a link by its address alone (D38). Anything that is not an http(s) link with a host is not a link: null. */
object LinkClassifier {
    private val REPO = setOf("github.com", "gitlab.com")
    private val PACKAGE = setOf("pypi.org", "npmjs.com", "crates.io", "maven.org", "huggingface.co")
    private val PAPER = setOf("arxiv.org", "doi.org")
    private val DOCS = setOf("readthedocs.io", "gitbook.io")

    fun classify(url: String): LinkType? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        val host = uri.host?.lowercase() ?: return null
        val path = uri.path?.lowercase().orEmpty()
        val bare = host.removePrefix("www.")
        fun on(domains: Set<String>) = bare in domains
        return when {
            on(REPO) -> LinkType.REPO
            on(PACKAGE) -> LinkType.PACKAGE
            on(PAPER) -> LinkType.PAPER
            on(DOCS) || DOCS.any { bare.endsWith(".$it") } || bare.startsWith("docs.") || "/wiki/" in path || path.endsWith("/wiki") || path.startsWith("/documentation") -> LinkType.DOCS
            else -> LinkType.WEBSITE
        }
    }
}
