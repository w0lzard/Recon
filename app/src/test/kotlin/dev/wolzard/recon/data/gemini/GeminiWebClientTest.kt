package dev.wolzard.recon.data.gemini

import dev.wolzard.recon.core.model.AnalyzerException
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.port.Secret
import dev.wolzard.recon.core.port.SecretStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.URLDecoder

class GeminiWebClientTest {
    private val server = MockWebServer()
    private val secrets = object : SecretStore {
        val map = mutableMapOf<Secret, String>(Secret.GEMINI_COOKIES to "__Secure-1PSID=sid; __Secure-1PSIDTS=old")
        override fun get(key: Secret) = map[key]
        override fun put(key: Secret, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    }

    @Before fun start() = server.start()
    @After fun stop() = server.close()

    private fun client(): GeminiWebClient {
