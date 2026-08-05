package dev.wolzard.recon.core.research

import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.Entity
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Finding
import dev.wolzard.recon.core.model.ResearchContext
import dev.wolzard.recon.core.model.Verdict
import dev.wolzard.recon.core.port.ResearchOutput
import dev.wolzard.recon.core.port.ResearchUnavailable
import dev.wolzard.recon.core.port.Researcher
import dev.wolzard.recon.core.port.WikiSummary
import dev.wolzard.recon.core.port.WikipediaClient
import java.io.IOException
import java.net.URI

/**
 * Keyless, **no AI call** (D13, was H4). Device language first, then English. Exact title with the qualifier
 * present in the extract → CONFIRMED; a close title → BEST_MATCH; disambiguation pages and misses fall through.
 */
class WikipediaResearcher(private val client: WikipediaClient) : Researcher {
    override val id = "wikipedia"
    private val accepted = setOf(EntityType.PERSON, EntityType.PLACE, EntityType.BOOK, EntityType.PRODUCT, EntityType.APP, EntityType.MEDIA, EntityType.OTHER)
    override fun accepts(type: EntityType) = type in accepted

    override suspend fun research(entities: List<Entity>, ctx: ResearchContext): ResearchOutput {
        val findings = mutableListOf<Finding>()
        val references = mutableMapOf<Entity, String>()
        try {
            for (e in entities) {
                val langs = listOf(ctx.languageTag, "en").distinct()
                val page = langs.firstNotNullOfOrNull { lang -> lookUp(lang, e) } ?: continue
                findings += finding(e, page)
                references[e] = page.extract
            }
        } catch (e: IOException) {
            throw ResearchUnavailable(null, e)
        }
        return ResearchOutput(findings, references = references)
    }

    private suspend fun lookUp(lang: String, e: Entity): WikiSummary? {
        val query = listOfNotNull(e.name, e.qualifier).joinToString(" ")
        val hits = client.search(lang, query).filter { matchOf(it.title, e.name) != null }
        val year = yearOf(e)
        // Media (D23): a title like "Dune (1984 film)" is a different work from the "2021" the video named.
        val pages = hits.filter { year == null || titleYear(it.title).let { y -> y == null || y == year } }
            .mapNotNull { client.summary(lang, it.key) }
            .filter { !it.disambiguation && it.extract.isNotBlank() && matchOf(it.title, e.name) != null }
        return pages.firstOrNull { year != null && hasYear(it, year) } ?: pages.firstOrNull()
    }

    /** Media only: the release year from the qualifier ("2010, Christopher Nolan"). */
    private fun yearOf(e: Entity): String? =
        if (e.type == EntityType.MEDIA) e.qualifier?.let { yearRe.find(it)?.value } else null

    private fun titleYear(title: String) = yearRe.find(title.substringAfterLast('(', ""))?.value
    private fun hasYear(p: WikiSummary, year: String) = year in p.title || year in p.extract

    private fun finding(e: Entity, page: WikiSummary): Finding {
        val exact = matchOf(page.title, e.name) == true
        val year = yearOf(e)
        // Media is confirmed only when the page carries the video's year; a title alone cannot tell two works apart.
        val qualifierFound = if (e.type == EntityType.MEDIA) year != null && hasYear(page, year)
        else e.qualifier == null || e.qualifier.split(',', ' ').map { it.trim().lowercase() }
            .filter { it.length >= 3 }.all { page.extract.lowercase().contains(it) }
        return Finding(
            entity = e, researcher = id,
            verdict = if (exact && qualifierFound) Verdict.CONFIRMED else Verdict.BEST_MATCH,
            title = page.title,
            whatItIs = page.extract.split(Regex("(?<=[.!?])\\s+")).take(2).joinToString(" "),
            keyFacts = emptyList(), howToTry = "", whyInVideo = "",
            primaryLink = page.url?.takeIf(::isWikipedia),
            basis = Basis.WIKIPEDIA,
        )
    }

    /** true = exact title (ignoring a "(…)" disambiguator), false = close, null = unrelated. */
    private fun matchOf(title: String, name: String): Boolean? {
        val t = title.replace(Regex("\\s*\\(.*\\)$"), "").trim().lowercase()
        val n = name.trim().lowercase()
        return when {
            t == n -> true
            t.contains(n) -> false // a longer, more specific title ("Atomic Habits: …"); never a shorter one (device run:
                                   // "GitHub Actions" must not match the "GitHub" article)
            else -> null
        }
    }

    private val yearRe = Regex("""\b(?:19|20)\d{2}\b""")

    private fun isWikipedia(url: String) = runCatching { URI(url) }.getOrNull()
        ?.let { it.scheme == "https" && (it.host?.endsWith(".wikipedia.org") == true) } == true
}
