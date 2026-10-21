package com.dopdop.bopdop.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import com.dopdop.bopdop.R
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.model.formatDuration
import kotlin.math.min
import kotlin.random.Random

/**
 * The home-screen widgets, drawn as pictures so they can look like BopDop rather than stock
 * widgets: a record sliding out of its sleeve, the song's waveform as the progress bar, glass
 * buttons and the app's own type. Everything is laid out in dp; the matching tap areas are in
 * the widget_*_canvas layouts, so the numbers here and there must stay in step ([Np], [Up], [Quick]).
 */
internal object WidgetArt {
    const val ORANGE = 0xFFFF4D2D.toInt()
    private const val PEACH = 0xFFFF9466.toInt()

    /** Tap-zone geometry shared with the XML layouts, in dp. */
    object Np {
        const val PAD = 14f
        const val PLAY = 52f; const val SIDE = 40f; const val GAP = 6f
        const val LIKE = 40f; const val LIKE_MARGIN = 10f
        const val STRIP_PLAY = 46f; const val STRIP_NEXT = 38f; const val STRIP_END = 10f; const val STRIP_GAP = 4f
        const val SQUARE_PLAY = 46f; const val SQUARE_MARGIN = 12f
    }
    object Up { const val HEADER = 86f; const val ROW = 54f; const val PAD = 10f; const val PLAY = 42f; const val MARGIN = 14f }
    object Quick { const val PAD = 10f; const val GAP = 4f }

    data class Size(val wDp: Float, val hDp: Float, val scale: Float) {
        val w get() = (wDp * scale).toInt().coerceAtLeast(1)
        val h get() = (hDp * scale).toInt().coerceAtLeast(1)
    }

    enum class NpShape { STRIP, SQUARE, FULL }

    fun npShape(wDp: Float, hDp: Float) = when {
        hDp < 110f -> NpShape.STRIP
        wDp < 210f -> NpShape.SQUARE
        else -> NpShape.FULL
    }

    // --- type ------------------------------------------------------------------------------

    private var family: Typeface? = null
    private fun face(ctx: Context, weight: Int): Typeface {
        val base = family ?: (runCatching { ResourcesCompat.getFont(ctx, R.font.jakarta) }.getOrNull() ?: Typeface.DEFAULT).also { family = it }
        return if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight, false) else Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun text(ctx: Context, s: Size, sizeDp: Float, weight: Int, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face(ctx, weight); textSize = sizeDp * s.scale; this.color = color
    }

    private fun Canvas.line(str: String, p: TextPaint, x: Float, baseline: Float, maxW: Float) {
        drawText(TextUtils.ellipsize(str, p, maxW, TextUtils.TruncateAt.END).toString(), x, baseline, p)
    }

    // --- shared pieces ---------------------------------------------------------------------

