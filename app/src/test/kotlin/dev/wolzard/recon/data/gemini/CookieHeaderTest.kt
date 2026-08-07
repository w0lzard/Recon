package dev.wolzard.recon.data.gemini

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CookieHeaderTest {
    private val jar = "NID=511=abc; SID=g.a000sid; __Secure-1PSID=g.a000psid; __Secure-1PSIDTS=sidts-xyz; SAPISID=sap/123; AEC=aec1; SOCS=soc"

    @Test fun redactHidesEveryGoogleSessionValue() {
        val out = CookieHeader.redact("failed with $jar")
        listOf("511=abc", "g.a000sid", "g.a000psid", "sidts-xyz", "sap/123", "aec1", "soc").forEach { assertFalse(out, out.contains(it)) }
        assertEquals(
            "failed with NID=<redacted>; SID=<redacted>; __Secure-1PSID=<redacted>; __Secure-1PSIDTS=<redacted>; SAPISID=<redacted>; AEC=<redacted>; SOCS=<redacted>",
            out,
        )
    }

    @Test fun redactHidesWholeCookieHeaderLines() {
        val out = CookieHeader.redact("GET /app\nCookie: foo=bar; baz=qux\nset-cookie: x=y; Path=/\nAccept: */*")
        assertEquals("GET /app\nCookie: <redacted>\nset-cookie: <redacted>\nAccept: */*", out)
    }

    @Test fun redactLeavesOrdinaryTextAlone() {
        val text = "SocketTimeoutException: timeout on https://gemini.google.com/app?hl=en&_reqid=12345"
        assertEquals(text, CookieHeader.redact(text))
    }
}
