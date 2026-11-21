package com.lin.music.core.innertube.stream

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

class NewPipeDownloaderImpl(
    private val client: OkHttpClient
) : Downloader() {

    override fun execute(request: NpRequest): NpResponse {
        val method = request.httpMethod()
        val body = request.dataToSend()?.toRequestBody()
        val builder = Request.Builder()
            .url(request.url())
            .method(method, body)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0"
            )

        request.headers().forEach { (key, values) ->
            values.forEach { value ->
                builder.addHeader(key, value)
            }
        }

        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            }
            val responseBody = response.body?.string().orEmpty()
            val latestUrl = response.request.url.toString()
            return NpResponse(
                response.code,
                response.message,
                response.headers.toMultimap(),
                responseBody,
                latestUrl
            )
        }
    }
}
