package dev.wolzard.recon.core.research

import dev.wolzard.recon.core.model.Basis
import dev.wolzard.recon.core.model.Entity
import dev.wolzard.recon.core.model.EntityType
import dev.wolzard.recon.core.model.Evidence
import dev.wolzard.recon.core.model.GhRepo
import dev.wolzard.recon.core.model.ResearchContext
import dev.wolzard.recon.core.model.Verdict
import dev.wolzard.recon.core.port.GitHubClient
import dev.wolzard.recon.core.port.GitHubRateLimited
import dev.wolzard.recon.core.port.ResearchUnavailable
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GitHubResearcherTest {
    private val ctx = ResearchContext("summary", "English")
    private fun repo(name: String, owner: String? = null, ev: Evidence = Evidence.ON_SCREEN) =
        Entity(EntityType.REPO, name, owner, ev, "said")
    private fun gh(full: String, stars: Int = 10, desc: String? = "d $full") = GhRepo(full, "https://github.com/$full", desc, stars)

    private class FakeClient(
        val repos: Map<String, GhRepo> = emptyMap(),
        val searches: Map<String, List<GhRepo>> = emptyMap(),
        val rateLimited: Boolean = false,
    ) : GitHubClient {
        val queries = mutableListOf<String>()
        val readmes = mutableListOf<String>()
        override suspend fun repo(owner: String, name: String): GhRepo? {
            if (rateLimited) throw GitHubRateLimited(42L)
            return repos["$owner/$name".lowercase()]
        }
        override suspend fun readme(owner: String, name: String): String { readmes += "$owner/$name"; return "README of $owner/$name" }
        override suspend fun search(query: String): List<GhRepo> { queries += query; return searches[query].orEmpty() }
    }

    @Test fun ownerKnownAndFoundIsConfirmedWithLinkStarsAndReadme() = runTest {
        val client = FakeClient(repos = mapOf("cli/cli" to gh("cli/cli", 40000, "GitHub's official command line tool")))
        val e = repo("cli", "cli")
        val out = GitHubResearcher(client).research(listOf(e), ctx)
