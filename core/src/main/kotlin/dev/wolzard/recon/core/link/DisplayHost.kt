package dev.wolzard.recon.core.link

import dev.wolzard.recon.core.model.Job
import java.net.URI

/** What a job is called in lists and headers: the link's host, "Shared video" for a file, or "Link" if there is none. */
fun Job.displayHost(): String {
    if (localUri != null) return "Shared video"
    val link = url ?: LinkRouter.extractUrl(sharedText)
    return link?.let { runCatching { URI(it).host?.removePrefix("www.")?.removePrefix("m.") }.getOrNull() } ?: "Link"
}
