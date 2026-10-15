package com.dopdop.bopdop.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.dopdop.bopdop.R
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.ByteMatrix
import com.google.zxing.qrcode.encoder.Encoder

enum class DotStyle(val label: String) { SQUARE("Square"), ROUNDED("Rounded"), DOTS("Dots"), LIQUID("Liquid"), DIAMOND("Diamond") }
enum class EyeStyle(val label: String) { SQUARE("Square"), ROUNDED("Rounded"), CIRCLE("Circle"), LEAF("Leaf") }
enum class LogoStyle(val label: String) { COVER("Cover"), BRAND("BopDop"), NONE("None") }
enum class CardBg(val label: String) { AURORA("Aurora"), LIGHT("Light"), DARK("Dark") }

/** A colour preset: the dots' gradient (start → end) and the eye colour. */
data class QrPalette(val name: String, val start: Int, val end: Int, val eye: Int)

val QrPresets = listOf(
    QrPalette("Aurora", 0xFF2443C4.toInt(), 0xFFB2408A.toInt(), 0xFF0A5CFF.toInt()),
    QrPalette("Ocean", 0xFF0B4F9C.toInt(), 0xFF1FA3C4.toInt(), 0xFF073B78.toInt()),
    QrPalette("Sunset", 0xFFE0466E.toInt(), 0xFFF29A3B.toInt(), 0xFFC22E5A.toInt()),
    QrPalette("Mint", 0xFF0E8A6A.toInt(), 0xFF2F6FD6.toInt(), 0xFF0B6B53.toInt()),
    QrPalette("Grape", 0xFF5B2BB8.toInt(), 0xFFD0459B.toInt(), 0xFF4A1F9E.toInt()),
    QrPalette("Mono", 0xFF15161B.toInt(), 0xFF15161B.toInt(), 0xFF15161B.toInt()),
)

data class QrStyle(
    val dots: DotStyle = DotStyle.LIQUID,
    val eyes: EyeStyle = EyeStyle.ROUNDED,
    /** null = take the colours from the cover art. */
    val palette: QrPalette? = null,
    val logo: LogoStyle = LogoStyle.COVER,
    val bg: CardBg = CardBg.AURORA,
)

object QrArt {
    private const val QUIET = 2

    /** High error correction so the centre logo can cover modules and still scan. */
    fun matrix(text: String, withLogo: Boolean): ByteMatrix = Encoder.encode(
        text, if (withLogo) ErrorCorrectionLevel.H else ErrorCorrectionLevel.Q,
        mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"),
    ).matrix

    private fun isEye(x: Int, y: Int, n: Int) = (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7)

