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

    /** Pill: one line, short; card: the cover fills it. */
    enum class NpShape { PILL, CARD }

    fun npShape(wDp: Float, hDp: Float) = if (hDp < 110f) NpShape.PILL else NpShape.CARD

    object Pill { const val PAD = 10f; const val ART = 56f; const val TEXT_GAP = 12f; const val END = 12f; const val TITLE = 24f; const val ROW = 36f }
    object Card { const val BTN = 40f; const val MARGIN = 8f }

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

    class NowPlaying(
        val track: Track?, val playing: Boolean, val posMs: Long, val durMs: Long, val liked: Boolean, val cover: Bitmap?,
        val shuffle: Boolean = false,
    )

    /** Covers must be software bitmaps to be drawn here; hardware ones are copied. */
    fun soft(b: Bitmap?): Bitmap? = if (b != null && Build.VERSION.SDK_INT >= 26 && b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b

    /** The cover's own deep colour (as album-art widgets do), or BopDop's ember when there's no cover. */
    private fun tint(cover: Bitmap?): Int {
        val p = cover?.let { androidx.palette.graphics.Palette.from(it).maximumColorCount(16).generate() }
        val c = p?.let { it.getDarkVibrantColor(it.getVibrantColor(it.getDominantColor(0xFF7A1E12.toInt()))) } ?: 0xFF7A1E12.toInt()
        // Deep enough for white type, never black.
        val hsv = FloatArray(3).also { Color.colorToHSV(c, it) }
        hsv[2] = hsv[2].coerceIn(0.32f, 0.55f); hsv[1] = hsv[1].coerceAtMost(0.8f)
        return Color.HSVToColor(hsv)
    }

    private fun mix(a: Int, b: Int, f: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt(),
    )

    private fun shuffleGlyph(c: Canvas, cx: Float, cy: Float, size: Float, on: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (on) Color.WHITE else 0x99FFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = size * 0.11f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val l = cx - size * 0.45f; val r = cx + size * 0.45f; val t = cy - size * 0.3f; val b = cy + size * 0.3f
        c.drawPath(Path().apply { moveTo(l, t); cubicTo(cx, t, cx, b, r, b) }, p)
        c.drawPath(Path().apply { moveTo(l, b); cubicTo(cx, b, cx, t, r, t) }, p)
        val hd = size * 0.16f
        c.drawPath(Path().apply { moveTo(r - hd, t - hd); lineTo(r, t); lineTo(r - hd, t + hd) }, p)
        c.drawPath(Path().apply { moveTo(r - hd, b - hd); lineTo(r, b); lineTo(r - hd, b + hd) }, p)
        if (on) c.drawCircle(cx, cy + size * 0.62f, size * 0.07f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    }

    fun nowPlaying(ctx: Context, s: Size, np: NowPlaying): Bitmap {
        val out = Bitmap.createBitmap(s.w, s.h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val k = s.scale; val w = s.w.toFloat(); val h = s.h.toFloat()
        val t = np.track
        val frac = if (np.durMs > 0) (np.posMs.toFloat() / np.durMs).coerceIn(0f, 1f) else 0f
        when (npShape(s.wDp, s.hDp)) {
            NpShape.PILL -> {
                // A capsule in the cover's colour: round cover, "Title - Artist", a row of plain controls.
                val base = tint(np.cover)
                val r = h / 2
                val shape = Path().apply { addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW) }
                c.save(); c.clipPath(shape)
                c.drawRect(0f, 0f, w, h, Paint().apply { shader = LinearGradient(0f, 0f, w, h, mix(base, Color.WHITE, 0.08f), mix(base, Color.BLACK, 0.35f), Shader.TileMode.CLAMP) })
                c.drawRect(0f, 0f, w, h * 0.5f, Paint().apply { shader = LinearGradient(0f, 0f, 0f, h * 0.5f, 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP) })
                // Progress as a soft fill along the bottom edge.
                c.drawRect(0f, h - 3 * k, w * frac, h, Paint().apply { color = 0x66FFFFFF })
                c.restore()
                c.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = k; color = 0x2EFFFFFF })
                val art = Pill.ART * k
                val ax = Pill.PAD * k; val ay = (h - art) / 2
                c.save(); c.clipPath(Path().apply { addCircle(ax + art / 2, ay + art / 2, art / 2, Path.Direction.CW) })
                if (np.cover != null) c.drawBitmap(np.cover, null, RectF(ax, ay, ax + art, ay + art), Paint(Paint.FILTER_BITMAP_FLAG))
                else { c.drawColor(0xFF1C1A1D.toInt()); mark(c, ax + art * 0.28f, ay + art * 0.22f, art * 0.56f, ORANGE) }
                c.restore()
                val tx = (Pill.PAD + Pill.ART + Pill.TEXT_GAP) * k
                val right = w - Pill.END * k
                val group = (Pill.TITLE + Pill.ROW) * k
                val top = (h - group) / 2
                val line = if (t == null) "Nothing playing - tap to open" else "${t.title} - ${t.artist}"
                c.line(line, text(ctx, s, 14.5f, 700, Color.WHITE), tx, top + 17 * k, right - tx)
                // Five equal slots: like, previous, play, next, shuffle.
                val slot = (right - tx) / 5
                val cy = top + Pill.TITLE * k + Pill.ROW * k / 2
                fun cx(i: Int) = tx + slot * (i + 0.5f)
                heart(c, cx(0), cy, 17 * k, np.liked)
                skipGlyph(c, cx(1), cy, 15 * k, forward = false)
                playGlyph(c, cx(2) + if (np.playing) 0f else 1.5f * k, cy, 19 * k, np.playing, Color.WHITE)
                skipGlyph(c, cx(3), cy, 15 * k, forward = true)
                shuffleGlyph(c, cx(4), cy, 18 * k, np.shuffle)
            }
            NpShape.CARD -> {
                // The cover fills the card; a scrim at the foot carries the song and the controls.
                val r = 26 * k
                val shape = Path().apply { addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW) }
                c.save(); c.clipPath(shape)
                if (np.cover != null) {
                    val side = maxOf(w, h)
                    c.drawBitmap(np.cover, null, RectF((w - side) / 2, (h - side) / 2, (w + side) / 2, (h + side) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
                } else {
                    card(c, s, null)
                    mark(c, w / 2 - h * 0.14f, h * 0.18f, h * 0.38f, ORANGE)
                }
                val tintC = tint(np.cover)
                c.drawRect(0f, h * 0.35f, w, h, Paint().apply {
                    shader = LinearGradient(0f, h * 0.35f, 0f, h, Color.argb(0, Color.red(tintC), Color.green(tintC), Color.blue(tintC)),
                        mix(tintC, Color.BLACK, 0.45f), Shader.TileMode.CLAMP)
                })
                c.drawRect(0f, h - 3 * k, w * frac, h, Paint().apply { color = 0x99FFFFFF.toInt() })
                c.restore()
                c.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = k; color = 0x2EFFFFFF })
                val btn = Card.BTN * k; val m = Card.MARGIN * k
                val cy = h - m - btn / 2
                val nextCx = w - m - btn / 2; val playCx = nextCx - btn; val prevCx = playCx - btn
                skipGlyph(c, prevCx, cy, 16 * k, forward = false)
                playGlyph(c, playCx + if (np.playing) 0f else 1.5f * k, cy, 20 * k, np.playing, Color.WHITE)
                skipGlyph(c, nextCx, cy, 16 * k, forward = true)
                val maxW = prevCx - btn / 2 - 16 * k
                c.line(t?.title ?: "Nothing playing", text(ctx, s, 17f, 800, Color.WHITE), 16 * k, h - 30 * k, maxW)
                c.line(t?.artist ?: "Tap to open BopDop", text(ctx, s, 13f, 500, 0xD9FFFFFF.toInt()), 16 * k, h - 13 * k, maxW)
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
