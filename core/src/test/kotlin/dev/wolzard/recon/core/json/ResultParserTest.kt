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
