package dev.wolzard.recon.core.research

import dev.wolzard.recon.core.research.RepoMaturity.STABLE_STARS
import org.junit.Assert.assertEquals
import org.junit.Test

class RepoMaturityTest {
    private val now = 1_000L * 86_400_000L
    private fun m(stars: Int?, idleDays: Long?, archived: Boolean = false) =
        RepoMaturity.of(stars, idleDays?.let { now - it * 86_400_000L }, archived, now)

    @Test fun stableStarsConstantIsOneThousand() = assertEquals(1_000, STABLE_STARS)

    @Test fun archivedOverridesEverythingEvenMissingFacts() {
        assertEquals(Maturity.INACTIVE, m(90_000, 0, archived = true))
        assertEquals(Maturity.INACTIVE, m(null, null, archived = true))
    }

    @Test fun missingPushDateOrStarsIsUnknownNeverExperimental() {
        assertEquals(Maturity.UNKNOWN, m(5_000, null))
        assertEquals(Maturity.UNKNOWN, m(0, null))
        assertEquals(Maturity.UNKNOWN, m(null, 1))
    }

    @Test fun pushBoundaryIsAYear() {
        assertEquals(Maturity.ESTABLISHED, m(STABLE_STARS, 365))
        assertEquals(Maturity.INACTIVE, m(STABLE_STARS, 366))
        assertEquals(Maturity.INACTIVE, m(90_000, 366))
        assertEquals(Maturity.INACTIVE, m(0, 366))
    }

    @Test fun starBoundaries() {
        assertEquals(Maturity.ESTABLISHED, m(STABLE_STARS, 1))
        assertEquals(Maturity.GROWING, m(STABLE_STARS - 1, 1))
        assertEquals(Maturity.GROWING, m(100, 1))
        assertEquals(Maturity.EXPERIMENTAL, m(99, 1))
        assertEquals(Maturity.EXPERIMENTAL, m(0, 0))
    }

    @Test fun stalenessWithinAYearDoesNotChangeTheTier() {
        assertEquals(Maturity.ESTABLISHED, m(STABLE_STARS, 300))
        assertEquals(Maturity.GROWING, m(500, 120))
        assertEquals(Maturity.EXPERIMENTAL, m(10, 300))
    }

    @Test fun aPushDatedInTheFutureCountsAsFresh() = assertEquals(Maturity.ESTABLISHED, m(STABLE_STARS, -3))
}
