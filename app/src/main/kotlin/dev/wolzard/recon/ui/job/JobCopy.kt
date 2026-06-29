package dev.wolzard.recon.ui.job

import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.Verdict
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The UI contract of the Failure table and degraded banners (plan → State machine), in one testable place. */
object JobCopy {
    const val NOTHING_FOUND = "Nothing specific to look up in this video"
    const val SHARE_FILE_HELP = "Some videos can't be downloaded (private, or the site needs a login). " +
        "Save or download the video in the app you watched it in, then share the video file itself to Recon."

    private val hhmm = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    fun time(epochMs: Long): String = hhmm.format(Instant.ofEpochMilli(epochMs))

    fun failureMessage(reason: FailureReason, retryAt: Long?, detail: String? = null): String = when (reason) {
        FailureReason.UNSUPPORTED_LINK -> "Recon couldn't find a link in what you shared."
        FailureReason.DOWNLOAD_FAILED -> "Couldn't download this video (it may be private or need login)."
        FailureReason.VIDEO_UNREADABLE -> "This video file couldn't be read."
        FailureReason.QUOTA_EXCEEDED -> "You've reached your Gemini usage limit. " + (retryAt?.let { "Try again after ${time(it)}." } ?: "Try again later today.")
        FailureReason.API_KEY_MISSING -> "Sign in to Gemini to start."
        FailureReason.API_KEY_INVALID -> "Your Gemini sign-in has expired. Sign in again."
        FailureReason.NETWORK -> "No connection or the request timed out."
        FailureReason.MODEL_OUTPUT_INVALID -> "The AI gave an unreadable answer."
        FailureReason.MODEL_BUSY -> "Google's AI is busy right now. Try again in a few minutes."
        FailureReason.UNKNOWN -> "Something went wrong (${detail ?: "unknown error"})."
    }

    /** Degraded banner text, or null for silent ones (wikipedia). */
    fun degradedBanner(id: String, retryAt: Long?): String? = when (id) {
