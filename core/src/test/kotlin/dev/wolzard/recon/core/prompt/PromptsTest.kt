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
