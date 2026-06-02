package dev.wolzard.recon.core.research

import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.Entity
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Finding
import dev.wolzard.recon.core.model.GhRepo
import dev.wolzard.recon.core.model.ResearchContext
import dev.wolzard.recon.core.model.Verdict
import dev.wolzard.recon.core.port.GitHubClient
import dev.wolzard.recon.core.port.GitHubRateLimited
import dev.wolzard.recon.core.port.ResearchOutput
import dev.wolzard.recon.core.port.ResearchUnavailable
import dev.wolzard.recon.core.port.Researcher
import java.io.IOException

/**
 * Verifies repos against the GitHub API, with **no AI call** (D9, D13). Owner known → `repo()`; otherwise, or on
 * 404, fuzzy search (≤ 2 queries). An exact name → BEST_MATCH with link; close names → 2–3 [Finding.candidates]
 * for the user to pick. README excerpts of resolved repos are handed to the AI researcher as references.
 */
class GitHubResearcher(private val client: GitHubClient) : Researcher {
    override val id = "github"
    override fun accepts(type: EntityType) = type == EntityType.REPO

    override suspend fun research(entities: List<Entity>, ctx: ResearchContext): ResearchOutput {
        val findings = mutableListOf<Finding>()
        val references = mutableMapOf<Entity, String>()
        try {
            for (e in entities) {
                if (findings.size >= MAX_RESOLVED) break
                val direct = e.qualifier?.let { client.repo(it, e.name) }?.takeIf(::isGitHubLink)
                val finding = if (direct != null) found(e, direct, Verdict.CONFIRMED) else search(e) ?: continue
                findings += finding
                if (finding.primaryLink != null) {
                    val (owner, name) = finding.title.split('/', limit = 2)
                    client.readme(owner, name)?.let { references[e] = it.take(README_CHARS) }
                }
            }
        } catch (e: GitHubRateLimited) {
            throw ResearchUnavailable(e.resetEpochMs, e)
