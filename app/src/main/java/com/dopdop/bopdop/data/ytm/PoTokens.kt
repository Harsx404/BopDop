package com.dopdop.bopdop.data.ytm

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.dopdop.bopdop.data.net.Http
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * YouTube Proof-of-Origin tokens, minted by Google's BotGuard running inside a
 * hidden WebView — a real browser engine, so the tokens are accepted. Same
 * protocol the DopDop desktop runs under jsdom (bgutils): Create → snapshot →
 * GenerateIT → integrity token → minter → mint(identifier).
 *
 * Player-request token is bound to the video id, the streaming (GVS) token to
 * the session's visitorData; NewPipeExtractor appends them where needed.
 */
object PoTokens : PoTokenProvider {
    private const val TAG = "PoTokens"
    private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
    private const val API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
    private const val WAA = "https://jnn-pa.googleapis.com/\$rpc/google.internal.waa.v1.Waa/"

    private lateinit var appContext: Context
    fun init(context: Context) { appContext = context.applicationContext }

    private class Session(val web: WebView, val bridge: Bridge, val visitorData: String, val streamingPot: String, val expiresAt: Long)

    @Volatile private var session: Session? = null
    private val lock = Mutex()

    private class Bridge {
        val botguard = CompletableDeferred<String>()
        val minter = CompletableDeferred<Unit>()
        val tokens = ConcurrentHashMap<Int, CompletableDeferred<String>>()

        @JavascriptInterface fun onBotguard(resp: String) { botguard.complete(resp) }
        @JavascriptInterface fun onMinterReady() { minter.complete(Unit) }
        @JavascriptInterface fun onError(msg: String) {
            val e = IOException("BotGuard: $msg")
            botguard.completeExceptionally(e); minter.completeExceptionally(e)
        }
        @JavascriptInterface fun onToken(id: Int, token: String) { tokens.remove(id)?.complete(token) }
        @JavascriptInterface fun onMintError(id: Int, msg: String) { tokens.remove(id)?.completeExceptionally(IOException(msg)) }
    }

    private val ids = AtomicInteger()

