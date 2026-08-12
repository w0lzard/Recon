package dev.wolzard.recon.data.gemini

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** [url] is where the call ended after redirects. */
class HttpResult(val code: Int, val body: String, val headers: Headers, val url: String = "")

/** Executes the call off-thread; cancelling the coroutine cancels the HTTP call. */
suspend fun Call.await(): HttpResult = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
        override fun onResponse(call: Call, response: Response) {
            val result = try {
                response.use { HttpResult(it.code, it.body.string(), it.headers, it.request.url.toString()) }
            } catch (e: IOException) {
                cont.resumeWithException(e); return
            }
            cont.resume(result)
        }
    })
}
