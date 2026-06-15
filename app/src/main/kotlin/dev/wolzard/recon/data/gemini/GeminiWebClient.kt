package dev.wolzard.recon.data.gemini

import dev.wolzard.recon.core.model.AnalyzerException
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.port.Secret
import dev.wolzard.recon.core.port.SecretStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * The gemini.google.com web protocol, signed in with the user's own browser cookies (D22).
 * Ported from gemini-webapi (github.com/HanaokaYuzu/Gemini-API @ 1ad0d4a, 2026-10-08); unofficial, so
 * Google can change it at any time. Cookies are kept as one Cookie header string in [Secret.GEMINI_COOKIES]
 * and are never logged. Chats are temporary, so jobs don't appear in the user's Gemini history.
 */
class GeminiWebClient(
    client: OkHttpClient,
    private val secrets: SecretStore,
    private val appUrl: String = "https://gemini.google.com",
    private val uploadUrl: String = "https://content-push.googleapis.com/upload",
    private val rotateUrl: String = "https://accounts.google.com/RotateCookies",
) {
    private data class Session(val at: String, val bl: String?, val sid: String?, val hl: String, val pushId: String)

    // Generation can take ~2 min under load; RunJob's own timeouts cancel the call earlier if needed.
    private val http = client.newBuilder().readTimeout(180, TimeUnit.SECONDS).writeTimeout(180, TimeUnit.SECONDS).build()
    private val json = Json { isLenient = true }
    private val lock = Mutex()
    private var session: Session? = null
    private var reqId = Random.nextInt(10_000, 100_000)

    /** Uploads one file and returns the id that [generate] takes. */
    suspend fun upload(file: File, mime: String): String {
        val s = session()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(mime.toMediaType())).build()
        val r = send(
            Request.Builder().url(uploadUrl).post(body)
                .header("Origin", ORIGIN).header("Referer", "$ORIGIN/")
                .header("X-Tenant-Id", "bard-storage").header("Push-ID", s.pushId).build(),
            withCookies = false, // a .google.com cookie never goes to googleapis.com; Push-ID authorises the upload
        )
        if (r.code !in 200..299 || r.body.isBlank()) throw AnalyzerException(FailureReason.UNKNOWN, message = "upload HTTP ${r.code}")
        return r.body.trim()
    }

    /** One prompt, with files from [upload] as (id, file name). Returns the reply text. */
    suspend fun generate(prompt: String, files: List<Pair<String, String>> = emptyList()): String {
        val r = postGenerate(session(), prompt, files).takeIf { it.code == 200 }
            ?: run { session = null; postGenerate(session(), prompt, files) } // stale token: re-init once
        if (r.code != 200) throw AnalyzerException(FailureReason.UNKNOWN, message = "generate HTTP ${r.code}")
        return replyText(r.body)
    }

    /** Sign-in check: re-reads the app page with the saved cookies. @throws AnalyzerException */
    suspend fun verify() {
        session = null
        session()
    }

    // --- session: GET /app carries the access token (SNlM0e) and request parameters in the page

    private suspend fun session(): Session = lock.withLock {
        session ?: (initOnce() ?: rotateCookies().let { initOnce() })
            ?.also { session = it } ?: throw AnalyzerException(FailureReason.API_KEY_INVALID, message = "signed out of Gemini")
    }

    private suspend fun initOnce(): Session? {
        val r = send(Request.Builder().url("$appUrl/app").get().header("Origin", ORIGIN).header("Referer", "$ORIGIN/").build())
        if (r.code != 200) return null
        fun field(name: String) = Regex("\"$name\":\\s*\"(.*?)\"").find(r.body)?.groupValues?.get(1)
        val at = field("SNlM0e") ?: return null // signed-out (guest) pages have none
        return Session(at, field("cfb2h"), field("FdrFJe"), field("TuX5cc") ?: "en", field("qKIAYe") ?: DEFAULT_PUSH_ID)
    }

    /** __Secure-1PSIDTS expires often; Google hands out a fresh one here (merged by [send]). */
    private suspend fun rotateCookies() {
        send(
            Request.Builder().url(rotateUrl).header("Origin", "https://accounts.google.com")
                .post("[000,\"-0000000000000000000\"]".toRequestBody("application/json".toMediaType())).build(),
        )
    }

    private suspend fun postGenerate(s: Session, prompt: String, files: List<Pair<String, String>>): HttpResult {
        val uuid = UUID.randomUUID().toString().uppercase()
        val fileData: JsonElement = if (files.isEmpty()) JsonNull else buildJsonArray {
            files.forEach { (id, name) -> add(buildJsonArray { add(buildJsonArray { add(JsonPrimitive(id)) }); add(JsonPrimitive(name)) }) }
        }
        val inner = MutableList<JsonElement>(81) { JsonNull }.apply {
            this[0] = JsonArray(listOf(JsonPrimitive(prompt), JsonPrimitive(0), JsonNull, fileData, JsonNull, JsonNull, JsonPrimitive(0)))
            this[1] = arr(JsonPrimitive(s.hl))
            this[2] = JsonArray(DEFAULT_METADATA)
            this[6] = arr(JsonPrimitive(1))
            this[7] = JsonPrimitive(1)          // streaming
            this[10] = JsonPrimitive(1)
            this[11] = JsonPrimitive(0)
            this[17] = arr(arr(JsonPrimitive(0)))
            this[18] = JsonPrimitive(0)
            this[27] = JsonPrimitive(1)
            this[30] = arr(JsonPrimitive(4))
            this[41] = arr(JsonPrimitive(1))
            this[45] = JsonPrimitive(1)         // temporary chat: kept out of the user's history
            this[53] = JsonPrimitive(0)
            this[59] = JsonPrimitive(uuid)
            this[61] = JsonArray(emptyList())
            this[68] = JsonPrimitive(1)
            this[79] = JsonPrimitive(1)
            this[80] = JsonPrimitive(1)
        }
        val url = "$appUrl/_/BardChatUi/data/assistant.lamda.BardFrontendService/StreamGenerate".toHttpUrl().newBuilder()
            .addQueryParameter("hl", s.hl).addQueryParameter("_reqid", nextReqId().toString()).addQueryParameter("rt", "c")
            .apply { s.bl?.let { addQueryParameter("bl", it) }; s.sid?.let { addQueryParameter("f.sid", it) } }
            .build()
        val form = FormBody.Builder().add("at", s.at)
            .add("f.req", JsonArray(listOf(JsonNull, JsonPrimitive(JsonArray(inner).toString()))).toString()).build()
        return send(
            Request.Builder().url(url).post(form)
                .header("Origin", ORIGIN).header("Referer", "$ORIGIN/").header("X-Same-Domain", "1")
                .header("x-goog-ext-525005358-jspb", "[\"$uuid\",1]").build(),
        )
    }

    private fun nextReqId() = synchronized(this) { reqId.also { reqId += 100_000 } }

    // --- response: ")]}'" then length-prefixed JSON frames; the reply text is cumulative, so the last one wins

    internal fun replyText(body: String): String {
        var text: String? = null
        for (part in frames(body)) {
