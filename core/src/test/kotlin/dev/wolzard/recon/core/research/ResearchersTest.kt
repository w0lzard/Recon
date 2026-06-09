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
