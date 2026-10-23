package dev.wolzard.recon.core.diagnostics

import dev.wolzard.recon.core.link.hostOf
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobState

/**
 * D35: the text behind "Copy diagnostics". Facts about the run only: ids, step, reason, times, sizes and the link's
 * host. Never the full link, the shared text, the user's note, cookies or tokens; error details are scrubbed and capped.
 */
object Diagnostics {
    private const val DETAIL_MAX = 300
    private val secrets = listOf(
        Regex("(?i)(cookie|set-cookie|authorization|bearer)\\s*[:=]?\\s*\\S+"),
        Regex("(?i)\\b(SID|HSID|SSID|APISID|SAPISID|SIDCC|__Secure-[A-Za-z0-9_]+|SNlM0e|at)=[^;\\s]+"),
        Regex("\\b(ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{20,}"),
        Regex("\\bgithub_pat_[A-Za-z0-9_]{20,}"),
        Regex("\\bAQ\\.[A-Za-z0-9_\\-]{10,}"),
        Regex("\\bAIza[0-9A-Za-z_\\-]{20,}"),
    )

    fun redact(text: String): String = secrets.fold(text.replace(Regex("\\s+"), " ").trim()) { s, r -> r.replace(s, "[removed]") }.take(DETAIL_MAX)

    fun text(job: Job, environment: String, path: String? = null, model: String? = null): String {
        fun secs(from: Long?, to: Long?) = if (from == null || to == null || to < from) "-" else "%.1f s".format(java.util.Locale.US, (to - from) / 1000.0)
        val done = job.state == JobState.DONE || job.state == JobState.FAILED
        return listOfNotNull(
            "Recon diagnostics",
            environment.takeIf { it.isNotBlank() },
            "job ${job.id.take(8)}",
            "state ${job.state}" + (job.failedStep?.let { " (failed in $it)" }.orEmpty()),
            job.failure?.let { "reason $it" },
            job.failureDetail?.let { "detail ${redact(it)}" },
            "link ${job.url?.let(::hostOf) ?: job.localUri?.let { "shared file" } ?: "-"}",
            path?.let { "path $it" },
            model?.let { "model $it" },
            "download ${secs(job.startedAt, job.downloadedAt)}" + (job.downloadBytes?.let { ", $it bytes" }.orEmpty()),
            "frames/sample ${secs(job.downloadedAt, job.sampledAt)}" + (job.frameCount?.let { ", $it frames" }.orEmpty()) + (job.hasAudio?.let { ", audio $it" }.orEmpty()),
            "analyse ${secs(job.sampledAt, job.analysedAt)}",
            if (done) "total ${secs(job.startedAt, job.updatedAt)}" else null,
        ).joinToString("\n")
    }

    /**
     * The reason shared by at least [threshold] of the [window] newest finished jobs (DONE or FAILED), or null.
     * UNSUPPORTED_LINK is the user's input, not a fault, and QUOTA_EXCEEDED has its own waiting screen.
     */
    fun repeatedFailure(jobs: List<Job>, window: Int = 5, threshold: Int = 3): FailureReason? =
        jobs.filter { it.state == JobState.DONE || it.state == JobState.FAILED }.sortedByDescending { it.createdAt }.take(window)
            .filter { it.state == JobState.FAILED }.mapNotNull { it.failure }
            .filter { it != FailureReason.UNSUPPORTED_LINK && it != FailureReason.QUOTA_EXCEEDED }
            .groupingBy { it }.eachCount().filterValues { it >= threshold }.maxByOrNull { it.value }?.key
}
