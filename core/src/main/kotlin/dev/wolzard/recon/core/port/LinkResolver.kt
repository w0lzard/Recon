package dev.wolzard.recon.core.port

interface LinkResolver {
    /** Follows redirects; returns the original URL on any failure. */
    suspend fun resolve(url: String): String
}
