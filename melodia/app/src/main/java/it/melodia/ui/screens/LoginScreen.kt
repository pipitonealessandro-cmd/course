package it.melodia.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import it.melodia.MelodiaApp
import it.melodia.ui.LocalActions
import org.json.JSONArray

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true" +
        "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"

/**
 * Google sign-in inside a WebView. Once the browser lands on music.youtube.com with a session
 * cookie, we store the cookies (never the password) and go back.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(contentPadding: PaddingValues) {
    val actions = LocalActions.current
    val app = MelodiaApp.instance
    var done by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose { webView?.destroy() }
    }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        TopBar("Accedi con YouTube") { actions.nav.popBackStack() }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Google refuses sign-in from user agents that look like an embedded WebView.
                    settings.userAgentString = settings.userAgentString.replace("; wv", "")
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            if (done || url == null || !url.startsWith("https://music.youtube.com")) return
                            val cookies = CookieManager.getInstance().getCookie("https://music.youtube.com") ?: return
                            if (!cookies.contains("SAPISID")) return
                            view.evaluateJavascript(
                                "(function(){try{return JSON.stringify([window.yt.config_.VISITOR_DATA||null, window.yt.config_.DATASYNC_ID||null]);}catch(e){return '[null,null]';}})()"
                            ) { raw ->
                                if (done) return@evaluateJavascript
                                done = true
                                var visitor: String? = null
                                var dataSync: String? = null
                                runCatching {
                                    // evaluateJavascript returns a JSON-encoded string.
                                    val inner = org.json.JSONTokener(raw).nextValue() as String
                                    val arr = JSONArray(inner)
                                    visitor = arr.optString(0).takeIf { it.isNotBlank() && it != "null" }
                                    dataSync = arr.optString(1).takeIf { it.isNotBlank() && it != "null" }
                                }
                                CookieManager.getInstance().flush()
                                app.login(cookies, visitor, dataSync)
                                app.player.message("Accesso effettuato")
                                actions.nav.popBackStack()
                            }
                        }
                    }
                    loadUrl(LOGIN_URL)
                }
            },
        )
    }
}
