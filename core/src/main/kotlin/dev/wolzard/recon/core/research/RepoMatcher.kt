package dev.wolzard.recon.core.research

/**
 * Fuzzy GitHub matching for spoken or misread repo names (T9, R19). GitHub search ANDs its terms, so one garbled
 * word zeroes a query (r2: "…jinchin latex" → 0); the fallback ORs the words and lets [similarity] rank the hits.
 */
object RepoMatcher {
    private const val MIN_TOKEN = 3
    private const val MAX_OR_TERMS = 5          // GitHub allows at most 5 boolean operators per query
    private val separators = Regex("[-_./\\s]+")
    private val camel = Regex("(?<=[a-z])(?=[A-Z][a-z])")

    /** Qualifier tokens first, then name tokens; lowercase, ≥ 3 chars, deduped. */
    fun tokens(name: String, qualifier: String?): List<String> =
        listOfNotNull(qualifier, name).flatMap { part -> part.split(separators).flatMap(::splitCamel) }
            .map { it.lowercase() }.filter { it.length >= MIN_TOKEN && it.all(Char::isLetterOrDigit) }.distinct()

    // "gitHubUserStatus" → git|Hub|User|Status, but "LaTeX"/"HeJiansun" stay whole: split only if both sides ≥ 3 chars.
    private fun splitCamel(word: String): List<String> {
        val parts = word.split(camel)
        return if (parts.size > 1 && parts.all { it.length >= MIN_TOKEN }) parts else listOf(word)
    }

    fun andQuery(tokens: List<String>): String = tokens.joinToString(" ")

    fun orQuery(tokens: List<String>): String =
        tokens.sortedByDescending { it.length }.take(MAX_OR_TERMS).joinToString(" OR ")

    /** 0..1: half normalised edit similarity of the whole name, half token overlap. */
    fun similarity(candidateFullName: String, target: String): Double {
        val a = candidateFullName.lowercase().filter(Char::isLetterOrDigit)
        val b = target.lowercase().filter(Char::isLetterOrDigit)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val edit = 1.0 - levenshtein(a, b).toDouble() / maxOf(a.length, b.length)
        val ta = tokens(candidateFullName, null).toSet()
        val tb = tokens(target, null).toSet()
        val overlap = if (ta.isEmpty() || tb.isEmpty()) 0.0 else (ta intersect tb).size.toDouble() / (ta union tb).size
        return 0.5 * edit + 0.5 * overlap
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }
}
