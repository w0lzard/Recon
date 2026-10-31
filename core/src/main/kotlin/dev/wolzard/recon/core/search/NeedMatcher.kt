package dev.wolzard.recon.core.search

/**
 * Library search helpers (D39), no database and no model: the FTS4 expression for a typed query, and the on-device
 * typo fallback that runs only when full-text search finds nothing.
 */
object NeedMatcher {
    private val WORD = Regex("[\\p{L}\\p{N}]+")

    fun words(text: String): List<String> = WORD.findAll(text.lowercase()).map { it.value }.toList()

    /**
     * Every word must match, the last as a prefix too. FTS4 has no quoted prefix ("w"*), so the last word is bare w*;
     * a typed OR or NEAR stays a plain word because only the exact keyword is an operator. "" for no words.
     */
    fun ftsQuery(query: String): String {
        val w = WORD.findAll(query).map { it.value }.toList()
        return w.mapIndexed { i, t -> if (i == w.lastIndex) "$t*" else "\"$t\"" }.joinToString(" ")
    }

    /** Typos allowed in a query word: none under 3 letters, 1 for 3 to 4, 2 for 5 or more. */
    fun maxDistance(length: Int) = when { length < 3 -> 0; length < 5 -> 1; else -> 2 }

    /** Damerau-Levenshtein (adjacent swaps count once; each substring edited at most once). */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
        }
        return d[a.length][b.length]
    }

    /** [texts] = an entry's name and aliases. */
    class Candidate(val id: Long, val texts: List<String>)

    /**
     * Ids of candidates where every query word is within [maxDistance] of some word of the candidate (the last query word
     * may also be a typo'd start of a longer word), best first. Words under 3 letters must match exactly.
     */
    fun typoMatches(query: String, candidates: List<Candidate>): List<Long> {
        val q = words(query)
        if (q.isEmpty()) return emptyList()
        return candidates.mapNotNull { c ->
            val cw = c.texts.flatMap(::words).distinct()
            var total = 0
            for ((i, w) in q.withIndex()) {
                val max = maxDistance(w.length)
                val best = cw.minOfOrNull { word ->
                    val whole = distance(w, word)
                    if (i == q.lastIndex && word.length > w.length) minOf(whole, distance(w, word.take(w.length))) else whole
                } ?: return@mapNotNull null
                if (best > max) return@mapNotNull null
                total += best
            }
            c.id to total
        }.sortedWith(compareBy({ it.second }, { it.first })).map { it.first }
    }
}
