package dev.wolzard.recon.data.wiki

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpWikipediaClientTest {
    private val server = MockWebServer()
    private val hosts = mutableListOf<String>()
    private lateinit var client: OkHttpWikipediaClient

    @Before fun start() {
        server.start()
        client = OkHttpWikipediaClient(OkHttpClient()) { lang -> hosts += lang; server.url("/$lang").toString().trimEnd('/') }
    }
    @After fun stop() = server.close()

    @Test fun searchParsesPagesAndSendsUserAgent() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"pages":[{"id":1,"key":"Taj_Mahal","title":"Taj Mahal","description":"Mausoleum in Agra"}]}""").build())
        val hits = client.search("en", "Taj Mahal Agra, India")
        assertEquals("Taj_Mahal", hits.single().key)
        val req = server.takeRequest()
        assertEquals("/en/w/rest.php/v1/search/page", req.url.encodedPath)
        assertEquals("Taj Mahal Agra, India", req.url.queryParameter("q"))
        assertEquals("3", req.url.queryParameter("limit"))
        assertTrue(req.headers["User-Agent"]!!.startsWith("Recon/"))
    }

    @Test fun summaryParsesTypeAndMobileUrl() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"type":"standard","title":"Taj Mahal","extract":"The Taj Mahal is a mausoleum.","content_urls":{"mobile":{"page":"https://en.m.wikipedia.org/wiki/Taj_Mahal"}}}""").build())
        val s = client.summary("en", "Taj_Mahal")!!
        assertEquals("https://en.m.wikipedia.org/wiki/Taj_Mahal", s.url)
        assertEquals(false, s.disambiguation)
        assertEquals("/en/api/rest_v1/page/summary/Taj_Mahal", server.takeRequest().url.encodedPath)
        server.enqueue(MockResponse.Builder().code(200).body("""{"type":"disambiguation","title":"Mercury","extract":"Mercury may refer to:"}""").build())
        assertTrue(client.summary("en", "Mercury")!!.disambiguation)
        server.enqueue(MockResponse.Builder().code(404).body("{}").build())
        assertNull(client.summary("en", "Nope"))
    }

    @Test fun invalidLanguageTagFallsBackToEnglish() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"pages":[]}""").build())
        client.search("evil.example/x", "q")
        assertEquals(listOf("en"), hosts)
    }
}