    /** Rounded charcoal glass card: a soft orange glow in the corner and a sheen across the top. */
    @Suppress("UNUSED_PARAMETER")
    private fun card(c: Canvas, s: Size, cover: Bitmap?, radiusDp: Float = 26f) {
        val w = s.w.toFloat(); val h = s.h.toFloat(); val r = radiusDp * s.scale
        val shape = Path().apply { addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW) }
        c.save(); c.clipPath(shape)
        // Charcoal, a touch lighter at the top like lit glass.
        c.drawRect(0f, 0f, w, h, Paint().apply { shader = LinearGradient(0f, 0f, w * 0.4f, h, 0xFF2A2A2F.toInt(), 0xFF111113.toInt(), Shader.TileMode.CLAMP) })
        // The brand glow, warm in the bottom-right corner.
        c.drawRect(0f, 0f, w, h, Paint().apply {
            shader = RadialGradient(w * 0.98f, h * 1.08f, maxOf(w, h) * 0.8f, (ORANGE and 0x00FFFFFF) or 0x66000000, ORANGE and 0x00FFFFFF, Shader.TileMode.CLAMP)
        })
        c.drawPath(Path().apply {
            moveTo(-w * 0.1f, h * 0.22f); cubicTo(w * 0.3f, h * 0.02f, w * 0.65f, h * 0.42f, w * 1.1f, h * 0.18f); lineTo(w * 1.1f, -1f); lineTo(-w * 0.1f, -1f); close()
        }, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x14FFFFFF })
        c.restore()
        c.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s.scale; color = 0x26FFFFFF })
    }

    private fun roundRect(c: Canvas, r: RectF, radius: Float, bmp: Bitmap?, fallback: Int = 0xFF2A2730.toInt()) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (bmp != null) {
            p.shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setRectToRect(RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat()), r, Matrix.ScaleToFit.FILL) })
            }
        } else p.color = fallback
        c.drawRoundRect(r, radius, radius, p)
    }

    /** A record: grooves, a moving glint and the cover as its centre label. [turn] is in degrees. */
    private fun vinyl(c: Canvas, cx: Float, cy: Float, r: Float, cover: Bitmap?, turn: Float) {
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0C0C0E.toInt() })
        val groove = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = r * 0.012f; color = 0x1FFFFFFF }
        var gr = r * 0.95f
        while (gr > r * 0.42f) { c.drawCircle(cx, cy, gr, groove); gr -= r * 0.055f }
        // Two soft glints that turn with the music.
        c.save(); c.rotate(turn, cx, cy)
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = SweepGradient(cx, cy, intArrayOf(0x00FFFFFF, 0x33FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x26FFFFFF, 0x00FFFFFF, 0x00FFFFFF),
                floatArrayOf(0f, 0.08f, 0.16f, 0.5f, 0.58f, 0.66f, 1f))
        })
        val label = r * 0.4f
        c.save(); c.clipPath(Path().apply { addCircle(cx, cy, label, Path.Direction.CW) })
        if (cover != null) c.drawBitmap(cover, null, RectF(cx - label, cy - label, cx + label, cy + label), Paint(Paint.FILTER_BITMAP_FLAG))
        else c.drawColor(ORANGE)
        c.restore(); c.restore()
        c.drawCircle(cx, cy, label, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = r * 0.02f; color = 0x55000000 })
        c.drawCircle(cx, cy, r * 0.045f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0C0C0E.toInt() })
        // Rim light, so the black record reads against a dark card.
        c.drawCircle(cx, cy, r * 0.985f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = r * 0.03f
            shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, 0x66FFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP)
        })
    }

    /** The cover as a sleeve with the record slid half out to its right. Returns the sleeve's right edge. */
    private fun sleeve(c: Canvas, s: Size, left: Float, top: Float, side: Float, cover: Bitmap?, turn: Float): Float {
        val r = side * 0.46f
        // Slid well out of the sleeve, so the record (and its spinning glint) shows.
        val cx = left + side * 0.62f + r * 0.75f
        vinyl(c, cx, top + side / 2, r, cover, turn)
        val rect = RectF(left, top, left + side, top + side)
        c.drawRoundRect(RectF(rect).apply { offset(0f, 3 * s.scale) }, 12 * s.scale, 12 * s.scale,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000; maskFilter = BlurMaskFilter(8 * s.scale, BlurMaskFilter.Blur.NORMAL) })
        roundRect(c, rect, 12 * s.scale, cover)
        c.drawRoundRect(rect, 12 * s.scale, 12 * s.scale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(rect.left, rect.top, rect.left, rect.bottom, 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        })
        return cx + r
    }

    /** The song's "waveform" (bars seeded by the song, so each song has its own shape) filled up to [frac]. */
    private fun waveform(c: Canvas, s: Size, rect: RectF, seed: String, frac: Float) {
        val rnd = Random(seed.hashCode())
        val bar = 3f * s.scale; val gap = 2.4f * s.scale
        val n = ((rect.width() + gap) / (bar + gap)).toInt().coerceAtLeast(8)
        var prev = 0.5f
        val played = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(rect.left, 0f, rect.right, 0f, PEACH, ORANGE, Shader.TileMode.CLAMP) }
        val rest = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFFFFF }
        for (i in 0 until n) {
            prev = (prev * 0.55f + rnd.nextFloat() * 0.45f).coerceIn(0.15f, 1f)
            val bh = rect.height() * (0.25f + 0.75f * prev)
            val x = rect.left + i * (bar + gap)
            val r = RectF(x, rect.centerY() - bh / 2, x + bar, rect.centerY() + bh / 2)
            c.drawRoundRect(r, bar / 2, bar / 2, if ((i + 0.5f) / n <= frac) played else rest)
        }
    }

    private fun glassButton(c: Canvas, cx: Float, cy: Float, r: Float, solid: Boolean) {
        if (solid) {
            c.drawCircle(cx, cy + r * 0.1f, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000; maskFilter = BlurMaskFilter(r * 0.35f, BlurMaskFilter.Blur.NORMAL) })
            c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        } else {
            c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF })
            c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = r * 0.05f; color = 0x33FFFFFF })
        }
    }

    private fun playGlyph(c: Canvas, cx: Float, cy: Float, size: Float, playing: Boolean, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        if (playing) {
            val bw = size * 0.26f; val bh = size * 0.8f; val r = bw * 0.35f
            c.drawRoundRect(RectF(cx - size * 0.32f, cy - bh / 2, cx - size * 0.32f + bw, cy + bh / 2), r, r, p)
            c.drawRoundRect(RectF(cx + size * 0.32f - bw, cy - bh / 2, cx + size * 0.32f, cy + bh / 2), r, r, p)
        } else {
            val path = Path().apply { moveTo(cx - size * 0.28f, cy - size * 0.42f); lineTo(cx + size * 0.44f, cy); lineTo(cx - size * 0.28f, cy + size * 0.42f); close() }
            p.pathEffect = android.graphics.CornerPathEffect(size * 0.12f)
            c.drawPath(path, p)
        }
    }

    private fun skipGlyph(c: Canvas, cx: Float, cy: Float, size: Float, forward: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; pathEffect = android.graphics.CornerPathEffect(size * 0.1f) }
        val d = if (forward) 1f else -1f
        val path = Path().apply { moveTo(cx - d * size * 0.38f, cy - size * 0.36f); lineTo(cx + d * size * 0.2f, cy); lineTo(cx - d * size * 0.38f, cy + size * 0.36f); close() }
        c.drawPath(path, p)
        val bx = cx + d * size * 0.3f; val bw = size * 0.14f
        c.drawRoundRect(RectF(minOf(bx, bx + d * bw), cy - size * 0.36f, maxOf(bx, bx + d * bw), cy + size * 0.36f), bw / 2, bw / 2, p)
    }

    private fun heart(c: Canvas, cx: Float, cy: Float, size: Float, filled: Boolean) {
        val s = size / 2
        val path = Path().apply {
            moveTo(cx, cy + s * 0.85f)
            cubicTo(cx - s * 1.3f, cy - s * 0.05f, cx - s * 0.75f, cy - s * 1.1f, cx, cy - s * 0.42f)
            cubicTo(cx + s * 0.75f, cy - s * 1.1f, cx + s * 1.3f, cy - s * 0.05f, cx, cy + s * 0.85f)
            close()
        }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (filled) color = ORANGE else { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = size * 0.11f; strokeJoin = Paint.Join.ROUND }
        })
    }

    /** The play-b mark, small, for labels. */
    private fun mark(c: Canvas, x: Float, y: Float, h: Float, color: Int) {
        val k = h / 248f
        val glyph = Path().apply {
            moveTo(60f, 16f); lineTo(84f, 16f); cubicTo(97.255f, 16f, 108f, 26.745f, 108f, 40f); lineTo(108f, 78.13f)
            cubicTo(114.446f, 76.734f, 121.138f, 76f, 128f, 76f); cubicTo(179.915f, 76f, 222f, 118.085f, 222f, 170f)
            cubicTo(222f, 221.915f, 179.915f, 264f, 128f, 264f); cubicTo(76.085f, 264f, 34f, 221.915f, 34f, 170f); lineTo(34f, 42f)
            cubicTo(34f, 27.641f, 45.641f, 16f, 60f, 16f); close()
            moveTo(109f, 126f); lineTo(172f, 162f); quadTo(184f, 169f, 172f, 176f); lineTo(109f, 212f); quadTo(100f, 217f, 100f, 206f)
            lineTo(100f, 132f); quadTo(100f, 121f, 109f, 126f); close()
            fillType = Path.FillType.EVEN_ODD
            transform(Matrix().apply { setTranslate(-34f, -16f); postScale(k, k); postTranslate(x, y) })
        }
        c.drawPath(glyph, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    // --- Now Playing -----------------------------------------------------------------------

    class NowPlaying(val track: Track?, val playing: Boolean, val posMs: Long, val durMs: Long, val liked: Boolean, val cover: Bitmap?)

    /** Covers must be software bitmaps to be drawn here; hardware ones are copied. */
    fun soft(b: Bitmap?): Bitmap? = if (b != null && Build.VERSION.SDK_INT >= 26 && b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b

    fun nowPlaying(ctx: Context, s: Size, np: NowPlaying): Bitmap {
        val out = Bitmap.createBitmap(s.w, s.h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val k = s.scale; val w = s.w.toFloat(); val h = s.h.toFloat()
        val t = np.track
        val frac = if (np.durMs > 0) (np.posMs.toFloat() / np.durMs).coerceIn(0f, 1f) else 0f
        val turn = (np.posMs / 1000f) * 200f % 360f
        val title = t?.title ?: "Nothing playing"
        val artist = t?.artist ?: "Tap to open BopDop"
        when (npShape(s.wDp, s.hDp)) {
            NpShape.STRIP -> {
                card(c, s, np.cover)
                val pad = 10 * k
                val side = h - 2 * pad
                val right = sleeve(c, s, pad, pad, side, np.cover, turn)
                val ctrlLeft = w - (Np.STRIP_END + Np.STRIP_NEXT + Np.STRIP_GAP + Np.STRIP_PLAY) * k
                val tx = right + 10 * k; val maxW = ctrlLeft - tx - 8 * k
                c.line(title, text(ctx, s, 15f, 750, Color.WHITE), tx, h * 0.42f, maxW)
                c.line(artist, text(ctx, s, 12f, 500, 0xB3FFFFFF.toInt()), tx, h * 0.42f + 17 * k, maxW)
                waveform(c, s, RectF(tx, h * 0.42f + 25 * k, tx + maxW, h * 0.42f + 35 * k), t?.id ?: "idle", frac)
                val playCx = ctrlLeft + Np.STRIP_PLAY / 2 * k
                glassButton(c, playCx, h / 2, Np.STRIP_PLAY / 2 * k, solid = true)
                playGlyph(c, playCx + if (np.playing) 0f else 1.5f * k, h / 2, 17 * k, np.playing, 0xFF141214.toInt())
                skipGlyph(c, w - (Np.STRIP_END + Np.STRIP_NEXT / 2) * k, h / 2, 16 * k, forward = true)
            }
            NpShape.SQUARE -> {
                val r = 26 * k
                c.save(); c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW) })
                if (np.cover != null) {
                    val side = maxOf(w, h)
                    c.drawBitmap(np.cover, null, RectF((w - side) / 2, (h - side) / 2, (w + side) / 2, (h + side) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
                } else card(c, s, null)
                c.drawRect(0f, 0f, w, h, Paint().apply { shader = LinearGradient(0f, h * 0.35f, 0f, h, 0x00000000, 0xD9000000.toInt(), Shader.TileMode.CLAMP) })
                // A sliver of record peeking from the top-right corner.
                vinyl(c, w + 6 * k, -6 * k, h * 0.32f, np.cover, turn)
                c.restore()
                c.drawRoundRect(RectF(0f, 0f, w, h), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = k; color = 0x26FFFFFF })
                val pr = Np.SQUARE_PLAY / 2 * k
                val pcx = w - (Np.SQUARE_MARGIN * k) - pr; val pcy = h - (Np.SQUARE_MARGIN * k) - pr
                val maxW = pcx - pr - 22 * k
                c.line(title, text(ctx, s, 15f, 750, Color.WHITE), 14 * k, pcy - 2 * k, maxW)
                c.line(artist, text(ctx, s, 11.5f, 500, 0xB3FFFFFF.toInt()), 14 * k, pcy + 14 * k, maxW)
                glassButton(c, pcx, pcy, pr, solid = true)
                playGlyph(c, pcx + if (np.playing) 0f else 1.5f * k, pcy, 17 * k, np.playing, 0xFF141214.toInt())
                c.drawRoundRect(RectF(14 * k, h - 6 * k, (14 * k) + (w - 28 * k) * frac, h - 4 * k), k, k, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ORANGE })
            }
            NpShape.FULL -> {
                card(c, s, np.cover)
                val pad = Np.PAD * k
                val side = min(h - 2 * pad, w * 0.31f)
                val right = sleeve(c, s, pad, (h - side) / 2, side, np.cover, turn)
                val tx = right + 12 * k
                val maxW = w - tx - pad
                // "NOW PLAYING" with the mark, then the song.
                mark(c, tx, pad + 1 * k, 10 * k, ORANGE)
                c.line(if (np.playing) "NOW PLAYING" else "PAUSED", text(ctx, s, 9.5f, 700, 0x99FFFFFF.toInt()).apply { letterSpacing = 0.12f }, tx + 12 * k, pad + 10 * k,
                    maxW - (Np.LIKE + Np.LIKE_MARGIN) * k)
                val titleP = text(ctx, s, 18f, 800, Color.WHITE)
                c.line(title, titleP, tx, pad + 34 * k, maxW - 4 * k)
                c.line(artist, text(ctx, s, 13f, 500, 0xBFFFFFFF.toInt()), tx, pad + 52 * k, maxW)
                val ctrlTop = h - (12 + Np.PLAY) * k
                val waveTop = pad + 62 * k
                val waveBottom = minOf(ctrlTop - 22 * k, waveTop + 26 * k)
                if (waveBottom - waveTop > 10 * k) {
                    waveform(c, s, RectF(tx, waveTop, w - pad, waveBottom), t?.id ?: "idle", frac)
                    val times = text(ctx, s, 10f, 600, 0x99FFFFFF.toInt())
                    c.drawText(formatDuration((np.posMs / 1000).toInt()).ifEmpty { "0:00" }, tx, waveBottom + 13 * k, times)
                    val total = formatDuration((np.durMs / 1000).toInt()).ifEmpty { "–:––" }
                    c.drawText(total, w - pad - times.measureText(total), waveBottom + 13 * k, times)
                }
                val cy = h - (12 + Np.PLAY / 2) * k
                val nextCx = w - (Np.PAD + Np.SIDE / 2) * k
                val playCx = w - (Np.PAD + Np.SIDE + Np.GAP + Np.PLAY / 2) * k
                val prevCx = w - (Np.PAD + Np.SIDE + Np.GAP + Np.PLAY + Np.GAP + Np.SIDE / 2) * k
                glassButton(c, prevCx, cy, Np.SIDE / 2 * k, solid = false); skipGlyph(c, prevCx, cy, 15 * k, forward = false)
                glassButton(c, playCx, cy, Np.PLAY / 2 * k, solid = true)
                playGlyph(c, playCx + if (np.playing) 0f else 1.5f * k, cy, 19 * k, np.playing, 0xFF141214.toInt())
                glassButton(c, nextCx, cy, Np.SIDE / 2 * k, solid = false); skipGlyph(c, nextCx, cy, 15 * k, forward = true)
                val lcx = w - (Np.LIKE_MARGIN + Np.LIKE / 2) * k; val lcy = (Np.LIKE_MARGIN + Np.LIKE / 2) * k
                heart(c, lcx, lcy, 18 * k, np.liked)
            }
        }
        return out
    }

    // --- Up next ---------------------------------------------------------------------------

    fun upNext(ctx: Context, s: Size, np: NowPlaying, next: List<Pair<Track, Bitmap?>>): Bitmap {
        val out = Bitmap.createBitmap(s.w, s.h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val k = s.scale; val w = s.w.toFloat(); val h = s.h.toFloat()
        card(c, s, np.cover)
        val turn = (np.posMs / 1000f) * 200f % 360f
        val pad = Up.MARGIN * k
        val disc = 56 * k
        vinyl(c, pad + disc / 2, pad + disc / 2, disc / 2, np.cover, turn)
        val tx = pad + disc + 12 * k
        val pr = Up.PLAY / 2 * k
        val pcx = w - pad - pr; val pcy = pad + pr
        val maxW = pcx - pr - tx - 8 * k
        c.line("UP NEXT", text(ctx, s, 9.5f, 700, ORANGE).apply { letterSpacing = 0.14f }, tx, pad + 12 * k, maxW)
        c.line(np.track?.title ?: "Nothing playing", text(ctx, s, 16f, 800, Color.WHITE), tx, pad + 32 * k, maxW)
        c.line(np.track?.artist ?: "Play something to fill your queue", text(ctx, s, 12f, 500, 0xB3FFFFFF.toInt()), tx, pad + 49 * k, maxW)
        glassButton(c, pcx, pcy, pr, solid = true)
        playGlyph(c, pcx + if (np.playing) 0f else 1.5f * k, pcy, 15 * k, np.playing, 0xFF141214.toInt())
        // Rows: big outlined number, cover, title and artist.
        val rows = ((h - Up.HEADER * k - 8 * k) / (Up.ROW * k)).toInt().coerceIn(0, 4)
        val numP = text(ctx, s, 22f, 800, ORANGE).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f * k }
        val tP = text(ctx, s, 14f, 700, Color.WHITE); val aP = text(ctx, s, 11.5f, 500, 0xA6FFFFFF.toInt())
        for (i in 0 until rows) {
            val top = Up.HEADER * k + i * Up.ROW * k
            val item = next.getOrNull(i) ?: break
            val (song, art) = item
            if (i > 0) c.drawRect(Up.PAD * k + 40 * k, top, w - Up.PAD * k, top + k * 0.7f, Paint().apply { color = 0x1AFFFFFF })
            val mid = top + Up.ROW * k / 2
            c.drawText("%02d".format(i + 1), Up.PAD * k + 4 * k, mid + 8 * k, numP)
            val ar = RectF(Up.PAD * k + 42 * k, mid - 20 * k, Up.PAD * k + 82 * k, mid + 20 * k)
            roundRect(c, ar, 9 * k, art)
            val x = ar.right + 10 * k; val mw = w - x - Up.PAD * k - 4 * k
            c.line(song.title, tP, x, mid - 1 * k, mw)
            c.line(song.artist, aP, x, mid + 15 * k, mw)
        }
        if (rows > 0 && next.isEmpty()) c.line("Autoplay picks up when your queue ends", aP, Up.PAD * k + 4 * k, Up.HEADER * k + 24 * k, w - 2 * Up.PAD * k)
        return out
    }

    // --- Quick mixes ---------------------------------------------------------------------------

    class Tile(val label: String, val sub: String, val from: Int, val to: Int, val icon: Int)

    fun quick(ctx: Context, s: Size, tiles: List<Tile>): Bitmap {
        val out = Bitmap.createBitmap(s.w, s.h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val k = s.scale; val w = s.w.toFloat(); val h = s.h.toFloat()
        card(c, s, null)
        val pad = Quick.PAD * k
        val slot = (w - 2 * pad) / tiles.size
        tiles.forEachIndexed { i, tile ->
            val r = RectF(pad + i * slot + Quick.GAP * k, pad, pad + (i + 1) * slot - Quick.GAP * k, h - pad)
            val rad = 18 * k
            c.drawRoundRect(r, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(r.left, r.top, r.right, r.bottom, tile.from, tile.to, Shader.TileMode.CLAMP) })
            c.drawRoundRect(r, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, r.top, 0f, r.top + r.height() * 0.55f, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            })
            c.drawRoundRect(r, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = k; color = 0x40FFFFFF })
            val compact = r.height() < 80 * k
            val iconSize = (if (compact) 20 else 24) * k
            val icx = r.left + 12 * k + iconSize / 2
            val icy = if (compact) r.centerY() - 8 * k else r.top + 12 * k + iconSize / 2
            ResourcesCompat.getDrawable(ctx.resources, tile.icon, null)?.mutate()?.let { d ->
                d.setTint(Color.WHITE)
                d.setBounds((icx - iconSize / 2).toInt(), (icy - iconSize / 2).toInt(), (icx + iconSize / 2).toInt(), (icy + iconSize / 2).toInt())
                d.draw(c)
            }
            val labelP = text(ctx, s, 13f, 800, Color.WHITE)
            c.line(tile.label, labelP, r.left + 12 * k, r.bottom - (if (compact) 9 else 22) * k, r.width() - 20 * k)
            if (!compact) c.line(tile.sub, text(ctx, s, 10f, 600, 0xCCFFFFFF.toInt()), r.left + 12 * k, r.bottom - 9 * k, r.width() - 20 * k)
        }
        return out
    }

    /** How big a widget is, from the launcher, scaled to keep the picture light. */
    fun sizeOf(ctx: Context, opts: android.os.Bundle?, defaultW: Float, defaultH: Float): Size {
        val portrait = ctx.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val minW = opts?.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val maxW = opts?.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) ?: 0
        val minH = opts?.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
        val maxH = opts?.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        val w = (if (portrait) minW else maxW).takeIf { it > 0 }?.toFloat() ?: defaultW
        val h = (if (portrait) maxH else minH).takeIf { it > 0 }?.toFloat() ?: defaultH
        return Size(w, h, min(ctx.resources.displayMetrics.density, 2.2f))
    }

}
