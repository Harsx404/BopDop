package com.dopdop.bopdop.data.spotify

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SpotifyProfile(val name: String, val avatar: String)

/**
 * The user's own Spotify web session: the `sp_dc` cookie open.spotify.com sets after signing in
 * on Spotify's real login page in a WebView. Kept on this phone only (never in backups); everything
 * Spotify is read with tokens the web player mints from it (see [SpotifyToken]).
 */
object SpotifyAccount {
    const val ORIGIN = "https://open.spotify.com"
    private lateinit var prefs: SharedPreferences
    private val _profile = MutableStateFlow<SpotifyProfile?>(null)
    val profile: StateFlow<SpotifyProfile?> = _profile.asStateFlow()

    var cookie: String? = null
        private set
    val signedIn get() = cookie != null

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("spotify", Context.MODE_PRIVATE)
        cookie = prefs.getString("sp_dc", null)?.takeIf { it.isNotBlank() }
        if (cookie != null) _profile.value = SpotifyProfile(prefs.getString("name", "").orEmpty().ifBlank { "Spotify" }, prefs.getString("avatar", "").orEmpty())
    }

    fun signIn(spDc: String) {
        cookie = spDc
        prefs.edit().putString("sp_dc", spDc).apply()
        if (_profile.value == null) _profile.value = SpotifyProfile("Spotify", "")
        SpotifyToken.invalidate()
    }

    fun setProfile(p: SpotifyProfile) {
        _profile.value = p
        prefs.edit().putString("name", p.name).putString("avatar", p.avatar).apply()
    }

    /**
     * Forget the session, and sign the WebView out too: its cookie jar would otherwise sign the
     * next "Connect" straight back in. Only Spotify's cookies go; the YouTube Music ones stay.
     */
    fun signOut() {
        cookie = null
        _profile.value = null
        prefs.edit().clear().apply()
        SpotifyToken.invalidate()
        SpotifyMatch.clear()
        val jar = CookieManager.getInstance()
        listOf(ORIGIN, "https://accounts.spotify.com", "https://www.spotify.com", "https://spotify.com").forEach { url ->
            jar.getCookie(url)?.split(";")?.map { it.substringBefore("=").trim() }?.filter { it.isNotEmpty() }?.forEach { name ->
                val gone = "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"
                jar.setCookie(url, gone)
                jar.setCookie(url, "$gone; Domain=.spotify.com")
            }
        }
        jar.flush()
    }
}
