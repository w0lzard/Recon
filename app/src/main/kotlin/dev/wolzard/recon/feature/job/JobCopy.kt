package dev.wolzard.recon.feature.job

import dev.wolzard.recon.core.model.AnswerStatus
import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.ClaimDomain
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.Finding
import dev.wolzard.recon.core.model.Verdict
import dev.wolzard.recon.designsystem.ChipKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The UI contract of the Failure table and degraded banners (plan → State machine), in one testable place. */
object JobCopy {
    const val NOTHING_FOUND = "Nothing to look up in this video"
    /** D24: shown once under the claims list; each sensitive claim has its own [adviceLine]. */
    const val NOT_ADVICE = "Not professional advice. Ask a qualified professional."
    const val TRY_LATER = "Try again later"
    const val NOTHING_FOUND_DETAIL = "No repos, products, people, places or claims in the frames or audio."
    const val SHARE_FILE_HELP = "Some videos can't be downloaded (private, or the site needs a login). " +
        "Save or download the video in the app you watched it in, then share the video file itself to Recon."
    const val LEAVE_SCREEN = "You can leave this screen. Recon keeps working and sends a notification when the sweep is complete."
    const val NO_FRAMES_TITLE = "No frames extracted"
    const val NO_FRAMES_BODY = "The download finished, but no frames could be read from it. The post may be private, removed, or not a video."
    const val NO_FRAMES_HINT = "Retrying often works if the post loaded slowly. If it fails again, open the link to check it plays."

    private val hhmm = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    fun time(epochMs: Long): String = hhmm.format(Instant.ofEpochMilli(epochMs))

    /** "JOB 7F3A-21": a short, stable handle for the job, from its id. */
    fun shortId(id: String): String {
        val h = id.filter { it.isLetterOrDigit() }.uppercase().padEnd(6, '0')
        return "JOB ${h.take(4)}-${h.substring(4, 6)}"
    }

    /** Step and elapsed times, "0:04"; null when either end is unknown. */
    fun elapsed(from: Long?, to: Long?): String? {
        if (from == null || to == null || to < from) return null
        val s = (to - from) / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    /** "4.2 MB" (decimal megabytes, one place), as on the step card. */
    fun megabytes(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)

    /** GitHub-style star counts: "212", "4.1k". */
    fun stars(n: Int): String = if (n < 1000) "$n" else String.format(Locale.US, "%.1fk", n / 1000.0).replace(".0k", "k")

    /** "in about 1 h 58 m": the wait until a known reset, rounded down to the minute (at least 1 m). */
    fun waitFor(ms: Long): String {
        val m = (ms / 60_000).coerceAtLeast(1)
        return "in about " + if (m >= 60) "${m / 60} h ${m % 60} m" else "$m m"
    }

    fun failureMessage(reason: FailureReason, retryAt: Long?, detail: String? = null): String = when (reason) {
        FailureReason.UNSUPPORTED_LINK -> "Recon couldn't find a link in what you shared."
        FailureReason.DOWNLOAD_FAILED -> "Couldn't download this video (it may be private or need login)."
        FailureReason.VIDEO_UNREADABLE -> "This video file couldn't be read."
        FailureReason.QUOTA_EXCEEDED -> "You've reached your Gemini usage limit. " + (retryAt?.let { "Try again after ${time(it)}." } ?: "Try again later.")
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

    /**
     * The design's status vocabulary (1a): Verified, Supported, Unverified, Unclear, Contradicted. AI memory alone, or a
     * match with no source behind it, is never Verified or Supported.
     */
    fun verdictChip(f: Finding): Pair<String, ChipKind> = when {
        f.verdict == Verdict.DISPUTED -> "Contradicted" to ChipKind.Bad
        f.verdict == Verdict.UNCLEAR || f.verdict == Verdict.NOT_FOUND -> "Unclear" to ChipKind.Unclear
        f.basis == Basis.AI_KNOWLEDGE || f.basis == Basis.VIDEO_ONLY || f.verdict == Verdict.NOT_CHECKED -> "Unverified" to ChipKind.Warn
        f.verdict == Verdict.SUPPORTED -> "Supported" to ChipKind.Ok
        f.verdict == Verdict.CONFIRMED -> "Verified" to ChipKind.Ok
        f.primaryLink != null -> "Verified" to ChipKind.Ok // BEST_MATCH with an API source
        else -> "Unverified" to ChipKind.Warn
    }

    /** Design 1i: the line under a health, legal or financial claim. */
    fun adviceLine(d: ClaimDomain): String = when (d) {
        ClaimDomain.HEALTH -> "Health claim"
        ClaimDomain.LEGAL -> "Legal claim"
        ClaimDomain.FINANCIAL -> "Financial claim"
        ClaimDomain.OTHER -> "Claim"
    } + ". Not professional advice; ask a qualified professional."

    fun answerStatus(s: AnswerStatus) = when (s) {
        AnswerStatus.VERIFIED -> "Verified"
        AnswerStatus.UNVERIFIED -> "Unverified"
        AnswerStatus.UNCLEAR -> "Unclear"
    }

    const val NO_ANSWER = "Recon couldn't answer this from the video."
    const val NO_SOURCE = "No source"

    /** D26: "MEDIA · MOVIE"; the sub-type only for media, and never "OTHER". */
    fun typeLabel(type: dev.wolzard.recon.core.model.EntityType, subtype: dev.wolzard.recon.core.model.MediaType?): String =
        listOfNotNull(type.wire.uppercase(), subtype?.takeIf { type == dev.wolzard.recon.core.model.EntityType.MEDIA && it != dev.wolzard.recon.core.model.MediaType.OTHER }?.wire?.uppercase())
            .joinToString(" · ")

    /** D26: "ANSWER · MEDIA · SONG". Only allow-listed parts appear (ResultParser); a missing part is left out, never guessed. */
    fun answerLabel(a: dev.wolzard.recon.core.model.Answer?): String = listOfNotNull(
        "ANSWER", a?.kind?.wire?.uppercase(), a?.subtype?.takeIf { it != dev.wolzard.recon.core.model.MediaType.OTHER }?.wire?.uppercase(),
    ).joinToString(" · ")
}
