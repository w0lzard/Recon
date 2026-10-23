package dev.wolzard.recon.core.diagnostics

import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.Job
import dev.wolzard.recon.core.model.JobState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    private fun job(i: Int, state: JobState = JobState.FAILED, reason: FailureReason? = FailureReason.NETWORK) =
        Job(id = "id$i", sharedText = "x", state = state, failure = reason.takeIf { state == JobState.FAILED }, createdAt = i.toLong(), updatedAt = i.toLong())

    @Test fun textHasStepReasonAndTimingsButNoFullLinkOrSharedText() {
        val j = job(1).copy(
            sharedText = "my private note https://youtu.be/secretid", url = "https://www.youtube.com/watch?v=secretid&token=abc",
            failedStep = JobState.ANALYSING, startedAt = 1_000, downloadedAt = 5_000, sampledAt = 6_000, analysedAt = null,
            downloadBytes = 4_400_000, frameCount = 24, hasAudio = true, updatedAt = 9_000, userContext = "what song is this",
        )
        val t = Diagnostics.text(j, "Recon 0.5.0 · Android 36", path = "B", model = "gemini-web")
        assertTrue(t, "failed in ANALYSING" in t && "reason NETWORK" in t && "download 4.0 s, 4400000 bytes" in t && "24 frames" in t && "total 8.0 s" in t)
        assertTrue(t, "Recon 0.5.0 · Android 36" in t && "path B" in t)
        assertFalse(t, "secretid" in t || "private note" in t || "what song" in t || "token=abc" in t)
    }

    @Test fun detailIsScrubbedOfCookiesAndTokensAndCapped() {
        val detail = "IOException Cookie: __Secure-1PSID=abc123; SAPISID=zzz ghp_" + "a".repeat(30) + " Bearer eyJhbGciOi AQ.Ab8RN6IxyzXYZ12345 " + "x".repeat(400)
        val t = Diagnostics.text(job(1).copy(failureDetail = detail), "")
        val line = t.lines().first { it.startsWith("detail ") }.removePrefix("detail ")
        assertFalse(line, "abc123" in line || "zzz" in line || "ghp_" in line || "eyJhbGci" in line || "Ab8RN6I" in line)
        assertTrue(line.length <= 300)
    }

    @Test fun threeFailuresWithTheSameReasonAmongTheLastFiveRaiseTheBanner() {
        val jobs = listOf(job(1), job(2, JobState.DONE), job(3), job(4), job(5, JobState.DONE))
        assertEquals(FailureReason.NETWORK, Diagnostics.repeatedFailure(jobs))
    }

    @Test fun twoFailuresMixedReasonsOldFailuresAndUserErrorsDoNotRaiseIt() {
        assertNull(Diagnostics.repeatedFailure(listOf(job(1), job(2), job(3, reason = FailureReason.DOWNLOAD_FAILED))))
        assertNull(Diagnostics.repeatedFailure(listOf(job(1), job(2), job(3, JobState.DONE), job(4, JobState.DONE), job(5, JobState.DONE), job(6, JobState.DONE))))
        assertNull(Diagnostics.repeatedFailure((1..4).map { job(it, reason = FailureReason.UNSUPPORTED_LINK) }))
    }

    @Test fun runningJobsAreIgnored() {
        assertNull(Diagnostics.repeatedFailure(listOf(job(1), job(2), job(3, JobState.ANALYSING))))
    }
}
