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