    /**
     * Draw the code into [size]×[size] at the canvas origin. Dark modules use a
     * diagonal [start]→[end] gradient; finder "eyes" are drawn as single shapes.
     */
    fun draw(c: Canvas, size: Float, m: ByteMatrix, style: QrStyle, start: Int, end: Int, eye: Int, logo: Bitmap?) {
        val n = m.width
        val cell = size / (n + QUIET * 2)
        val o = QUIET * cell
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, size, size, start, end, Shader.TileMode.CLAMP)
        }
        // Clear a centre square for the logo (≈ 22% of the code, rounded to whole modules).
        val hole = if (style.logo != LogoStyle.NONE) ((n * 0.22f).toInt() or 1).coerceAtLeast(5) else 0
        val h0 = (n - hole) / 2
        fun dark(x: Int, y: Int) = x in 0 until n && y in 0 until n && m.get(x, y).toInt() == 1 && !isEye(x, y, n) &&
            !(hole > 0 && x in (h0 - 1)..(h0 + hole) && y in (h0 - 1)..(h0 + hole))

        val r = RectF()
        for (y in 0 until n) for (x in 0 until n) {
            if (!dark(x, y)) continue
            val l = o + x * cell; val t = o + y * cell
            when (style.dots) {
                DotStyle.SQUARE -> c.drawRect(l, t, l + cell + 0.5f, t + cell + 0.5f, paint)
                DotStyle.ROUNDED -> { r.set(l + cell * 0.06f, t + cell * 0.06f, l + cell * 0.94f, t + cell * 0.94f); c.drawRoundRect(r, cell * 0.32f, cell * 0.32f, paint) }
                DotStyle.DOTS -> c.drawCircle(l + cell / 2, t + cell / 2, cell * 0.43f, paint)
                DotStyle.DIAMOND -> c.drawPath(Path().apply {
                    moveTo(l + cell / 2, t + cell * 0.04f); lineTo(l + cell * 0.96f, t + cell / 2)
                    lineTo(l + cell / 2, t + cell * 0.96f); lineTo(l + cell * 0.04f, t + cell / 2); close()
                }, paint)
                DotStyle.LIQUID -> {
                    // Round blobs that melt into their right / lower neighbours — droplets fusing.
                    val rad = cell * 0.46f
                    c.drawCircle(l + cell / 2, t + cell / 2, rad, paint)
                    if (dark(x + 1, y)) c.drawRect(l + cell / 2, t + cell / 2 - rad, l + cell * 1.5f, t + cell / 2 + rad, paint)
                    if (dark(x, y + 1)) c.drawRect(l + cell / 2 - rad, t + cell / 2, l + cell / 2 + rad, t + cell * 1.5f, paint)
                }
            }
        }

        val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = eye }
        listOf(0 to 0, n - 7 to 0, 0 to n - 7).forEachIndexed { i, (ex, ey) ->
            drawEye(c, o + ex * cell, o + ey * cell, cell, style.eyes, eyePaint, i)
        }

        if (hole > 0) {
            val side = hole * cell
            val l = o + h0 * cell; val t = o + h0 * cell
            r.set(l, t, l + side, t + side)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
            c.drawRoundRect(r, side * 0.24f, side * 0.24f, bgPaint)
            val inner = RectF(r).apply { inset(side * 0.08f, side * 0.08f) }
            if (logo != null) drawBitmapRounded(c, logo, inner, side * 0.18f)
            else drawBrand(c, inner, start, end)
        }
    }

    private fun drawEye(c: Canvas, l: Float, t: Float, cell: Float, s: EyeStyle, p: Paint, corner: Int) {
        val outer = RectF(l, t, l + 7 * cell, t + 7 * cell)
        val ring = Path().apply { fillType = Path.FillType.EVEN_ODD }
        val holeR = RectF(l + cell, t + cell, l + 6 * cell, t + 6 * cell)
        val dot = RectF(l + 2 * cell, t + 2 * cell, l + 5 * cell, t + 5 * cell)
        when (s) {
            EyeStyle.SQUARE -> { ring.addRect(outer, Path.Direction.CW); ring.addRect(holeR, Path.Direction.CW); c.drawPath(ring, p); c.drawRect(dot, p) }
            EyeStyle.ROUNDED -> {
                ring.addRoundRect(outer, cell * 2.2f, cell * 2.2f, Path.Direction.CW); ring.addRoundRect(holeR, cell * 1.5f, cell * 1.5f, Path.Direction.CW)
                c.drawPath(ring, p); c.drawRoundRect(dot, cell * 0.9f, cell * 0.9f, p)
            }
            EyeStyle.CIRCLE -> {
                ring.addOval(outer, Path.Direction.CW); ring.addOval(holeR, Path.Direction.CW)
                c.drawPath(ring, p); c.drawOval(dot, p)
            }
            EyeStyle.LEAF -> {
                // Rounded on three corners, sharp on the one pointing at the code's centre.
                fun radii(rr: Float): FloatArray {
                    val a = FloatArray(8) { rr }
                    val sharp = when (corner) { 0 -> 2; 1 -> 3; else -> 1 } // 0:TL→BR sharp, 1:TR→BL, 2:BL→TR
                    a[sharp * 2] = 0f; a[sharp * 2 + 1] = 0f
                    return a
                }
                ring.addRoundRect(outer, radii(cell * 2.6f), Path.Direction.CW); ring.addRoundRect(holeR, radii(cell * 1.8f), Path.Direction.CW)
                c.drawPath(ring, p)
                c.drawPath(Path().apply { addRoundRect(dot, radii(cell * 1.2f), Path.Direction.CW) }, p)
            }
        }
    }

    private fun drawBitmapRounded(c: Canvas, b: Bitmap, dst: RectF, radius: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val sh = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = maxOf(dst.width() / b.width, dst.height() / b.height)
        sh.setLocalMatrix(Matrix().apply {
            setScale(scale, scale)
            postTranslate(dst.left + (dst.width() - b.width * scale) / 2, dst.top + (dst.height() - b.height * scale) / 2)
        })
        p.shader = sh
        c.drawRoundRect(dst, radius, radius, p)
    }

    /** The BopDop mark: a gradient droplet with a white "B". */
    private fun drawBrand(c: Canvas, dst: RectF, start: Int, end: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(dst.left, dst.top, dst.right, dst.bottom, start, end, Shader.TileMode.CLAMP) }
        c.drawRoundRect(dst, dst.width() * 0.3f, dst.width() * 0.3f, p)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER; textSize = dst.height() * 0.62f; typeface = Typeface.DEFAULT_BOLD
        }
        c.drawText("B", dst.centerX(), dst.centerY() - (t.descent() + t.ascent()) / 2, t)
    }

    // --- Share card ------------------------------------------------------------------------

    /**
     * The image people actually share: cover, title, the styled code on a white
     * plate, over the Liquid Glass aurora (or a plain light / dark ground).
     */
    fun card(
        ctx: Context, link: String, title: String, subtitle: String, cover: Bitmap?, style: QrStyle,
        artStart: Int, artEnd: Int, width: Int = 1080,
    ): Bitmap {
        val h = (width * 1.42f).toInt()
        val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = width.toFloat()
        val pal = style.palette ?: QrPalette("Cover", darken(artStart), darken(artEnd), darken(artStart, 0.55f))

        // Ground
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        when (style.bg) {
            CardBg.AURORA -> {
                bg.color = 0xFF161338.toInt(); c.drawRect(0f, 0f, w, h.toFloat(), bg)
                listOf(Triple(0.1f, 0.05f, artStart), Triple(0.95f, 0.35f, 0xFF7A45CC.toInt()), Triple(0.3f, 0.75f, artEnd), Triple(0.85f, 1f, 0xFFE2678F.toInt()))
                    .forEach { (x, y, col) ->
                        bg.shader = RadialGradient(w * x, h * y, w * 0.85f, intArrayOf(col and 0xD0FFFFFF.toInt(), col and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                        c.drawRect(0f, 0f, w, h.toFloat(), bg)
                    }
                bg.shader = null
            }
            CardBg.LIGHT -> { bg.color = 0xFFF2F3F7.toInt(); c.drawRect(0f, 0f, w, h.toFloat(), bg) }
            CardBg.DARK -> { bg.color = 0xFF0E0D1C.toInt(); c.drawRect(0f, 0f, w, h.toFloat(), bg) }
        }
        val onBg = if (style.bg == CardBg.LIGHT) 0xFF15161B.toInt() else 0xFFFFFFFF.toInt()
        val onBg2 = if (style.bg == CardBg.LIGHT) 0x9915161B.toInt() else 0xB3FFFFFF.toInt()
        val font = runCatching { ResourcesCompat.getFont(ctx, R.font.jakarta) }.getOrNull() ?: Typeface.DEFAULT

        // Header: cover + title
        val pad = w * 0.08f
        val coverSize = w * 0.2f
        val coverRect = RectF(pad, pad, pad + coverSize, pad + coverSize)
        if (cover != null) drawBitmapRounded(c, cover, coverRect, coverSize * 0.18f)
        else drawBrand(c, coverRect, artStart, artEnd)
        val tx = coverRect.right + w * 0.045f
        val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = onBg; textSize = w * 0.058f; typeface = Typeface.create(font, Typeface.BOLD) }
        val subP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = onBg2; textSize = w * 0.036f; typeface = font }
        val maxW = w - tx - pad
        c.drawText(ellipsize(title, titleP, maxW), tx, coverRect.centerY() - w * 0.005f, titleP)
        c.drawText(ellipsize(subtitle, subP, maxW), tx, coverRect.centerY() + w * 0.05f, subP)

        // Glass plate behind the code
        val plateTop = coverRect.bottom + w * 0.07f
        val plate = RectF(pad, plateTop, w - pad, plateTop + (w - 2 * pad))
        val plateP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        if (style.bg != CardBg.LIGHT) {
            plateP.setShadowLayer(w * 0.04f, 0f, w * 0.012f, 0x55000000)
        } else plateP.setShadowLayer(w * 0.03f, 0f, w * 0.01f, 0x22000000)
        c.drawRoundRect(plate, w * 0.07f, w * 0.07f, plateP)
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE; strokeWidth = w * 0.004f
            shader = LinearGradient(plate.left, plate.top, plate.right, plate.bottom, intArrayOf(0x40000000, 0x08000000, 0x30000000), null, Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(plate, w * 0.07f, w * 0.07f, rim)

        val qrInset = plate.width() * 0.05f
        val qrSize = plate.width() - 2 * qrInset
        val logo = if (style.logo == LogoStyle.COVER) cover else null
        val mtx = matrix(link, style.logo != LogoStyle.NONE)
        c.save()
        c.translate(plate.left + qrInset, plate.top + qrInset)
        draw(c, qrSize, mtx, style, pal.start, pal.end, pal.eye, logo)
        c.restore()

        // Footer
        val foot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = onBg; textSize = w * 0.04f; typeface = Typeface.create(font, Typeface.BOLD); textAlign = Paint.Align.CENTER }
        val foot2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = onBg2; textSize = w * 0.032f; typeface = font; textAlign = Paint.Align.CENTER }
        val fy = plate.bottom + (h - plate.bottom) * 0.45f
        c.drawText("Scan with BopDop", w / 2, fy, foot)
        c.drawText("Library  ›  Scan code", w / 2, fy + w * 0.055f, foot2)
        return bmp
    }

    private fun ellipsize(s: String, p: Paint, max: Float): String {
        if (p.measureText(s) <= max) return s
        var e = s
        while (e.isNotEmpty() && p.measureText("$e…") > max) e = e.dropLast(1)
        return "$e…"
    }

    /** Cover colours are often light; QR dots need to stay dark enough to scan on white. */
    private fun darken(argb: Int, maxL: Float = 0.42f): Int {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(argb, hsl)
        hsl[2] = hsl[2].coerceAtMost(maxL)
        hsl[1] = (hsl[1] * 1.15f).coerceAtMost(0.95f)
        return androidx.core.graphics.ColorUtils.HSLToColor(hsl)
    }
}
