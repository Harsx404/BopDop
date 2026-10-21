package com.dopdop.bopdop.data.spotify

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.dopdop.bopdop.data.net.Http
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Access tokens for the signed-in Spotify account.
 *
 * The web player signs its own token request in ways that change with every rebuild, so BopDop
 * doesn't imitate it: it opens open.spotify.com in an invisible WebView with the account's cookie,
 * lets Spotify's own script fetch `/api/token`, and reads the answer as it passes. Tokens last
 * about an hour and are reused until then. Pathfinder also wants a client token, which is minted
 * from the web player's version and device id.
 */
object SpotifyToken {
    private const val TAG = "SpotifyToken"
    const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    private lateinit var app: Context
    private val lock = Mutex()

    private data class Token(val bearer: String, val expiresAt: Long, val clientId: String?)
    @Volatile private var token: Token? = null
    @Volatile private var client: Pair<String, Long>? = null

    fun init(context: Context) { app = context.applicationContext }

    fun invalidate() { token = null; client = null; retryAt = 0L; clientRetryAt = 0L }

    /** A signed-in bearer token, or null when signed out or Spotify wouldn't give one. */
    /** After a failed attempt, no new hidden web player for a while (each one is a full browser page). */
    @Volatile private var retryAt = 0L

    suspend fun bearer(): String? = lock.withLock {
        val cookie = SpotifyAccount.cookie ?: return null
        token?.takeIf { it.expiresAt - System.currentTimeMillis() > 60_000 }?.let { return it.bearer }
        if (System.currentTimeMillis() < retryAt) return null
        val fresh = withContext(Dispatchers.Main) { withTimeoutOrNull(20_000) { harvest(cookie) } }
        if (fresh == null) { Log.w(TAG, "no token from the web player"); retryAt = System.currentTimeMillis() + 5 * 60_000 }
        else Log.i(TAG, "token ok, valid ${(fresh.expiresAt - System.currentTimeMillis()) / 60_000} min")
        token = fresh
        fresh?.bearer
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun harvest(cookie: String): Token? {
        val jar = CookieManager.getInstance()
        jar.setAcceptCookie(true)
        jar.setCookie(SpotifyAccount.ORIGIN, "sp_dc=$cookie; Domain=.spotify.com; Path=/; Secure")
        jar.flush()
        val result = CompletableDeferred<Token?>()
        val view = WebView(app)
        try {
            view.settings.javaScriptEnabled = true
            view.settings.domStorageEnabled = true
            view.settings.userAgentString = UA
            // Only the page's scripts are needed to mint a token: no pictures, fonts or media.
            view.settings.blockNetworkImage = true
            view.settings.mediaPlaybackRequiresUserGesture = true
            view.addJavascriptInterface(Bridge(result), "BopDopToken")
            view.webViewClient = object : WebViewClient() {
                override fun onPageStarted(v: WebView, url: String?, favicon: android.graphics.Bitmap?) { v.evaluateJavascript(HOOK, null) }
                override fun onPageFinished(v: WebView, url: String?) { v.evaluateJavascript(HOOK, null) }
                override fun shouldInterceptRequest(v: WebView, r: android.webkit.WebResourceRequest): android.webkit.WebResourceResponse? {
                    val path = r.url.path.orEmpty().lowercase()
                    val skip = r.url.host?.contains("scdn.co") == true && !path.endsWith(".js") ||
                        listOf(".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg", ".woff", ".woff2", ".ttf", ".mp4", ".mp3", ".css").any(path::endsWith)
                    return if (skip) android.webkit.WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0))) else null
                }
            }
            view.loadUrl(SpotifyAccount.ORIGIN + "/")
            return result.await()
        } finally {
            Handler(Looper.getMainLooper()).post { runCatching { view.stopLoading(); view.destroy() } }
        }
    }

    private class Bridge(val out: CompletableDeferred<Token?>) {
        @JavascriptInterface
        fun token(body: String?) {
            if (body.isNullOrBlank() || out.isCompleted) return
            runCatching {
                val o = JSONObject(body)
                // The page first mints an anonymous token; only the signed-in one reads the library.
                if (o.optBoolean("isAnonymous")) return
                val bearer = o.optString("accessToken").ifBlank { return }
                val exp = o.optLong("accessTokenExpirationTimestampMs").takeIf { it > System.currentTimeMillis() }
                    ?: (System.currentTimeMillis() + 50 * 60_000)
                out.complete(Token(bearer, exp, o.optString("clientId").ifBlank { null }))
            }
        }
    }

    /** Wraps fetch and XHR in the page so `/api/token` answers are reported to [Bridge]. */
    private val HOOK = """
        (function () {
          if (window.__bopdopToken) return; window.__bopdopToken = true;
          var send = function (b) { try { BopDopToken.token(b); } catch (e) {} };
          var isToken = function (u) { try { return String(u).indexOf('/api/token') !== -1; } catch (e) { return false; } };
          var f = window.fetch;
          if (f) window.fetch = function (input, init) {
            var url = (input && input.url) ? input.url : input;
            var p = f.apply(this, arguments);
            if (isToken(url)) p.then(function (r) { r.clone().text().then(send).catch(function () {}); }).catch(function () {});
            return p;
          };
          var open = XMLHttpRequest.prototype.open, sendX = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.open = function (m, u) { this.__u = u; return open.apply(this, arguments); };
          XMLHttpRequest.prototype.send = function () {
            var x = this;
            x.addEventListener('load', function () { if (isToken(x.__u)) send(x.responseText); });
            return sendX.apply(this, arguments);
          };
        })();
    """.trimIndent()

    /** Pathfinder's second header. Best-effort: requests go without it if this fails. */
    @Volatile private var clientRetryAt = 0L

    suspend fun clientToken(): String? = withContext(Dispatchers.IO) {
        client?.takeIf { it.second - System.currentTimeMillis() > 60_000 }?.let { return@withContext it.first }
        if (System.currentTimeMillis() < clientRetryAt) return@withContext null
        val clientId = token?.clientId ?: return@withContext null
        clientRetryAt = System.currentTimeMillis() + 10 * 60_000
        runCatching {
            val page = Http.client.newCall(Request.Builder().url(SpotifyAccount.ORIGIN).header("User-Agent", UA).build()).execute()
            val (html, deviceId) = page.use { r ->
                r.body?.string().orEmpty() to r.headers("Set-Cookie").firstNotNullOfOrNull { Regex("sp_t=([^;]+)").find(it)?.groupValues?.get(1) }
            }
            val config = Regex("""<script id="appServerConfig" type="text/plain">([^<]+)</script>""").find(html)?.groupValues?.get(1) ?: return@runCatching null
            val version = JSONObject(String(Base64.decode(config, Base64.DEFAULT))).optString("clientVersion").ifBlank { return@runCatching null }
            val body = JSONObject().put("client_data", JSONObject()
                .put("client_version", version).put("client_id", clientId)
                .put("js_sdk_data", JSONObject()
                    .put("device_brand", "unknown").put("device_model", "unknown").put("os", "windows").put("os_version", "NT 10.0")
                    .put("device_id", deviceId ?: java.util.UUID.randomUUID().toString()).put("device_type", "computer")))
            val req = Request.Builder().url("https://clienttoken.spotify.com/v1/clienttoken")
                .post(body.toString().toByteArray().toRequestBody("application/json".toMediaType()))
                .header("Accept", "application/json").header("User-Agent", UA).build()
            Http.client.newCall(req).execute().use { r ->
                val granted = JSONObject(r.body?.string().orEmpty()).optJSONObject("granted_token") ?: return@runCatching null
                val t = granted.optString("token").ifBlank { return@runCatching null }
                client = t to (System.currentTimeMillis() + granted.optLong("expires_after_seconds", 1200) * 1000)
                t
            }
        }.onFailure { Log.w(TAG, "client token: ${it.message}") }.getOrNull()
    }
}
