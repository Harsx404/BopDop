package com.dopdop.bopdop.data.share

import android.net.Uri
import android.util.Base64
import com.dopdop.bopdop.data.model.Card
import com.dopdop.bopdop.data.model.CardKind
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/** What a BopDop share link / QR code carries. */
sealed interface SharePayload {
    /** A user playlist: its name and track ids ("yt:…" / "sv:…"). [total] = size before trimming to fit a code. */
    data class Playlist(val name: String, val ids: List<String>, val total: Int = ids.size) : SharePayload
    /** A catalogue album / playlist / artist — the id is enough to open it. */
    data class Collection(val card: Card) : SharePayload
}

/**
 * Compact `bopdop://share/…` links. Playlists are packed as
 * `[v1][name][count]{[source][len][id]}…`, deflated and base64url'd, so a
 * code stays small; QR codes carry up to [QR_MAX_TRACKS] songs so they scan at a glance.
 */
object ShareCodec {
    const val SCHEME = "bopdop"
    const val HOST = "share"
    /** Most songs a QR keeps scannable once it carries a centre logo (error correction H). */
    const val QR_MAX_TRACKS = 30

    fun encode(p: SharePayload): String = when (p) {
        is SharePayload.Playlist -> "$SCHEME://$HOST/p/" + b64(deflate(pack(p)))
        is SharePayload.Collection -> Uri.Builder().scheme(SCHEME).authority(HOST)
            .appendPath("c").appendPath(p.card.kind.name.lowercase()).appendPath(p.card.id)
            .appendQueryParameter("t", p.card.title)
            .apply { if (p.card.subtitle.isNotBlank()) appendQueryParameter("s", p.card.subtitle.take(60)) }
            .build().toString()
    }

    /** The link for a QR code: long playlists are trimmed to their first [QR_MAX_TRACKS] songs. */
    fun encodeForQr(p: SharePayload): String =
        encode(if (p is SharePayload.Playlist && p.ids.size > QR_MAX_TRACKS) p.copy(ids = p.ids.take(QR_MAX_TRACKS)) else p)

    fun decode(text: String): SharePayload? = runCatching {
        val u = Uri.parse(text.trim())
        if (u.scheme != SCHEME || u.host != HOST) return null
        val seg = u.pathSegments
        when (seg.firstOrNull()) {
            "p" -> unpack(inflate(unb64(seg[1])))
            "c" -> {
                val kind = CardKind.valueOf(seg[1].uppercase())
                if (kind !in setOf(CardKind.ALBUM, CardKind.PLAYLIST, CardKind.ARTIST)) return null
                val id = seg[2]
                if (!id.startsWith("yt:") && !id.startsWith("sv:")) return null
                SharePayload.Collection(Card(id, kind, u.getQueryParameter("t").orEmpty().ifBlank { "Shared" }, u.getQueryParameter("s").orEmpty()))
            }
            else -> null
        }
    }.getOrNull()

    private fun pack(p: SharePayload.Playlist): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(1)
        val name = p.name.take(60).toByteArray(Charsets.UTF_8).take(180).toByteArray()
        out.write(name.size); out.write(name)
        out.write(p.total shr 8 and 0xff); out.write(p.total and 0xff)
        val ids = p.ids.filter { it.startsWith("yt:") || it.startsWith("sv:") }
        out.write(ids.size shr 8 and 0xff); out.write(ids.size and 0xff)
        ids.forEach { id ->
            val raw = id.substringAfter(':').toByteArray(Charsets.US_ASCII)
            out.write(if (id.startsWith("yt:")) 'y'.code else 's'.code)
            out.write(raw.size); out.write(raw)
        }
        return out.toByteArray()
    }

    private fun unpack(b: ByteArray): SharePayload.Playlist? {
        var i = 0
        fun u8() = b[i++].toInt() and 0xff
        fun u16() = (u8() shl 8) or u8()
        if (u8() != 1) return null
        val nl = u8()
        val name = String(b, i, nl, Charsets.UTF_8); i += nl
        val total = u16()
        val n = u16()
        val ids = List(n) {
            val src = u8().toChar()
            val len = u8()
            val raw = String(b, i, len, Charsets.US_ASCII); i += len
            (if (src == 'y') "yt:" else "sv:") + raw
        }
        return SharePayload.Playlist(name.ifBlank { "Shared playlist" }, ids, maxOf(total, ids.size))
    }

    private fun deflate(b: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION, true)
        d.setInput(b); d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    private fun inflate(b: ByteArray): ByteArray {
        val f = Inflater(true)
        f.setInput(b)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!f.finished()) {
            val n = f.inflate(buf)
            if (n == 0 && (f.needsInput() || f.needsDictionary())) break
            out.write(buf, 0, n)
            if (out.size() > 64 * 1024) break
        }
        f.end()
        return out.toByteArray()
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private fun unb64(s: String) = Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
