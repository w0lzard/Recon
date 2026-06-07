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
