package com.lin.music.feature.auth

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lin.music.core.designsystem.components.LinInvertedButton
import com.lin.music.core.designsystem.components.LinOutlinedButton
import com.lin.music.core.designsystem.theme.APP_BRAND_NAME
import com.lin.music.core.designsystem.theme.LinTextStyles
import com.lin.music.core.designsystem.theme.LinTheme
import com.lin.music.core.designsystem.theme.linBrandText
import com.lin.music.core.innertube.auth.SapisidAuthSigner
import com.lin.music.core.model.AccountSession
import org.json.JSONObject

private const val YTM_LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true&continue=https%3A%2F%2Fmusic.youtube.com%2F"
private const val YTM_HOME_URL = "https://music.youtube.com"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YtmLoginScreen(
    accentColor: Color,
    onLoggedIn: (AccountSession) -> Unit,
    modifier: Modifier = Modifier
) {
    val linColors = LinTheme.colors
    var showWebView by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var alreadyCaptured by remember { mutableStateOf(false) }

    fun captureSessionFromCookies(webView: WebView?, requireLoggedInFlag: Boolean) {
        if (alreadyCaptured) return
        val cm = CookieManager.getInstance()
        cm.flush()
        val ytmCookies = cm.getCookie(YTM_HOME_URL).orEmpty()
        val ytCookies = cm.getCookie("https://www.youtube.com").orEmpty()
        val combinedCookies = SapisidAuthSigner.deduplicateCookies(ytCookies, ytmCookies)
        val sapisid = SapisidAuthSigner.extractSapisid(combinedCookies) ?: return

        if (webView != null) {
            webView.evaluateJavascript(
                """
                (function() {
                    try {
                        var cfg = window.ytcfg;
                        if (cfg && cfg.get) {
                            return JSON.stringify({
                                loggedIn: Boolean(cfg.get('LOGGED_IN')),
                                visitorData: cfg.get('VISITOR_DATA') || null,
                                delegatedSessionId: cfg.get('DELEGATED_SESSION_ID') || null
                            });
                        }
                    } catch (e) {}
                    return "{}";
                })();
                """.trimIndent()
            ) { raw ->
                if (alreadyCaptured) return@evaluateJavascript
                val cleaned = raw?.trim()?.removeSurrounding("\"")
                    ?.replace("\\\"", "\"")
                    ?.replace("\\\\", "\\")
                val obj = runCatching { JSONObject(cleaned ?: "{}") }.getOrNull()
                val loggedIn = obj?.optBoolean("loggedIn", false) ?: false
                if (requireLoggedInFlag && !loggedIn) {
                    return@evaluateJavascript
                }
                alreadyCaptured = true
                val visitorData = obj?.optString("visitorData")?.takeIf { it.isNotBlank() && it != "null" }
                val delegatedId = obj?.optString("delegatedSessionId")?.takeIf { it.isNotBlank() && it != "null" }
                onLoggedIn(
                    AccountSession(
                        cookies = combinedCookies,
                        sapisid = sapisid,
                        visitorData = visitorData,
                        dataSyncId = delegatedId
                    )
                )
            }
        } else {
            alreadyCaptured = true
            onLoggedIn(AccountSession(cookies = combinedCookies, sapisid = sapisid))
        }
    }

    if (!showWebView) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(28.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(APP_BRAND_NAME, style = LinTextStyles.brand, color = accentColor)
                Text(
                    text = "youtube music,\nwithout the noise.",
                    style = LinTextStyles.drilldownTitle,
                    color = linColors.textPrimary
                )
                Text(
                    text = "sign in with your google account to stream natively from your youtube music library, mixes & playlists.",
                    style = LinTextStyles.subtitle,
                    color = linColors.textSecondary
                )
                Spacer(Modifier.height(8.dp))
                LinInvertedButton(
                    text = "sign in with youtube music",
                    onClick = { showWebView = true }
                )
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(linColors.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, linColors.glassBorder)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = linBrandText("$APP_BRAND_NAME · SIGN IN", accentColor, linColors.textPrimary),
                    style = LinTextStyles.brand
                )
                Text("sign in, then tap done if not redirected", style = LinTextStyles.subtitle, color = linColors.textSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinOutlinedButton(
                    text = "done",
                    onClick = { captureSessionFromCookies(webViewRef, requireLoggedInFlag = false) }
                )
                LinOutlinedButton(
                    text = "back",
                    onClick = { showWebView = false }
                )
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (url != null && url.startsWith("https://music.youtube.com")) {
                                captureSessionFromCookies(view, requireLoggedInFlag = true)
                            }
                        }
                    }
                    loadUrl(YTM_LOGIN_URL)
                    webViewRef = this
                }
            }
        )
    }
}
