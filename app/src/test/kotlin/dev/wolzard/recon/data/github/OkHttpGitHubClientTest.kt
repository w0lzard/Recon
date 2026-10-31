package dev.wolzard.recon.data.github

import dev.wolzard.recon.core.port.GitHubRateLimited
import dev.wolzard.recon.core.port.Secret
import dev.wolzard.recon.core.port.SecretStore
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class OkHttpGitHubClientTest {
    private val server = MockWebServer()
    private var token: String? = null
    private val secrets = object : SecretStore {
        override fun get(key: Secret) = if (key == Secret.GITHUB_TOKEN) token else null
        override fun put(key: Secret, value: String?) = Unit
    }
    private lateinit var client: OkHttpGitHubClient

    @Before fun start() { server.start(); client = OkHttpGitHubClient(OkHttpClient(), secrets, server.url("").toString().trimEnd('/')) }
    @After fun stop() = server.close()

    private fun json(body: String, code: Int = 200) = MockResponse.Builder().code(code).body(body).build()

    @Test fun repoSendsHeadersAndParses() = runTest {
        server.enqueue(json("""{"full_name":"cli/cli","html_url":"https://github.com/cli/cli","description":"GitHub CLI","stargazers_count":40000,"extra":1}"""))
        val r = client.repo("cli", "cli")!!
        assertEquals("cli/cli", r.fullName)
        assertEquals(40000, r.stars)
        val req = server.takeRequest()
        assertEquals("/repos/cli/cli", req.url.encodedPath)
        assertEquals("Recon", req.headers["User-Agent"])
        assertEquals("2022-11-28", req.headers["X-GitHub-Api-Version"])
        assertNull(req.headers["Authorization"])
    }

    @Test fun repoParsesPushDateLicenseLanguageAndArchived() = runTest {
        server.enqueue(json("""{"full_name":"a/b","html_url":"https://github.com/a/b","stargazers_count":5,"pushed_at":"2026-10-20T08:30:00Z","archived":true,"license":{"key":"mit","spdx_id":"MIT","name":"MIT License"},"language":"Rust"}"""))
        val r = client.repo("a", "b")!!
        assertEquals("2026-10-20T08:30:00Z", r.pushedAt)
        assertEquals(true, r.archived)
        assertEquals("MIT", r.license?.spdxId)
        assertEquals("Rust", r.language)
    }

    @Test fun repoWithNullLicenseAndLanguageParses() = runTest {
        server.enqueue(json("""{"full_name":"a/b","html_url":"https://github.com/a/b","stargazers_count":5,"pushed_at":null,"license":null,"language":null}"""))
        val r = client.repo("a", "b")!!
        assertNull(r.pushedAt); assertNull(r.license); assertNull(r.language); assertEquals(false, r.archived)
    }

    @Test fun latestReleaseParsesAndNoReleaseIsNull() = runTest {
        server.enqueue(json("""{"tag_name":"v1.2.0","published_at":"2026-09-01T10:00:00Z","name":"x","draft":false}"""))
        val rel = client.latestRelease("a", "b")!!
        assertEquals("v1.2.0", rel.tag)
        assertEquals("2026-09-01T10:00:00Z", rel.publishedAt)
        assertEquals("/repos/a/b/releases/latest", server.takeRequest().url.encodedPath)
        server.enqueue(json("""{"message":"Not Found"}""", 404))
        assertNull(client.latestRelease("a", "b"))
    }

    @Test fun latestReleaseRateLimitThrows() = runTest {
        server.enqueue(MockResponse.Builder().code(403).addHeader("X-RateLimit-Remaining", "0").body("{}").build())
        try { client.latestRelease("a", "b"); fail() } catch (_: GitHubRateLimited) { }
    }

    @Test fun tokenIsSentOnlyWhenSet() = runTest {
        token = "ghp_test"
        server.enqueue(json("{}", 404))
        assertNull(client.repo("a", "b"))
        assertEquals("Bearer ghp_test", server.takeRequest().headers["Authorization"])
    }

    @Test fun rateLimitThrowsWithReset() = runTest {
        server.enqueue(MockResponse.Builder().code(403).addHeader("X-RateLimit-Remaining", "0").addHeader("X-RateLimit-Reset", "1759900000").body("{}").build())
        try { client.repo("a", "b"); fail() } catch (e: GitHubRateLimited) { assertEquals(1_759_900_000_000L, e.resetEpochMs) }
        server.enqueue(json("""{"message":"secondary rate limit"}""", 429))
        try { client.search("x"); fail() } catch (e: GitHubRateLimited) { assertNull(e.resetEpochMs) }
    }

    @Test fun serverErrorIsIOException() = runTest {
        server.enqueue(json("{}", 502))
        try { client.repo("a", "b"); fail() } catch (_: IOException) { }
    }

    @Test fun readmeIsRawAndTruncated() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("x".repeat(20_000)).build())
        assertEquals(8_000, client.readme("cli", "cli")!!.length)
        assertEquals("application/vnd.github.raw", server.takeRequest().headers["Accept"])
        server.enqueue(json("{}", 404))
        assertNull(client.readme("a", "b"))
    }

    @Test fun searchUsesInNameAndParsesItems() = runTest {
        server.enqueue(json("""{"total_count":1,"items":[{"full_name":"HEJustinSun/my-girlfriend-jingtian-latex","html_url":"https://github.com/HEJustinSun/my-girlfriend-jingtian-latex","description":null,"stargazers_count":3}]}"""))
        val hits = client.search("girlfriend latex")
        assertEquals("HEJustinSun/my-girlfriend-jingtian-latex", hits.single().fullName)
        val req = server.takeRequest()
        assertEquals("girlfriend latex in:name", req.url.queryParameter("q"))
        assertEquals("10", req.url.queryParameter("per_page"))
    }
}
