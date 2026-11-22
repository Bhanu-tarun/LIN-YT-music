package com.lin.music.core.innertube.client

import com.lin.music.core.model.ContentRegions
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class YouTubeClient(
    val clientName: String,
    val clientVersion: String,
    val clientId: String,
    val userAgent: String,
    val apiHost: String = "https://youtubei.googleapis.com/youtubei/v1/",
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: Int? = null,
    val supportsAuthCookies: Boolean = false
) {
    WEB_REMIX(
        clientName = "WEB_REMIX",
        clientVersion = "1.20260804.16.00",
        clientId = "67",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0",
        apiHost = "https://music.youtube.com/youtubei/v1/",
        supportsAuthCookies = true
    ),
    ANDROID_VR(
        clientName = "ANDROID_VR",
        clientVersion = "1.65.10",
        clientId = "28",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
        apiHost = "https://youtubei.googleapis.com/youtubei/v1/",
        osName = "Android",
        osVersion = "12L",
        deviceMake = "Oculus",
        deviceModel = "Quest 3",
        androidSdkVersion = 32,
        supportsAuthCookies = false
    ),
    VISIONOS(
        clientName = "VISIONOS",
        clientVersion = "1.04",
        clientId = "101",
        userAgent = "com.google.ios.youtube/1.04 (RealityDevice17,1; U; CPU OS 26_6_0 like Mac OS X; en_US)",
        apiHost = "https://youtubei.googleapis.com/youtubei/v1/",
        osName = "visionOS",
        osVersion = "26.6.0.23O770",
        deviceMake = "Apple",
        deviceModel = "RealityDevice17,1",
        supportsAuthCookies = false
    ),
    IOS(
        clientName = "IOS",
        clientVersion = "20.10.4",
        clientId = "5",
        userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
        apiHost = "https://youtubei.googleapis.com/youtubei/v1/",
        osName = "iPhone",
        osVersion = "18.3.2.22D82",
        deviceMake = "Apple",
        deviceModel = "iPhone16,2",
        supportsAuthCookies = false
    ),
    TVHTML5(
        clientName = "TVHTML5",
        clientVersion = "7.20250212.16.00",
        clientId = "7",
        userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
        apiHost = "https://youtubei.googleapis.com/youtubei/v1/",
        supportsAuthCookies = false
    );

    fun buildContextJson(
        visitorData: String? = null,
        regionCode: String = ContentRegions.DEFAULT_CODE
    ): JsonObject = buildJsonObject {
        val gl = ContentRegions.normalize(regionCode)
        put("client", buildJsonObject {
            put("clientName", clientName)
            put("clientVersion", clientVersion)
            put("hl", "en")
            put("gl", gl)
            if (!visitorData.isNullOrBlank()) {
                put("visitorData", visitorData)
            }
            osName?.let { put("osName", it) }
            osVersion?.let { put("osVersion", it) }
            deviceMake?.let { put("deviceMake", it) }
            deviceModel?.let { put("deviceModel", it) }
            androidSdkVersion?.let { put("androidSdkVersion", it) }
        })
        put("user", buildJsonObject {
            put("lockedSafetyMode", false)
        })
        put("request", buildJsonObject {
            put("useSsl", true)
        })
    }
}
