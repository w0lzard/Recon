package dev.wolzard.recon.feature.job

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
        "knowledge", "web" -> "Couldn't look things up (quota, busy or connection). Items show only what the video said." +
            (retryAt?.let { " Try after ${time(it)}." }.orEmpty())
        "github" -> "GitHub limit reached. Repos were looked up another way, or not checked."
        "budget" -> "Analysis needed a second try, so lookups were skipped to save your Gemini usage."
        else -> null
    }

    fun verdict(v: Verdict) = when (v) {
        Verdict.CONFIRMED -> "Confirmed"
        Verdict.BEST_MATCH -> "Best match"
        Verdict.NOT_FOUND -> "Not found"
        Verdict.NOT_CHECKED -> "Not checked"
        Verdict.SUPPORTED -> "Supported"
        Verdict.DISPUTED -> "Disputed"
        Verdict.UNCLEAR -> "Unclear"
    }

    fun basis(b: Basis) = when (b) {
        Basis.GITHUB_API -> "From GitHub"
        Basis.WIKIPEDIA -> "From Wikipedia"
        Basis.WEB_SOURCES -> "From web sources"
        Basis.AI_KNOWLEDGE -> "Unverified, from AI knowledge"
        Basis.VIDEO_ONLY -> "Only what the video said"
    }

    fun howToTryLabel(t: EntityType) = when (t) {
        EntityType.REPO -> "Install"
        EntityType.APP, EntityType.PRODUCT -> "Get it"
        EntityType.BOOK -> "Read"
        EntityType.PLACE -> "Visit"
        EntityType.RECIPE -> "Ingredients & steps"
        else -> "Next step"
    }
}