    private fun waa(endpoint: String, body: String): String {
        val req = Request.Builder().url(WAA + endpoint)
            .post(body.toRequestBody("application/json+protobuf".toMediaType()))
            .header("x-goog-api-key", API_KEY)
            .header("x-user-agent", "grpc-web-javascript/0.1")
            .header("User-Agent", YtMusic.WEB_UA)
            .build()
        return Http.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("WAA $endpoint HTTP ${r.code}")
            r.body?.string().orEmpty()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun boot(): Session = withTimeout(25_000) {
        val visitor = YtMusic.visitorData ?: run {
            runCatching { YtMusic.suggestions("music") }
            YtMusic.visitorData
        } ?: throw IOException("no visitorData")
        val challenge = withContext(Dispatchers.IO) { waa("Create", JSONArray().put(REQUEST_KEY).toString()) }
        val bridge = Bridge()
        val loaded = CompletableDeferred<Unit>()
        val web = withContext(Dispatchers.Main) {
            WebView(appContext).apply {
                settings.javaScriptEnabled = true
                settings.userAgentString = YtMusic.WEB_UA
                addJavascriptInterface(bridge, "Bridge")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) { loaded.complete(Unit) }
                }
                loadDataWithBaseURL("https://www.youtube.com/", HTML, "text/html", "utf-8", null)
            }
        }
        loaded.await()
        withContext(Dispatchers.Main) { web.evaluateJavascript("runBotGuard(${JSONObject.quote(challenge)})", null) }
        val bgResponse = bridge.botguard.await()
        val it = withContext(Dispatchers.IO) { waa("GenerateIT", JSONArray().put(REQUEST_KEY).put(bgResponse).toString()) }
        val ttl = runCatching { JSONArray(it).optLong(1, 6 * 3600) }.getOrDefault(6 * 3600L)
        withContext(Dispatchers.Main) { web.evaluateJavascript("initMinter(${JSONObject.quote(it)})", null) }
        bridge.minter.await()
        val partial = Session(web, bridge, visitor, "", 0)
        val streaming = mintWith(partial, visitor)
        Log.i(TAG, "BotGuard ready (ttl ${ttl}s)")
        Session(web, bridge, visitor, streaming, System.currentTimeMillis() + (ttl - 600).coerceAtLeast(600) * 1000)
    }

    private suspend fun mintWith(s: Session, identifier: String): String {
        val id = ids.incrementAndGet()
        val d = CompletableDeferred<String>()
        s.bridge.tokens[id] = d
        withContext(Dispatchers.Main) { s.web.evaluateJavascript("mint($id, ${JSONObject.quote(identifier)})", null) }
        return withTimeout(8_000) { d.await() }
    }

    private suspend fun current(): Session = lock.withLock {
        session?.takeIf { System.currentTimeMillis() < it.expiresAt } ?: run {
            session?.let { old -> withContext(Dispatchers.Main) { old.web.destroy() } }
            boot().also { session = it }
        }
    }

    fun invalidate() { session = null }

    override fun getWebClientPoToken(videoId: String): PoTokenResult? = runCatching {
        runBlocking {
            val s = current()
            PoTokenResult(s.visitorData, mintWith(s, videoId), s.streamingPot)
        }
    }.onFailure { Log.w(TAG, "web poToken failed: ${it.message}"); invalidate() }.getOrNull()

    override fun getWebEmbedClientPoToken(videoId: String): PoTokenResult? = null
    override fun getAndroidClientPoToken(videoId: String): PoTokenResult? = null
    override fun getIosClientPoToken(videoId: String): PoTokenResult? = null

    private const val HTML = """<!DOCTYPE html><html><head><script>
let minter = null, wpso = null;
function b64ToU8(s) {
  s = s.replace(/-/g, '+').replace(/_/g, '/').replace(/\./g, '=');
  const b = atob(s); const u = new Uint8Array(b.length);
  for (let i = 0; i < b.length; i++) u[i] = b.charCodeAt(i);
  return u;
}
function u8ToB64url(u) {
  let s = ''; for (let i = 0; i < u.length; i++) s += String.fromCharCode(u[i]);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_');
}
function parseChallenge(raw) {
  let d = [];
  if (raw.length > 1 && typeof raw[1] === 'string') d = JSON.parse(new TextDecoder().decode(b64ToU8(raw[1]).map(x => x + 97)));
  else if (raw.length && typeof raw[0] === 'object') d = raw[0];
  const script = Array.isArray(d[1]) ? d[1].find(v => v && typeof v === 'string') : undefined;
  return { script: script, program: d[4], globalName: d[5] };
}
function withTimeout(p, ms, what) { return Promise.race([p, new Promise((_, j) => setTimeout(() => j(new Error(what + ' timed out')), ms))]); }
async function runBotGuard(rawJson) {
  try {
    const c = parseChallenge(JSON.parse(rawJson));
    if (!c.script) throw new Error('no interpreter');
    (0, eval)(c.script);
    const vm = window[c.globalName];
    if (!vm || !vm.a) throw new Error('VM unavailable');
    let snap = null; let ready;
    const isReady = new Promise(r => ready = r);
    const noop = () => {};
    vm.a(c.program, (asyncSnapshot) => { snap = asyncSnapshot; ready(); }, true, undefined, noop, [[], []], undefined, false, [noop, noop, noop, noop, noop]);
    await withTimeout(isReady, 6000, 'VM load');
    wpso = [];
    const resp = await withTimeout(new Promise(res => snap(r => res(r), [undefined, undefined, wpso, undefined])), 6000, 'snapshot');
    Bridge.onBotguard(resp);
  } catch (e) { Bridge.onError(String(e && e.message || e)); }
}
async function initMinter(itJson) {
  try {
    const it = JSON.parse(itJson);
    if (!wpso || !wpso[0]) throw new Error('no signal output');
    const m = await wpso[0](b64ToU8(it[0]));
    if (typeof m !== 'function') throw new Error('minter unavailable');
    minter = m; Bridge.onMinterReady();
  } catch (e) { Bridge.onError(String(e && e.message || e)); }
}
async function mint(id, identifier) {
  try {
    const r = await minter(new TextEncoder().encode(identifier));
    if (!r || !r.length) throw new Error('empty token');
    Bridge.onToken(id, u8ToB64url(r));
  } catch (e) { Bridge.onMintError(id, String(e && e.message || e)); }
}
</script></head><body></body></html>"""
}
