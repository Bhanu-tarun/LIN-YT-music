package com.lin.music.core.innertube.auth

import com.lin.music.core.model.AccountSession
import java.security.MessageDigest

object SapisidAuthSigner {

    private const val YTM_ORIGIN = "https://music.youtube.com"

    fun parseCookies(cookieHeader: String): Map<String, String> {
        if (cookieHeader.isBlank()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        cookieHeader.split(';').forEach { part ->
            val idx = part.indexOf('=')
            if (idx > 0) {
                val key = part.substring(0, idx).trim()
                val value = part.substring(idx + 1).trim()
                if (key.isNotEmpty()) {
                    map[key] = value
                }
            }
        }
        return map
    }

    /**
     * Merges multiple cookie strings without duplicate keys; later arguments override earlier ones.
     */
    fun deduplicateCookies(vararg cookieHeaders: String): String {
        val merged = LinkedHashMap<String, String>()
        cookieHeaders.forEach { header ->
            parseCookies(header).forEach { (k, v) ->
                if (v.isNotEmpty()) merged[k] = v
            }
        }
        return merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun extractSapisid(cookieHeader: String): String? {
        val cookies = parseCookies(cookieHeader)
        return cookies["SAPISID"]
            ?: cookies["__Secure-3PAPISID"]
            ?: cookies["__Secure-1PAPISID"]
    }

    fun buildAuthorizationHeader(
        cookieHeader: String,
        origin: String = YTM_ORIGIN,
        timestampSeconds: Long = System.currentTimeMillis() / 1000L
    ): String? {
        val cookies = parseCookies(cookieHeader)
        val sapisid = cookies["SAPISID"] ?: cookies["__Secure-3PAPISID"] ?: cookies["__Secure-1PAPISID"]
        val p1 = cookies["__Secure-1PAPISID"]
        val p3 = cookies["__Secure-3PAPISID"]

        if (sapisid.isNullOrBlank() && p1.isNullOrBlank() && p3.isNullOrBlank()) {
            return null
        }

        val parts = mutableListOf<String>()
        if (!sapisid.isNullOrBlank()) {
            parts += "SAPISIDHASH ${timestampSeconds}_${sha1Hex("$timestampSeconds $sapisid $origin")}"
        }
        if (!p1.isNullOrBlank()) {
            parts += "SAPISID1PHASH ${timestampSeconds}_${sha1Hex("$timestampSeconds $p1 $origin")}"
        }
        if (!p3.isNullOrBlank()) {
            parts += "SAPISID3PHASH ${timestampSeconds}_${sha1Hex("$timestampSeconds $p3 $origin")}"
        }
        return parts.joinToString(" ")
    }

    fun buildSessionHeaders(
        session: AccountSession?,
        origin: String = YTM_ORIGIN,
        timestampSeconds: Long = System.currentTimeMillis() / 1000L
    ): Map<String, String> {
        val headers = linkedMapOf(
            "Origin" to origin,
            "Referer" to "$origin/",
            "X-Origin" to origin,
            "X-Goog-AuthUser" to "0",
            "Accept-Language" to "en-US,en;q=0.9"
        )
        if (session == null) return headers

        val visitorData = session.visitorData
        if (!visitorData.isNullOrBlank()) {
            headers["X-Goog-Visitor-Id"] = visitorData
        }

        // Only send X-Goog-PageId when a real delegated Brand Account ID is present
        val dataSyncId = session.dataSyncId
        if (!dataSyncId.isNullOrBlank()) {
            val delegated = if (dataSyncId.contains("||")) {
                val parts = dataSyncId.split("||")
                val first = parts.getOrNull(0)?.trim().orEmpty()
                val second = parts.getOrNull(1)?.trim().orEmpty()
                if (first.isNotBlank() && second.isNotBlank()) first else null
            } else {
                dataSyncId.trim().takeIf { it.isNotBlank() }
            }
            if (delegated != null) {
                headers["X-Goog-PageId"] = delegated
            }
        }

        val cleanCookies = deduplicateCookies(session.cookies)
        if (cleanCookies.isNotBlank()) {
            headers["Cookie"] = cleanCookies
            buildAuthorizationHeader(cleanCookies, origin, timestampSeconds)?.let { auth ->
                headers["Authorization"] = auth
                headers["X-Youtube-Bootstrap-Logged-In"] = "true"
            }
        }
        return headers
    }

    internal fun sha1Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
