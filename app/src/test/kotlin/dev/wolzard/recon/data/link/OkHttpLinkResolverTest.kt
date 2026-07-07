package dev.wolzard.recon.data.link

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

class OkHttpLinkResolverTest {
    private val server = MockWebServer()
    private val resolver = OkHttpLinkResolver(OkHttpClient(), timeout = 300.milliseconds)

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    private fun redirect(to: String) = MockResponse.Builder().code(301).addHeader("Location", to).build()

    @Test fun followsRedirectsToFinalUrl() = runTest {
        server.enqueue(redirect("/b"))
        server.enqueue(redirect(server.url("/c").toString()))
        server.enqueue(MockResponse.Builder().code(200).build())
        assertEquals(server.url("/c").toString(), resolver.resolve(server.url("/a").toString()))
        assertEquals("HEAD", server.takeRequest().method)
    }

    @Test fun headRejectedFallsBackToGet() = runTest {
        server.enqueue(MockResponse.Builder().code(405).build())
        server.enqueue(redirect("/final"))
        server.enqueue(MockResponse.Builder().code(200).build())
        assertEquals(server.url("/final").toString(), resolver.resolve(server.url("/s").toString()))
        assertEquals(listOf("HEAD", "GET", "GET"), List(3) { server.takeRequest().method })
    }

    @Test fun timeoutGivesInput() = runTest {
        val slow = MockResponse.Builder().code(200).headersDelay(2, TimeUnit.SECONDS).build()
        server.enqueue(slow); server.enqueue(slow)
        val input = server.url("/slow").toString()
        assertEquals(input, resolver.resolve(input))
    }

    @Test fun moreThanFiveRedirectsGivesInput() = runTest {
        repeat(12) { server.enqueue(redirect("/r$it")) }
        val input = server.url("/start").toString()
        assertEquals(input, resolver.resolve(input))
    }
}
