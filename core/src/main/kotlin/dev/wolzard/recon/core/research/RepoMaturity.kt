package dev.wolzard.recon.core.research

enum class Maturity { ESTABLISHED, GROWING, EXPERIMENTAL, INACTIVE, UNKNOWN }

/** Maturity of a GitHub repo from API facts only (D38); no model is asked. */
object RepoMaturity {
    /** At or above this many stars, with a push inside a year, a repo is ESTABLISHED. */
    const val STABLE_STARS = 1_000
    const val GROWING_STARS = 100
    private const val DAY = 86_400_000L
    private const val ACTIVE_DAYS = 365

    /**
     * In order: archived is INACTIVE whatever else is known; a missing push date or star count is UNKNOWN (no badge,
     * never a guess); no push for more than [ACTIVE_DAYS] days is INACTIVE; then [STABLE_STARS] and up is ESTABLISHED,
     * [GROWING_STARS] and up GROWING, the rest EXPERIMENTAL. The release tag is shown as a fact but does not change the label.
     */
    fun of(stars: Int?, pushedAtMs: Long?, archived: Boolean, nowMs: Long): Maturity = when {
        archived -> Maturity.INACTIVE
        stars == null || pushedAtMs == null -> Maturity.UNKNOWN
        (nowMs - pushedAtMs) / DAY > ACTIVE_DAYS -> Maturity.INACTIVE
        stars >= STABLE_STARS -> Maturity.ESTABLISHED
        stars >= GROWING_STARS -> Maturity.GROWING
        else -> Maturity.EXPERIMENTAL
    }
}
