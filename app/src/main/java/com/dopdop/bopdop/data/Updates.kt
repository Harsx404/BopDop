package com.dopdop.bopdop.data

import com.dopdop.bopdop.BuildConfig
import com.dopdop.bopdop.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/** A newer build published on GitHub. */
data class AppUpdate(val version: String, val title: String, val notes: String, val apkUrl: String?, val pageUrl: String)

/**
 * Looks for a newer BopDop on the project's GitHub releases. The latest release's tag
 * ("v1.3.0-beta.1") is compared with the installed version; when it's newer, [available] holds it
 * and the app offers it on Home and in Settings. Checked once per launch, quietly: no network, no
 * nag.
 */
object Updates {
    private const val LATEST = "https://api.github.com/repos/Harsx404/BopDop/releases/latest"

    private val _available = MutableStateFlow<AppUpdate?>(null)
    val available: StateFlow<AppUpdate?> = _available.asStateFlow()
    private var checked = false

    /** Checks GitHub (once per launch unless [force]); returns the update, or null when up to date. */
    suspend fun check(force: Boolean = false): AppUpdate? {
        if (checked && !force) return _available.value
        val found = withContext(Dispatchers.IO) { runCatching { fetch() }.getOrNull() }
        checked = true
        _available.value = found?.takeIf { isNewer(it.version, BuildConfig.VERSION_NAME) }
        return _available.value
    }

    private fun fetch(): AppUpdate? {
        val req = Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").build()
        Http.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            val o = JSONObject(r.body?.string() ?: return null)
            val assets = o.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }?.optString("browser_download_url")
            return AppUpdate(
                version = o.optString("tag_name").removePrefix("v"),
                title = o.optString("name").ifBlank { o.optString("tag_name") },
                notes = o.optString("body"),
                apkUrl = apk?.ifBlank { null },
                pageUrl = o.optString("html_url"),
            )
        }
    }

    /**
     * Version order: numbers part by part ("1.10.0" > "1.9.2"); for the same numbers a release
     * beats its betas, and "beta.2" beats "beta.1".
     */
    internal fun isNewer(candidate: String, installed: String): Boolean = compare(candidate, installed) > 0

    private fun compare(a: String, b: String): Int {
        val (an, ap) = split(a); val (bn, bp) = split(b)
        for (i in 0 until maxOf(an.size, bn.size)) {
            val d = (an.getOrElse(i) { 0 }).compareTo(bn.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return when {
            ap == null && bp == null -> 0
            ap == null -> 1
            bp == null -> -1
            else -> comparePre(ap, bp)
        }
    }

    private fun split(v: String): Pair<List<Int>, String?> {
        val clean = v.trim().removePrefix("v")
        val core = clean.substringBefore('-')
        val pre = clean.substringAfter('-', "").ifBlank { null }
        return core.split('.').map { it.toIntOrNull() ?: 0 } to pre
    }

    private fun comparePre(a: String, b: String): Int {
        val pa = a.split('.'); val pb = b.split('.')
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrNull(i) ?: return -1
            val y = pb.getOrNull(i) ?: return 1
            val xi = x.toIntOrNull(); val yi = y.toIntOrNull()
            val d = if (xi != null && yi != null) xi.compareTo(yi) else x.compareTo(y)
            if (d != 0) return d
        }
        return 0
    }
}
