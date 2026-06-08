package dev.wolzard.recon.core.prompt

import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.Entity
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Evidence
import dev.wolzard.recon.core.model.Finding
import dev.wolzard.recon.core.model.ResearchContext
import dev.wolzard.recon.core.model.ResearchItem
import dev.wolzard.recon.core.model.Verdict
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsTest {
    private val ctx = ResearchContext(summary = "A reel about tools.", language = "Hindi")
    private val repo = Entity(EntityType.REPO, "cli", "cli", Evidence.ON_SCREEN, "Shows the gh tool.")
    private val place = Entity(EntityType.PLACE, "Taj Mahal", "Agra, India", Evidence.ON_SCREEN, "A white marble tomb.")
    private val claim = Entity(EntityType.CLAIM, "Chocolate causes acne", null, Evidence.ON_SCREEN, "Says it does not.")
    private val fixedRepo = Finding(repo, "github", Verdict.CONFIRMED, "cli/cli", "d", emptyList(), "", "", "https://github.com/cli/cli", 1, Basis.GITHUB_API)
    private val fixedPlace = Finding(place, "wikipedia", Verdict.CONFIRMED, "Taj Mahal", "d", emptyList(), "", "", null, null, Basis.WIKIPEDIA)
    private val items = listOf(
        ResearchItem(repo, reference = "README: gh is GitHub on the command line.", fixed = fixedRepo),
        ResearchItem(place, reference = "The Taj Mahal is an ivory-white marble mausoleum.", fixed = fixedPlace),
        ResearchItem(claim),
    )

    @Test fun analyzeFillsLanguageAndAllowsEmptyList() {
        val p = Prompts.analyze("Hindi")
        assertTrue(p.contains("Write summary and saidInVideo in Hindi."))
        assertTrue(p.contains("An empty list is a correct, complete answer"))
        assertFalse(p.contains("{language}"))
    }

    @Test fun researchContainsEveryItemIndexAndSaidInVideo() {
        val p = Prompts.research(ctx, items)
        items.forEachIndexed { i, it -> assertTrue(p.contains("$i. [${it.entity.type.wire}] ${it.entity.name}")) }
        items.forEach { assertTrue(p.contains(it.entity.saidInVideo)) }
        assertTrue(p.contains("0. [repo] cli — cli — the video says: \"Shows the gh tool.\""))
        assertTrue(p.contains("2. [claim] Chocolate causes acne — the video says:"))
        assertTrue(p.contains("A reel about tools."))
        assertTrue(p.contains("Write in Hindi."))
        assertTrue(p.contains("Use Google Search"))
    }

    @Test fun knowledgeIsSelfContainedAndIncludesReferences() {
        val p = Prompts.knowledge(ctx, items)
        assertTrue(p.contains("You have NO web access"))
        assertTrue(p.contains("Reference (GitHub README): README: gh is GitHub on the command line."))
        assertTrue(p.contains("Reference (Wikipedia extract): The Taj Mahal is an ivory-white marble mausoleum."))
        assertTrue(p.contains("howToTry:"))
        assertFalse(p.contains("as in the web research prompt"))
        assertFalse(p.contains("Google Search"))
        assertFalse(Regex("\\{[a-zA-Z]+\\}").containsMatchIn(p.substringBefore("Respond only")))
    }

    @Test fun schemasParseAsJson() {
        Json.parseToJsonElement(Prompts.ANALYZE_SCHEMA) as JsonObject
    }
}
