package dev.wolzard.recon.core.research

import dev.wolzard.recon.core.model.AnalyzerException
import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.Entity
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Evidence
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.GhRepo
import dev.wolzard.recon.core.model.ResearchContext
import dev.wolzard.recon.core.model.ResearchItem
import dev.wolzard.recon.core.model.Source
import dev.wolzard.recon.core.model.Verdict
import dev.wolzard.recon.core.port.GitHubClient
import dev.wolzard.recon.core.port.GroundingCapability
import dev.wolzard.recon.core.port.LanguageModel
import dev.wolzard.recon.core.port.ModelAnswer
import dev.wolzard.recon.core.port.ResearchUnavailable
import dev.wolzard.recon.core.port.WikiHit
import dev.wolzard.recon.core.port.WikiSummary
import dev.wolzard.recon.core.port.WikipediaClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class ResearchersTest {
    private val ctx = ResearchContext("A reel.", "English", "en")
    private fun e(type: EntityType, name: String, q: String? = null) = Entity(type, name, q, Evidence.ON_SCREEN, "said $name")

    /** Answers every item with the given verdict; records calls. */
    private class FakeLm(
        var verdict: String = "best_match",
        var sources: List<Source> = emptyList(),
        var error: Exception? = null,
    ) : LanguageModel {
        val calls = mutableListOf<Pair<String, Boolean>>()
        override suspend fun generate(prompt: String, schema: String?, googleSearch: Boolean): ModelAnswer {
            calls += prompt to googleSearch
            error?.let { throw it }
            val n = Regex("""^(\d+)\. \[""", RegexOption.MULTILINE).findAll(prompt).count()
            val items = (0 until n).joinToString(",") { """{"index":$it,"title":"T$it","verdict":"$verdict","whatItIs":"AI text $it","keyFacts":["k"],"howToTry":"h","whyInVideo":"y"}""" }
            return ModelAnswer("""{"items":[$items]}""", sources, if (sources.isEmpty()) null else "<div/>", "m1")
        }
    }

    private class FakeWiki(
        val pages: Map<String, WikiSummary> = emptyMap(),
        val fail: Boolean = false,
        val langs: Set<String> = setOf("en"),
    ) : WikipediaClient {
        val requests = mutableListOf<String>()
        override suspend fun search(lang: String, query: String): List<WikiHit> {
            requests += "search:$lang:$query"
            if (fail) throw IOException("offline")
            if (lang !in langs) return emptyList()
            return pages.keys.filter { k -> query.contains(k, ignoreCase = true) }.map { WikiHit(it.replace(' ', '_'), it) }
        }
        override suspend fun summary(lang: String, key: String): WikiSummary? { requests += "summary:$lang:$key"; return pages[key.replace('_', ' ')] }
    }

    private class NoGitHub : GitHubClient {
        override suspend fun repo(owner: String, name: String): GhRepo? = null
        override suspend fun readme(owner: String, name: String): String? = null
        override suspend fun search(query: String): List<GhRepo> = emptyList()
    }

    private val taj = WikiSummary("Taj Mahal", "The Taj Mahal is an ivory-white marble mausoleum on the right bank of the river Yamuna in Agra, Uttar Pradesh, India. It was commissioned in 1631. More text.", "https://en.m.wikipedia.org/wiki/Taj_Mahal", false)

    // --- KnowledgeResearcher (default)

    @Test fun knowledgeMakesOneUngroundedCallAndCapsVerdicts() = runTest {
        val lm = FakeLm(verdict = "disputed")
        val claim = e(EntityType.CLAIM, "Chocolate causes acne")
        val out = KnowledgeResearcher(lm).research(listOf(ResearchItem(claim)), ctx)
        assertEquals(1, lm.calls.size)
        assertEquals(false, lm.calls[0].second)
        assertTrue(lm.calls[0].first.contains("You have NO web access"))
        val f = out.findings.single()
        assertEquals(Verdict.UNCLEAR, f.verdict)
        assertEquals(Basis.AI_KNOWLEDGE, f.basis)
        assertNull(f.primaryLink)
        assertTrue(out.sources.isEmpty())
        assertEquals("m1", out.model)

        lm.verdict = "confirmed"
        assertEquals(Verdict.BEST_MATCH, KnowledgeResearcher(lm).research(listOf(ResearchItem(e(EntityType.PRODUCT, "Razr"))), ctx).findings.single().verdict)
    }

    @Test fun knowledgeQuotaIsResearchUnavailable() = runTest {
        val lm = FakeLm(error = AnalyzerException(FailureReason.QUOTA_EXCEEDED, retryAtEpochMs = 99))
        try { KnowledgeResearcher(lm).research(listOf(ResearchItem(e(EntityType.BOOK, "Atomic Habits"))), ctx); fail() } catch (u: ResearchUnavailable) {
            assertEquals(99L, u.retryAtEpochMs)
        }
    }

    // --- WebSearchResearcher (optional)

    @Test fun webDisputedWithSourcesStaysDisputed() = runTest {
        val lm = FakeLm(verdict = "disputed", sources = listOf(Source("A", "https://a"), Source("B", "https://b")))
        val out = WebSearchResearcher(lm).research(listOf(ResearchItem(e(EntityType.CLAIM, "x"))), ctx)
        assertEquals(Verdict.DISPUTED, out.findings.single().verdict)
        assertEquals(true, lm.calls.single().second)
        assertEquals(2, out.sources.size)
        assertEquals("<div/>", out.searchSuggestionsHtml)
    }

    @Test fun webSupportedWithoutSourcesIsUnclear() = runTest {
        val out = WebSearchResearcher(FakeLm(verdict = "supported")).research(listOf(ResearchItem(e(EntityType.CLAIM, "x"))), ctx)
        assertEquals(Verdict.UNCLEAR, out.findings.single().verdict)
        val conf = WebSearchResearcher(FakeLm(verdict = "confirmed")).research(listOf(ResearchItem(e(EntityType.APP, "x"))), ctx)
        assertEquals(Verdict.BEST_MATCH, conf.findings.single().verdict)
    }

    // --- WikipediaResearcher

    @Test fun r6TajMahalIsConfirmedWithLinkAndNoAiCall() = runTest {
        val wiki = FakeWiki(mapOf("Taj Mahal" to taj))
        val place = e(EntityType.PLACE, "Taj Mahal", "Agra, India")
