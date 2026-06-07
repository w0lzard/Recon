package dev.wolzard.recon.core.json

import dev.wolzard.recon.core.model.AnalyzerException
import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.Entity
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Evidence
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.model.Finding
import dev.wolzard.recon.core.model.ResearchItem
import dev.wolzard.recon.core.model.Verdict
import dev.wolzard.recon.core.model.VerdictCap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ResultParserTest {

    private fun ent(type: String, name: String, qualifier: String? = null, evidence: String = "on-screen") =
        """{"type":"$type","name":"$name","qualifier":${qualifier?.let { "\"$it\"" } ?: "null"},"evidence":"$evidence","saidInVideo":"s"}"""

    private fun analysis(vararg entities: String, extra: String = "") =
        """{"entities":[${entities.joinToString(",")}],"summary":"A video.","confidence":0.8,"onScreenText":["gh repo"]$extra}"""

    private fun assertInvalid(block: () -> Unit) {
        try {
            block(); fail("expected MODEL_OUTPUT_INVALID")
        } catch (e: AnalyzerException) {
            assertEquals(FailureReason.MODEL_OUTPUT_INVALID, e.reason)
        }
    }

    // --- fences, prose, fields

    @Test fun validJsonRoundTrips() {
        val r = ResultParser.parseAnalysis(analysis(ent("repo", "cli", "cli")))
        assertEquals(1, r.entities.size)
        assertEquals(Entity(EntityType.REPO, "cli", "cli", Evidence.ON_SCREEN, "s"), r.entities[0])
        assertEquals("A video.", r.summary)
        assertEquals(0.8, r.confidence, 0.0)
        assertEquals(listOf("gh repo"), r.onScreenText)
    }

    @Test fun fencedAndProsePrefixedInputParses() {
        val json = analysis(ent("book", "Atomic Habits", "James Clear"))
        assertEquals(1, ResultParser.parseAnalysis("```json\n$json\n```").entities.size)
        assertEquals(1, ResultParser.parseAnalysis("Here is the JSON you asked for:\n$json\nThanks").entities.size)
    }

    @Test fun unknownFieldsAreIgnored() {
        val r = ResultParser.parseAnalysis(analysis(ent("app", "Codeium"), extra = ""","mood":"happy""""))
        assertEquals("Codeium", r.entities[0].name)
    }

    @Test fun missingSummaryThrows() = assertInvalid {
        ResultParser.parseAnalysis("""{"entities":[],"confidence":0.0,"onScreenText":[]}""")
    }

    @Test fun garbageThrows() {
        assertInvalid { ResultParser.parseAnalysis("I could not watch the video, sorry.") }
        assertInvalid { ResultParser.parseAnalysis("{not json") }
    }

    // --- types and values

    @Test fun unknownTypeBecomesOther() {
        assertEquals(EntityType.OTHER, ResultParser.parseAnalysis(analysis(ent("podcast", "Lex"))).entities[0].type)
    }

    @Test fun confidenceIsClamped() {
        val r = ResultParser.parseAnalysis(analysis(ent("app", "X")).replace("0.8", "1.7"))
        assertEquals(1.0, r.confidence, 0.0)
    }

    // --- repo validation

    @Test fun invalidRepoOwnerBecomesNull() {
        assertNull(ResultParser.parseAnalysis(analysis(ent("repo", "cli", "not an owner!"))).entities[0].qualifier)
    }

    @Test fun githubUrlInRepoNameIsSplit() {
        val e = ResultParser.parseAnalysis(analysis(ent("repo", "github.com/a/b"))).entities[0]
        assertEquals("a", e.qualifier)
        assertEquals("b", e.name)
        val e2 = ResultParser.parseAnalysis(analysis(ent("repo", "https://github.com/cli/cli"))).entities[0]
        assertEquals("cli" to "cli", e2.qualifier to e2.name)
    }

    @Test fun repoNameWithSpacesIsRetypedOther() {
        val e = ResultParser.parseAnalysis(analysis(ent("repo", "My Girlfriend Jinchen LaTeX"))).entities[0]
        assertEquals(EntityType.OTHER, e.type)
    }

    // --- limits

    @Test fun duplicatesCollapseAndBlankNamesDrop() {
        val r = ResultParser.parseAnalysis(analysis(ent("app", "Codeium"), ent("app", "codeium"), ent("app", " ")))
        assertEquals(1, r.entities.size)
    }

    @Test fun atMostEightEntities() {
        val many = (1..12).map { ent("product", "P$it") }.toTypedArray()
        assertEquals(8, ResultParser.parseAnalysis(analysis(*many)).entities.size)
    }

    @Test fun onScreenTextAndStringsAreCapped() {
        val long = "x".repeat(900)
        val texts = (1..30).joinToString(",") { "\"$long\"" }
        val raw = """{"entities":[${ent("app", long)}],"summary":"$long","confidence":0.5,"onScreenText":[$texts]}"""
        val r = ResultParser.parseAnalysis(raw)
        assertEquals(20, r.onScreenText.size)
        assertTrue(r.onScreenText.all { it.length <= 200 })
        assertEquals(500, r.summary.length)
        assertEquals(500, r.entities[0].name.length)
    }

    // --- r10 fixture: "no entity" is a valid analysis

    @Test fun r10EmptyEntitiesIsValid() {
        val r = ResultParser.parseAnalysis(
            """{"entities":[],"summary":"Hands squeeze and stretch colourful slime while a voice asks viewers to like and subscribe.","confidence":0.0,"onScreenText":[]}"""
        )
        assertTrue(r.entities.isEmpty())
        assertEquals(0.0, r.confidence, 0.0)
    }

    // --- parseResearch

    private val product = Entity(EntityType.PRODUCT, "Razr 2026", "Motorola", Evidence.SPOKEN, "A foldable phone.")
    private val claim = Entity(EntityType.CLAIM, "Chocolate causes acne", null, Evidence.ON_SCREEN, "Says it does not.")
    private val repo = Entity(EntityType.REPO, "cli", "cli", Evidence.ON_SCREEN, "GitHub's CLI.")

    private fun item(i: Int, verdict: String, whatItIs: String = "W$i", title: String = "T$i") =
        """{"index":$i,"title":"$title","verdict":"$verdict","whatItIs":"$whatItIs","keyFacts":["a","b","c","d","e","f"],"howToTry":"h","whyInVideo":"y"}"""

    private fun research(vararg items: String) = """{"items":[${items.joinToString(",")}]}"""

    @Test fun researchIndexOutOfRangeDroppedAndDuplicateFirstWins() {
        val out = ResultParser.parseResearch(
            research(item(0, "confirmed", whatItIs = "first"), item(0, "not_found", whatItIs = "second"), item(5, "confirmed")),
            listOf(ResearchItem(product)), VerdictCap.WEB,
        )
        assertEquals(1, out.size)
        assertEquals("first", out[0].whatItIs)
        assertEquals(Verdict.CONFIRMED, out[0].verdict)
        assertEquals(5, out[0].keyFacts.size)
        assertEquals("web", out[0].researcher)
        assertEquals(Basis.WEB_SOURCES, out[0].basis)
        assertNull(out[0].primaryLink)
    }

    @Test fun researchVerdictTypeMismatchesAreMapped() {
        val out = ResultParser.parseResearch(
            research(item(0, "confirmed"), item(1, "disputed")),
            listOf(ResearchItem(claim), ResearchItem(product)), VerdictCap.WEB,
        )
        assertEquals(Verdict.UNCLEAR, out[0].verdict)
        assertEquals(Verdict.BEST_MATCH, out[1].verdict)
    }

    @Test fun researchUrlsAreStrippedFromText() {
        val out = ResultParser.parseResearch(
            research(item(0, "confirmed", whatItIs = "Buy it at https://shop.example.com/razr now or www.example.com")),
            listOf(ResearchItem(product)), VerdictCap.WEB,
        )
        assertFalse(out[0].whatItIs.contains("example.com"))
        assertTrue(out[0].whatItIs.startsWith("Buy it at"))
