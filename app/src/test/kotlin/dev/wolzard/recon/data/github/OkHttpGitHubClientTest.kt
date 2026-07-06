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
