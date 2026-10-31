package dev.wolzard.recon.data.media

/**
 * When a yt-dlp run fails for a reason a newer yt-dlp could fix, update it once and run again once. The bundled
 * yt-dlp ages out (the 0.18.1 library ships 2025.11.12, which fails on today's YouTube), and an update is the only fix.
 * Not worth an update: the network is down, the link is not supported, or the video is private or behind a login.
 * At most one update per [MIN_GAP_MS], counted from the last attempt whether or not it worked.
 */
object UpdateOnFailure {
    const val MIN_GAP_MS = 24L * 60 * 60 * 1000

    val NETWORK_RE = Regex("""Errno (7|-2|-3|101|110|111)|timed out|Network is unreachable|Temporary failure in name resolution""")
    private val UNSUPPORTED_RE = Regex("""Unsupported URL""", RegexOption.IGNORE_CASE)
    private val PRIVATE_RE = Regex("""\bprivate\b|login required|sign in to confirm your age|members-only""", RegexOption.IGNORE_CASE)

    fun eligible(stderr: String) = !NETWORK_RE.containsMatchIn(stderr) && !UNSUPPORTED_RE.containsMatchIn(stderr) && !PRIVATE_RE.containsMatchIn(stderr)

    fun due(lastAttemptMs: Long?, nowMs: Long) = lastAttemptMs == null || nowMs - lastAttemptMs >= MIN_GAP_MS

    /** [update] returns false when it failed; then the first failure is rethrown and nothing is retried. */
    suspend fun <T> run(nowMs: () -> Long, lastAttemptMs: () -> Long?, update: suspend () -> Boolean, block: suspend () -> T): T =
        try {
            block()
        } catch (e: YtDlpException) {
            if (!eligible(e.message.orEmpty()) || !due(lastAttemptMs(), nowMs()) || !update()) throw e
            block()
        }
}
