package dev.wolzard.recon.core.model

/**
 * D26: what the platform says about a shared link, from one yt-dlp metadata call. Best effort and display-only:
 * any field can be missing, and the analysis never reads it.
 */
data class LinkPreview(val title: String?, val durationSec: Long?, val thumbnailUrl: String?) {
    val isEmpty get() = title == null && durationSec == null && thumbnailUrl == null

    companion object {
        /** m:ss under an hour, h:mm:ss from an hour; null for a missing or negative length. */
        fun formatDuration(sec: Long?): String? {
            if (sec == null || sec < 0) return null
            val h = sec / 3600; val m = sec % 3600 / 60; val s = sec % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
    }
}
