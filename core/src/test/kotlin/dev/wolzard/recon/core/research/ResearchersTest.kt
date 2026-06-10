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
        val out = WikipediaResearcher(wiki).research(listOf(place), ctx)
        val f = out.findings.single()
        assertEquals(Verdict.CONFIRMED, f.verdict)
        assertEquals("https://en.m.wikipedia.org/wiki/Taj_Mahal", f.primaryLink)
        assertEquals(Basis.WIKIPEDIA, f.basis)
        assertEquals("The Taj Mahal is an ivory-white marble mausoleum on the right bank of the river Yamuna in Agra, Uttar Pradesh, India. It was commissioned in 1631.", f.whatItIs)
        assertEquals(taj.extract, out.references[place])
        assertTrue(wiki.requests.size <= 2)
    }

    @Test fun disambiguationPageIsNotReturned() = runTest {
        val wiki = FakeWiki(mapOf("Mercury" to WikiSummary("Mercury", "Mercury may refer to:", "https://en.m.wikipedia.org/wiki/Mercury", true)))
        assertTrue(WikipediaResearcher(wiki).research(listOf(e(EntityType.OTHER, "Mercury")), ctx).findings.isEmpty())
    }

    @Test fun claimsAndRecipesAreNeverAccepted() {
        val w = WikipediaResearcher(FakeWiki())
        assertTrue(!w.accepts(EntityType.CLAIM) && !w.accepts(EntityType.RECIPE) && !w.accepts(EntityType.REPO))
        listOf(EntityType.PERSON, EntityType.PLACE, EntityType.BOOK, EntityType.PRODUCT, EntityType.APP, EntityType.OTHER).forEach { assertTrue(w.accepts(it)) }
    }

    @Test fun nonWikipediaUrlIsDropped() = runTest {
        val wiki = FakeWiki(mapOf("Taj Mahal" to taj.copy(url = "https://evil.example/wiki/Taj_Mahal")))
        assertNull(WikipediaResearcher(wiki).research(listOf(e(EntityType.PLACE, "Taj Mahal", "Agra, India")), ctx).findings.single().primaryLink)
    }

    @Test fun deviceLanguageThenEnglishFallback() = runTest {
        val wiki = FakeWiki(mapOf("Taj Mahal" to taj))
        WikipediaResearcher(wiki).research(listOf(e(EntityType.PLACE, "Taj Mahal")), ctx.copy(languageTag = "hi"))
        assertTrue(wiki.requests.first().startsWith("search:hi:"))
        assertTrue(wiki.requests.any { it.startsWith("search:en:") })
    }

    @Test fun qualifierMissingFromExtractIsBestMatch() = runTest {
        val wiki = FakeWiki(mapOf("Taj Mahal" to taj))
        assertEquals(Verdict.BEST_MATCH, WikipediaResearcher(wiki).research(listOf(e(EntityType.PLACE, "Taj Mahal", "Bengaluru")), ctx).findings.single().verdict)
    }

    // --- ResearchChain

    private fun chain(lm: FakeLm, wiki: WikipediaClient = FakeWiki(), grounding: GroundingCapability = GroundingCapability.UNAVAILABLE) =
        ResearchChain(GitHubResearcher(NoGitHub()), WikipediaResearcher(wiki), KnowledgeResearcher(lm), WebSearchResearcher(lm), { grounding })

    @Test fun capabilityPicksTheAiResearcher() = runTest {
        val lm = FakeLm()
        chain(lm, grounding = GroundingCapability.AVAILABLE).run(listOf(e(EntityType.APP, "X")), ctx, budgetLeft = true)
        assertEquals(true, lm.calls.single().second)
        chain(lm, grounding = GroundingCapability.UNKNOWN).run(listOf(e(EntityType.APP, "X")), ctx, budgetLeft = true)
        assertEquals(false, lm.calls.last().second)
    }

    @Test fun noBudgetMeansNoAiCallAndBudgetDegraded() = runTest {
        val lm = FakeLm()
        val out = chain(lm, FakeWiki(mapOf("Taj Mahal" to taj))).run(listOf(e(EntityType.PLACE, "Taj Mahal", "Agra, India"), e(EntityType.CLAIM, "c")), ctx, budgetLeft = false)
        assertTrue(lm.calls.isEmpty())
        assertEquals(listOf(Verdict.CONFIRMED, Verdict.NOT_CHECKED), out.findings.map { it.verdict })
        assertTrue(out.degraded.containsKey("budget"))
    }

    @Test fun unresolvedRepoGoesToTheAiResearcher() = runTest {
        val lm = FakeLm()
        val out = chain(lm).run(listOf(e(EntityType.REPO, "zzzz")), ctx, budgetLeft = true)
        assertEquals("knowledge", out.findings.single().researcher)
        assertEquals(1, lm.calls.size)
    }

    @Test fun degradedAiKeepsKeylessFindingsRestNotChecked() = runTest {
        val lm = FakeLm(error = AnalyzerException(FailureReason.MODEL_BUSY, retryAtEpochMs = 7))
        val place = e(EntityType.PLACE, "Taj Mahal", "Agra, India")
        val book = e(EntityType.CLAIM, "Water while standing hurts knees")
        val out = chain(lm, FakeWiki(mapOf("Taj Mahal" to taj))).run(listOf(place, book), ctx, budgetLeft = true)
        assertEquals(Verdict.CONFIRMED, out.findings[0].verdict)
        assertEquals(Verdict.NOT_CHECKED, out.findings[1].verdict)
        assertEquals(book.saidInVideo, out.findings[1].whatItIs)
        assertEquals(Basis.VIDEO_ONLY, out.findings[1].basis)
        assertEquals(7L, out.degraded["knowledge"])
    }

    @Test fun wikipediaHitsGetAiTextButKeepVerdictAndLink() = runTest {
        val lm = FakeLm(verdict = "not_found")
        val place = e(EntityType.PLACE, "Taj Mahal", "Agra, India")
        val out = chain(lm, FakeWiki(mapOf("Taj Mahal" to taj))).run(listOf(place), ctx, budgetLeft = true)
        val f = out.findings.single()
        assertEquals(Verdict.CONFIRMED, f.verdict)
        assertEquals("https://en.m.wikipedia.org/wiki/Taj_Mahal", f.primaryLink)
        assertEquals("AI text 0", f.whatItIs)
        assertTrue(lm.calls.single().first.contains("Reference (Wikipedia extract): The Taj Mahal"))
    }

    @Test fun orderLimitsAndAtMostOneAiCall() = runTest {
        val lm = FakeLm()
        val nine = (1..9).map { e(EntityType.PRODUCT, "P$it") }
        val out = chain(lm).run(nine, ctx, budgetLeft = true)
        assertEquals(nine, out.findings.map { it.entity })
        assertEquals(6, out.findings.count { it.verdict != Verdict.NOT_CHECKED })
        assertEquals(3, out.findings.count { it.verdict == Verdict.NOT_CHECKED })
        assertEquals(1, lm.calls.size)
        assertEquals("m1", out.model)
    }

    @Test fun emptyInputMakesNoCalls() = runTest {
        val lm = FakeLm()
        val wiki = FakeWiki()
        val out = chain(lm, wiki).run(emptyList(), ctx, budgetLeft = true)
        assertTrue(out.findings.isEmpty() && lm.calls.isEmpty() && wiki.requests.isEmpty())
    }

    @Test fun wikipediaOfflineIsSilentlyDegraded() = runTest {
        val out = chain(FakeLm(), FakeWiki(fail = true)).run(listOf(e(EntityType.BOOK, "Atomic Habits", "James Clear")), ctx, budgetLeft = true)
        assertEquals("knowledge", out.findings.single().researcher)
        assertTrue(out.degraded.containsKey("wikipedia"))
    }

    @Test fun broaderArticleIsNotAMatch() = runTest {
        val github = WikiSummary("GitHub", "GitHub is a proprietary developer platform.", "https://en.wikipedia.org/wiki/GitHub", false)
        val wiki = object : WikipediaClient {
            override suspend fun search(lang: String, query: String) = listOf(WikiHit("GitHub", "GitHub"))
            override suspend fun summary(lang: String, key: String) = github
        }
        assertTrue(WikipediaResearcher(wiki).research(listOf(e(EntityType.APP, "GitHub Actions", "GitHub")), ctx).findings.isEmpty())
    }
}
