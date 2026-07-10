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
