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
        val base = server.url("").toString().trimEnd('/')
        return GeminiWebClient(OkHttpClient(), secrets, appUrl = base, uploadUrl = "$base/upload", rotateUrl = "$base/rotate")
    }

    private val appPage = """<script>WIZ_global_data = {"SNlM0e":"tok123","cfb2h":"boq_build","FdrFJe":"-42","TuX5cc":"en","qKIAYe":"feeds/push1"}</script>"""

    /** One length-prefixed frame: the length counts from the newline after the number. */
    private fun frame(json: String) = "${("\n" + json).length}\n$json"

    /** A StreamGenerate part carrying [text] in candidate 0, the way the web app streams it. */
    private fun replyPart(text: String): String {
        val inner = JsonArray(listOf(JsonNull, JsonArray(listOf(JsonPrimitive("c_1"), JsonPrimitive("r_1"))), JsonNull, JsonNull,
            JsonArray(listOf(JsonArray(listOf(JsonPrimitive("rc_1"), JsonArray(listOf(JsonPrimitive(text)))))))))
        return JsonArray(listOf(JsonPrimitive("wrb.fr"), JsonNull, JsonPrimitive(inner.toString()))).toString()
    }

    private fun ok(body: String) = MockResponse.Builder().body(body).build()

    @Test fun `generate posts the token and prompt and returns the last cumulative text`() = runTest {
        server.enqueue(ok(appPage))
        server.enqueue(ok(")]}'\n\n" + frame("[${replyPart("Hel")}]") + "\n" + frame("[${replyPart("Hello ✓ 😀 world")}]")))

        assertEquals("Hello ✓ 😀 world", client().generate("say hi"))

        val init = server.takeRequest()
        assertEquals("/app", init.url.encodedPath)
        assertEquals("__Secure-1PSID=sid; __Secure-1PSIDTS=old", init.headers["Cookie"])
        val gen = server.takeRequest()
        assertEquals("boq_build", gen.url.queryParameter("bl"))
        assertEquals("-42", gen.url.queryParameter("f.sid"))
        val form = URLDecoder.decode(gen.body!!.utf8(), "UTF-8")
        assertTrue(form, form.startsWith("at=tok123&f.req="))
        assertTrue(form, form.contains("say hi"))
    }

