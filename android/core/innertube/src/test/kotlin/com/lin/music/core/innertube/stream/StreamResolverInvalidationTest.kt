package com.lin.music.core.innertube.stream

import com.lin.music.core.innertube.InnerTubeApi
import com.lin.music.core.innertube.SessionProvider
import com.lin.music.core.model.AccountSession
import com.lin.music.core.model.AudioQuality
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class StreamResolverInvalidationTest {

    private lateinit var server: MockWebServer
    private lateinit var resolver: StreamResolver

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val sessionProvider = object : SessionProvider {
            override suspend fun currentSession(): AccountSession? = null
        }
        val api = InnerTubeApi(sessionProvider, OkHttpClient()).apply {
            baseUrl = server.url("/").toString()
        }
        resolver = StreamResolver(api, OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueVisitor(visitorData: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"responseContext":{"visitorData":"$visitorData"}}""")
        )
    }

    private fun enqueuePlayer(streamUrl: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "playabilityStatus": { "status": "OK" },
                      "streamingData": {
                        "expiresInSeconds": "21600",
                        "adaptiveFormats": [
                          {
                            "itag": 251,
                            "mimeType": "audio/webm; codecs=\"opus\"",
                            "bitrate": 135000,
                            "url": "$streamUrl"
                          }
                        ]
                      }
                    }
                    """.trimIndent()
                )
        )
    }

    @Test
    fun `invalidateStream with clearVisitorData drops cached URL and re-fetches fresh visitorData and URL`() = runTest {
        enqueueVisitor("VISITOR_VPN_1")
        enqueuePlayer("https://cdn.example/videoplayback?v=1&ip=10.0.0.1")

        val first = resolver.resolveStream("vid_vpn", AudioQuality.HIGH)
        assertEquals("https://cdn.example/videoplayback?v=1&ip=10.0.0.1", first.url)

        // Cached hit should not hit MockWebServer
        val cached = resolver.resolveStream("vid_vpn", AudioQuality.HIGH)
        assertEquals(first.url, cached.url)
        assertEquals(2, server.requestCount)

        // Simulate VPN disconnect -> invalidate stream & visitorData
        enqueueVisitor("VISITOR_HOME_2")
        enqueuePlayer("https://cdn.example/videoplayback?v=2&ip=192.168.1.5")

        resolver.invalidateStream("vid_vpn", clearVisitorData = true)

        val refreshed = resolver.resolveStream("vid_vpn", AudioQuality.HIGH)
        assertEquals("https://cdn.example/videoplayback?v=2&ip=192.168.1.5", refreshed.url)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `invalidateAllStreams clears cached URLs across all tracks`() = runTest {
        enqueueVisitor("VISITOR_1")
        enqueuePlayer("https://cdn.example/videoplayback?id=a1")
        assertEquals("https://cdn.example/videoplayback?id=a1", resolver.resolveStream("vid_a", AudioQuality.HIGH).url)

        resolver.invalidateAllStreams()

        enqueueVisitor("VISITOR_2")
        enqueuePlayer("https://cdn.example/videoplayback?id=a2")
        assertEquals("https://cdn.example/videoplayback?id=a2", resolver.resolveStream("vid_a", AudioQuality.HIGH).url)
        assertEquals(4, server.requestCount)
    }
}
