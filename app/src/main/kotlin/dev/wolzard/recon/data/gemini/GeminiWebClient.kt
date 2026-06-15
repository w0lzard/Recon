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
