package com.dopdop.bopdop.data.account

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

/** The signed-in YouTube Music account, as shown in the app. */
data class YtAccount(val name: String, val handle: String, val photo: String)

/**
 * YouTube Music sign-in. The user signs in to Google in a WebView; we keep the
 * music.youtube.com cookies (app-private storage only) and sign InnerTube
 * requests with them the same way the web player does (SAPISIDHASH).
 */
object AccountStore {
    const val ORIGIN = "https://music.youtube.com"

    private lateinit var prefs: SharedPreferences
    @Volatile private var cookie: String? = null
    private val _account = MutableStateFlow<YtAccount?>(null)
    val account: StateFlow<YtAccount?> = _account.asStateFlow()

    val signedIn: Boolean get() = cookie != null

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)
        cookie = prefs.getString("cookie", null)?.takeIf { sapisid(it) != null }
        if (cookie != null) _account.value = YtAccount(
            prefs.getString("name", "").orEmpty().ifEmpty { "YouTube Music" },
            prefs.getString("handle", "").orEmpty(),
            prefs.getString("photo", "").orEmpty(),
        )
    }

    /** True when the cookie jar carries a usable session. */
    fun signIn(cookies: String): Boolean {
        if (sapisid(cookies) == null) return false
        cookie = cookies
        prefs.edit().putString("cookie", cookies).apply()
        if (_account.value == null) _account.value = YtAccount("YouTube Music", "", "")
        return true
    }

    fun setProfile(a: YtAccount) {
        if (cookie == null) return
        _account.value = a
        prefs.edit().putString("name", a.name).putString("handle", a.handle).putString("photo", a.photo).apply()
    }

    fun signOut() {
        cookie = null
        _account.value = null
        prefs.edit().clear().apply()
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    /** Headers that make an InnerTube request act as the signed-in user; empty when signed out. */
    fun authHeaders(): Map<String, String> {
        val c = cookie ?: return emptyMap()
        val sid = sapisid(c) ?: return emptyMap()
        val ts = System.currentTimeMillis() / 1000
        val hash = MessageDigest.getInstance("SHA-1").digest("$ts $sid $ORIGIN".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return mapOf(
            "Cookie" to c,
            "Authorization" to "SAPISIDHASH ${ts}_$hash",
            "X-Goog-AuthUser" to "0",
            "X-Origin" to ORIGIN,
        )
    }

    private fun sapisid(cookies: String): String? = cookies.split(';').map { it.trim() }.let { parts ->
        fun value(name: String) = parts.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')?.takeIf { it.isNotEmpty() }
        value("SAPISID") ?: value("__Secure-3PAPISID")
    }
}
