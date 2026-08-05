package dev.wolzard.recon.core.research

import dev.wolzard.recon.core.model.Entity
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Evidence
import dev.wolzard.recon.core.model.ResearchContext
import dev.wolzard.recon.core.model.Verdict
import dev.wolzard.recon.core.port.WikiHit
import dev.wolzard.recon.core.port.WikiSummary
import dev.wolzard.recon.core.port.WikipediaClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** D23: media is verified against Wikipedia by title AND year. */
class MediaResearchTest {
    private val ctx = ResearchContext("A reel.", "English", "en")
    private fun media(name: String, q: String?) = Entity(EntityType.MEDIA, name, q, Evidence.SPOKEN, "said $name")

    private class Wiki(val pages: List<WikiSummary>) : WikipediaClient {
        override suspend fun search(lang: String, query: String) = pages.map { WikiHit(it.title, it.title) }
        override suspend fun summary(lang: String, key: String) = pages.firstOrNull { it.title == key }
    }

    private fun page(title: String, extract: String) = WikiSummary(title, extract, "https://en.wikipedia.org/wiki/${title.replace(' ', '_')}", false)
    private val inception = page("Inception", "Inception is a 2010 science fiction action heist film written and directed by Christopher Nolan.")

    @Test fun titleAndYearPresentIsConfirmed() = runTest {
        val f = WikipediaResearcher(Wiki(listOf(inception))).research(listOf(media("Inception", "2010, Christopher Nolan")), ctx).findings.single()
        assertEquals(Verdict.CONFIRMED, f.verdict)
        assertEquals("https://en.wikipedia.org/wiki/Inception", f.primaryLink)
    }

    @Test fun yearThatDoesNotMatchThePageIsOnlyABestMatch() = runTest {
        val f = WikipediaResearcher(Wiki(listOf(inception))).research(listOf(media("Inception", "2014")), ctx).findings.single()
        assertEquals(Verdict.BEST_MATCH, f.verdict)
    }

    @Test fun noYearCannotBeConfirmed() = runTest {
        val f = WikipediaResearcher(Wiki(listOf(inception))).research(listOf(media("Inception", null)), ctx).findings.single()
        assertEquals(Verdict.BEST_MATCH, f.verdict)
    }

    @Test fun titleDisambiguatedByAnotherYearIsSkipped() = runTest {
        val old = page("Dune (1984 film)", "Dune is a 1984 American epic space opera film.")
        val new = page("Dune (2021 film)", "Dune is a 2021 epic science fiction film directed by Denis Villeneuve.")
        val f = WikipediaResearcher(Wiki(listOf(old, new))).research(listOf(media("Dune", "2021")), ctx).findings.single()
        assertEquals("Dune (2021 film)", f.title)
        assertEquals(Verdict.CONFIRMED, f.verdict)
    }

    @Test fun onlyAWrongYearPageGivesNoFinding() = runTest {
        val old = page("Dune (1984 film)", "Dune is a 1984 American epic space opera film.")
        assertTrue(WikipediaResearcher(Wiki(listOf(old))).research(listOf(media("Dune", "2021")), ctx).findings.isEmpty())
    }

    @Test fun wikipediaAcceptsMedia() {
        assertTrue(WikipediaResearcher(Wiki(emptyList())).accepts(EntityType.MEDIA))
    }
}
