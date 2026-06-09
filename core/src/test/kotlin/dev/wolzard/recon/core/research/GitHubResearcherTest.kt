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
        val f = out.findings.single()
        assertEquals(Verdict.CONFIRMED, f.verdict)
        assertEquals("cli/cli", f.title)
        assertEquals("https://github.com/cli/cli", f.primaryLink)
        assertEquals(40000, f.stars)
        assertEquals(Basis.GITHUB_API, f.basis)
        assertEquals("GitHub's official command line tool", f.whatItIs)
        assertEquals("README of cli/cli", out.references[e])
        assertTrue(client.queries.isEmpty())
    }

    @Test fun notFoundThenExactNameSearchHitIsBestMatch() = runTest {
        val client = FakeClient(searches = mapOf("vilmibm user status" to listOf(gh("vilmibm/gh-user-status"), gh("x/gh-user-status-fork"), gh("y/user-status"))))
        val f = GitHubResearcher(client).research(listOf(repo("gh-user-status", "vilmibm")), ctx).findings.single() // repo() 404
        assertEquals(Verdict.BEST_MATCH, f.verdict)
        assertEquals("https://github.com/vilmibm/gh-user-status", f.primaryLink)
        assertTrue(f.candidates.isEmpty())
    }

    @Test fun noCandidateAboveMinimumFallsThrough() = runTest {
        val client = FakeClient(searches = mapOf("unrelated thing" to listOf(gh("someone/completely-different"))))
        assertTrue(GitHubResearcher(client).research(listOf(repo("unrelated-thing")), ctx).findings.isEmpty())
    }

    @Test fun atMostThreeResolved() = runTest {
        val repos = (1..5).associate { "o/r$it" to gh("o/r$it") }
        val out = GitHubResearcher(FakeClient(repos = repos)).research((1..5).map { repo("r$it", "o") }, ctx)
        assertEquals(3, out.findings.size)
        assertEquals(listOf("o/r1", "o/r2", "o/r3"), out.findings.map { it.title })
    }

    @Test fun rateLimitIsResearchUnavailable() = runTest {
        try {
            GitHubResearcher(FakeClient(rateLimited = true)).research(listOf(repo("cli", "cli")), ctx); fail()
        } catch (e: ResearchUnavailable) {
            assertEquals(42L, e.retryAtEpochMs)
        }
    }

    @Test fun nonGithubLinksAreRejected() = runTest {
        val bad = GhRepo("cli/cli", "http://evil.example/cli", "d", 1)
        val out = GitHubResearcher(FakeClient(repos = mapOf("cli/cli" to bad))).research(listOf(repo("cli", "cli")), ctx)
        assertTrue(out.findings.isEmpty())
    }

