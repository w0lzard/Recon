package dev.wolzard.recon.data.media

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateOnFailureTest {
    private val day = UpdateOnFailure.MIN_GAP_MS
    private var now = 10 * day
    private var last: Long? = null
    private var updates = 0
    private var runs = 0
    private var updateWorks = true

    /** Fails with [stderr] on the first [failures] runs, then returns "ok". */
    private suspend fun go(stderr: String, failures: Int = 1): String = UpdateOnFailure.run(
        nowMs = { now }, lastAttemptMs = { last },
        update = { updates++; last = now; updateWorks },
    ) { if (runs++ < failures) throw YtDlpException(stderr) else "ok" }

    private val generic = "ERROR: 'NoneType' object has no attribute 'lower'"

    @Test fun otherFailureUpdatesOnceAndRetriesOnce() = runTest {
        assertEquals("ok", go(generic))
        assertEquals(1, updates); assertEquals(2, runs)
    }

    @Test fun aSecondFailureAfterTheRetryIsNotRetriedAgain() = runTest {
        assertThrows(YtDlpException::class.java) { kotlinx.coroutines.runBlocking { go(generic, failures = 5) } }
        assertEquals(1, updates); assertEquals(2, runs)
    }

    @Test fun throttleHoldsForADayThenReopens() = runTest {
        last = now - day + 1
        assertThrows(YtDlpException::class.java) { kotlinx.coroutines.runBlocking { go(generic) } }
        assertEquals(0, updates); assertEquals(1, runs)
        now += 1; runs = 0
        assertEquals("ok", go(generic))
        assertEquals(1, updates)
    }

    @Test fun aFailedUpdateRethrowsWithoutRetrying() = runTest {
        updateWorks = false
        assertThrows(YtDlpException::class.java) { kotlinx.coroutines.runBlocking { go(generic) } }
        assertEquals(1, updates); assertEquals(1, runs)
        assertEquals(now, last)   // the attempt counts for the throttle even though it failed
    }

    @Test fun networkUnsupportedAndPrivateFailuresDoNotUpdate() = runTest {
        val skipped = listOf(
            "ERROR: [Errno -3] Temporary failure in name resolution", "ERROR: unable to download: timed out", "Network is unreachable",
            "ERROR: Unsupported URL: https://example.com/x",
            "ERROR: [youtube] abc: Private video. Sign in if you've been granted access", "ERROR: This video is private",
            "ERROR: [Instagram] x: Requested content is not available, rate-limit reached or login required",
            "ERROR: Sign in to confirm your age", "ERROR: This video is available to members-only",
        )
        for (s in skipped) {
            runs = 0
            assertThrows(s, YtDlpException::class.java) { kotlinx.coroutines.runBlocking { go(s) } }
            assertEquals(s, 0, updates)
        }
    }

    @Test fun eligibleAndDueRules() {
        assertTrue(UpdateOnFailure.eligible(generic))
        assertTrue(UpdateOnFailure.eligible("ERROR: [youtube] x: Sign in to confirm you're not a bot"))
        assertFalse(UpdateOnFailure.eligible("ERROR: Unsupported URL: x"))
        assertTrue(UpdateOnFailure.due(null, 5)); assertTrue(UpdateOnFailure.due(0, day)); assertFalse(UpdateOnFailure.due(1, day))
    }
}
