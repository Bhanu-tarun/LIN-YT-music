package com.lin.music.core.innertube.auth

import com.lin.music.core.model.AccountSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SapisidAuthSignerTest {

    @Test
    fun `extractSapisid prefers SAPISID then 3PAPISID`() {
        val cookie1 = "SID=abc; SAPISID=mysapisid123; __Secure-3PAPISID=alt3p"
        assertEquals("mysapisid123", SapisidAuthSigner.extractSapisid(cookie1))

        val cookie2 = "SID=abc; __Secure-3PAPISID=alt3p123"
        assertEquals("alt3p123", SapisidAuthSigner.extractSapisid(cookie2))

        assertNull(SapisidAuthSigner.extractSapisid("SID=abc; HSID=xyz"))
    }

    @Test
    fun `buildAuthorizationHeader computes deterministic SHA1 SAPISIDHASH`() {
        val ts = 1700000000L
        val sapisid = "TEST_SAPISID_VALUE"
        val origin = "https://music.youtube.com"
        val expectedSha1 = SapisidAuthSigner.sha1Hex("$ts $sapisid $origin")

        val header = SapisidAuthSigner.buildAuthorizationHeader(
            cookieHeader = "SAPISID=$sapisid; SID=foo",
            origin = origin,
            timestampSeconds = ts
        )

        assertEquals("SAPISIDHASH ${ts}_$expectedSha1", header)
        assertEquals(40, expectedSha1.length)
    }

    @Test
    fun `buildSessionHeaders includes pageId and visitorId when present`() {
        val session = AccountSession(
            cookies = "SAPISID=abc; __Secure-1PAPISID=p1; __Secure-3PAPISID=p3",
            sapisid = "abc",
            visitorData = "CgtdemoVisitor",
            dataSyncId = "1234567890||987654"
        )
        val headers = SapisidAuthSigner.buildSessionHeaders(session, timestampSeconds = 1700000000L)
        assertEquals("https://music.youtube.com", headers["Origin"])
        assertEquals("CgtdemoVisitor", headers["X-Goog-Visitor-Id"])
        assertEquals("1234567890", headers["X-Goog-PageId"])
        val auth = headers["Authorization"]
        assertNotNull(auth)
        assertTrue(auth!!.contains("SAPISIDHASH"))
        assertTrue(auth.contains("SAPISID1PHASH"))
        assertTrue(auth.contains("SAPISID3PHASH"))
        assertEquals("true", headers["X-Youtube-Bootstrap-Logged-In"])
    }

    @Test
    fun `deduplicateCookies merges and overrides earlier duplicate keys`() {
        val merged = SapisidAuthSigner.deduplicateCookies(
            "SID=old; SAPISID=old_s; FOO=bar",
            "SID=new; SAPISID=new_s"
        )
        val parsed = SapisidAuthSigner.parseCookies(merged)
        assertEquals("new", parsed["SID"])
        assertEquals("new_s", parsed["SAPISID"])
        assertEquals("bar", parsed["FOO"])
    }

    @Test
    fun `buildSessionHeaders omits pageId for non-delegated personal dataSyncId`() {
        val session = AccountSession(
            cookies = "SAPISID=abc",
            sapisid = "abc",
            dataSyncId = "1234567890||"
        )
        val headers = SapisidAuthSigner.buildSessionHeaders(session, timestampSeconds = 1700000000L)
        assertNull(headers["X-Goog-PageId"])
    }
}
