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

    @Test fun parseAcceptsTheFormsDevToolsCopies() {
        val want = CookieHeader.Parsed.Ok("NID=1; __Secure-1PSID=g.a0-x_y/z.; __Secure-1PSIDTS=sidts-1")
        assertEquals(want, CookieHeader.parse("NID=1; __Secure-1PSID=g.a0-x_y/z.; __Secure-1PSIDTS=sidts-1"))
        assertEquals(want, CookieHeader.parse("  Cookie: NID=1; __Secure-1PSID=g.a0-x_y/z.; __Secure-1PSIDTS=sidts-1;\n"))
        assertEquals(want, CookieHeader.parse("cookie:NID=1;__Secure-1PSID=g.a0-x_y/z.;__Secure-1PSIDTS=sidts-1"))
        assertEquals(want, CookieHeader.parse("NID=1\n__Secure-1PSID=g.a0-x_y/z.\r\n__Secure-1PSIDTS=\"sidts-1\""))
    }

    @Test fun parseRejectsMalformedInputWithoutEchoingValues() {
        assertEquals(CookieHeader.Parsed.Empty, CookieHeader.parse("   "))
        assertEquals(CookieHeader.Parsed.Empty, CookieHeader.parse("Cookie: ;"))
        assertEquals(CookieHeader.Parsed.Malformed(null), CookieHeader.parse("just some text"))
        assertEquals(CookieHeader.Parsed.Malformed(null), CookieHeader.parse("=value; __Secure-1PSID=x"))
        assertEquals(CookieHeader.Parsed.Malformed(null), CookieHeader.parse("bad name=1; __Secure-1PSID=x"))
        assertEquals(CookieHeader.Parsed.Malformed("__Secure-1PSID"), CookieHeader.parse("__Secure-1PSID=has space"))
        assertEquals(CookieHeader.Parsed.Malformed("SID"), CookieHeader.parse("SID=a,b; __Secure-1PSID=x"))
        assertEquals(CookieHeader.Parsed.Malformed("SID"), CookieHeader.parse("SID=a\\b; __Secure-1PSID=x"))
        assertEquals(CookieHeader.Parsed.Malformed("SID"), CookieHeader.parse("SID=\u0000; __Secure-1PSID=x"))
    }

    @Test fun parseNeedsANonEmptySessionCookie() {
        assertEquals(CookieHeader.Parsed.NoSession, CookieHeader.parse("NID=1; SID=2"))
        assertEquals(CookieHeader.Parsed.NoSession, CookieHeader.parse("__Secure-1PSID=; NID=1"))
        assertEquals(CookieHeader.Parsed.NoSession, CookieHeader.parse("__Secure-1PSIDTS=x")) // the timestamp cookie alone is not a session
    }
}
