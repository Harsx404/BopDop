package com.dopdop.bopdop.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dopdop.bopdop.data.account.AccountStore
import com.dopdop.bopdop.data.ytm.YtMusic
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.components.ScreenCache
import com.dopdop.bopdop.ui.glass.glassEdges
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import kotlinx.coroutines.launch

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true&continue=https%3A%2F%2Fmusic.youtube.com%2F"

/**
 * Google sign-in for YouTube Music, in a WebView. Once Google hands us back
 * to music.youtube.com with a session cookie, we keep the cookies and pull
 * the account's name and photo; everything after that is signed requests.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SignInScreen() {
    val app = LocalApp.current
    var progress by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }

    fun finish(cookies: String) {
        if (done || !AccountStore.signIn(cookies)) return
        done = true
        YtMusic.clearCache()
        ScreenCache.put("home", null)
        app.scope.launch {
            runCatching { YtMusic.accountInfo() }.getOrNull()?.let(AccountStore::setProfile)
            app.toast("Signed in as ${AccountStore.account.value?.name ?: "YouTube Music"}")
        }
        app.nav.popBackStack()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.windowInsetsPadding(WindowInsets.statusBars).fillMaxWidth().height(56.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton { app.nav.popBackStack() }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sign in", style = BopType.Headline, color = Bop.Text)
                Text("YouTube Music · Google account", style = BopType.Caption, color = Bop.Text3)
            }
            Spacer(Modifier.width(42.dp))
        }
        if (progress in 1..99) LinearProgressIndicator(
            progress = { progress / 100f }, color = Bop.Accent, trackColor = Color.Transparent,
            modifier = Modifier.fillMaxWidth().height(2.dp),
        )
        val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
        Box(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp).padding(top = 6.dp)
                .clip(shape).background(Color.White).glassEdges(shape, 0.6f),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, p: Int) { progress = p }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (url?.startsWith(AccountStore.ORIGIN) == true) {
                                    CookieManager.getInstance().flush()
                                    CookieManager.getInstance().getCookie(AccountStore.ORIGIN)?.let(::finish)
                                }
                            }
                        }
                        loadUrl(LOGIN_URL)
                    }
                },
                onRelease = { it.destroy() },
            )
        }
        Text(
            "Your password goes straight to Google. BopDop only keeps the YouTube Music session on this phone.",
            style = BopType.Caption, color = Bop.Text3, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp).windowInsetsPadding(WindowInsets.navigationBars),
        )
    }
}
