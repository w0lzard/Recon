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
    private val accepted = setOf(EntityType.PERSON, EntityType.PLACE, EntityType.BOOK, EntityType.PRODUCT, EntityType.APP, EntityType.OTHER)
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
