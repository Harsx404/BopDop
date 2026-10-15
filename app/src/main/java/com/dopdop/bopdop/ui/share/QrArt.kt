package com.dopdop.bopdop.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.dopdop.bopdop.R
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.ByteMatrix
import com.google.zxing.qrcode.encoder.Encoder

/** Everything the share card / live preview needs to agree on. */
data class QrLook(
    val theme: IsoTheme? = null, // null = colours from the cover
    val shape: BlockShape = BlockShape.CUBE,
    val coverInCode: Boolean = true,
    val decor: Boolean = true,
)

/** The rendered share image, whether its 3D code scans on its own, and the camera angle used. */
class ShareCard(val bitmap: Bitmap, val scans3d: Boolean, val pitch: Float)

object QrArt {
    /** High error correction so the cover pedestal can sit on modules and still scan. */
    fun matrix(text: String, withCover: Boolean): ByteMatrix = Encoder.encode(
        text, if (withCover) ErrorCorrectionLevel.H else ErrorCorrectionLevel.Q,
        mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 0),
    ).matrix

    /**
     * The image people share: a pearl liquid-metal ground tinted by the cover,
     * the playlist's cover and name, and the code as a floating 3D diorama.
     * The card is decoded before it's returned; if the 3D code can't be read,
     * a small flat code is added so every shared image scans.
     */
    fun card(ctx: Context, link: String, title: String, subtitle: String, cover: Bitmap?, theme: IsoTheme, look: QrLook, width: Int = 1080): ShareCard {
        val h = (width * 1.45f).toInt()
        val w = width.toFloat()
        val m = matrix(link, look.coverInCode && cover != null)
        val iso = IsoQr(m, theme, look.shape, if (look.coverInCode) cover else null, look.decor, seed = link.hashCode())

        fun base(): Pair<Bitmap, Canvas> {
            val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            metalGround(c, w, h.toFloat(), theme)
            header(ctx, c, w, title, subtitle, cover, theme)
            return bmp to c
        }

        // 1) Try the pure 3D card, easing the camera down until the code verifies.
        for (pitch in listOf(56f, 48f, 40f)) {
            val (bmp, c) = base()
            val stage = RectF(w * 0.06f, w * 0.30f, w * 0.94f, w * 0.30f + w * 0.88f)
            c.save(); c.translate(stage.left, stage.top)
            drawGroundShadow(c, stage.width(), stage.height(), 46)
            iso.draw(c, stage.width(), stage.height(), tilt = 1f, yawDeg = 45f, pitchDeg = pitch)
            c.restore()
            footer(ctx, c, w, h.toFloat(), "Scan with BopDop", "Library  ›  Scan a code")
            if (IsoQr.scans(bmp, link)) return ShareCard(bmp, true, pitch)
            bmp.recycle()
        }

        // 2) Keep the diorama as the hero, add a small flat code that always scans.
        val (bmp2, c2) = base()
        val stage2 = RectF(w * 0.04f, w * 0.30f, w * 0.70f, w * 0.30f + w * 0.70f)
        c2.save(); c2.translate(stage2.left, stage2.top)
        drawGroundShadow(c2, stage2.width(), stage2.height(), 40)
        iso.draw(c2, stage2.width(), stage2.height(), tilt = 1f, yawDeg = 45f)
        c2.restore()
        val plate = RectF(w * 0.60f, stage2.bottom - w * 0.06f, w * 0.94f, stage2.bottom - w * 0.06f + w * 0.34f)
        val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); setShadowLayer(w * 0.03f, 0f, w * 0.01f, 0x33000000) }
        c2.drawRoundRect(plate, w * 0.045f, w * 0.045f, pp)
        val flat = IsoQr(m, theme, BlockShape.CUBE, null, decor = false)
        val inset = plate.width() * 0.07f
        c2.save(); c2.translate(plate.left + inset, plate.top + inset)
        flat.draw(c2, plate.width() - 2 * inset, plate.height() - 2 * inset, tilt = 0f, yawDeg = 0f)
        c2.restore()
        footer(ctx, c2, w, h.toFloat(), "Scan the small code with BopDop", "Library  ›  Scan a code")
        return ShareCard(bmp2, false, 56f)
    }

    /** Pearl metal: cool base tinted by the theme, satin highlight bands and soft troughs. */
    fun metalGround(c: Canvas, w: Float, h: Float, theme: IsoTheme) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val top = ColorUtils.blendARGB(0xFFEEF0F3.toInt(), theme.grass, 0.18f)
        val bottom = ColorUtils.blendARGB(0xFFD6D9DF.toInt(), theme.eye, 0.12f)
        p.shader = LinearGradient(0f, 0f, w * 0.3f, h, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        // Flowing satin: alternating bright and soft-dark diagonal bands.
        val bands = listOf(Triple(0.18f, 0x88FFFFFF.toInt(), 0.22f), Triple(0.42f, 0x1A1E2430, 0.18f), Triple(0.63f, 0x99FFFFFF.toInt(), 0.16f), Triple(0.86f, 0x141E2430, 0.2f))
        for ((pos, col, width) in bands) {
            val y = h * pos
            p.shader = LinearGradient(0f, y - h * width, w * 0.55f, y + h * width,
                intArrayOf(col and 0x00FFFFFF, col, col and 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.save(); c.rotate(-14f, w / 2, h / 2)
            c.drawRect(-w, y - h * width, w * 2, y + h * width, p)
            c.restore()
        }
        // A warm glow from the theme hero colour.
        p.shader = RadialGradient(w * 0.85f, h * 0.12f, w * 0.7f, intArrayOf(theme.eye and 0x00FFFFFF or 0x30000000, theme.eye and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
    }

    private fun font(ctx: Context): Typeface = runCatching { ResourcesCompat.getFont(ctx, R.font.jakarta) }.getOrNull() ?: Typeface.DEFAULT

    private fun header(ctx: Context, c: Canvas, w: Float, title: String, subtitle: String, cover: Bitmap?, theme: IsoTheme) {
        val pad = w * 0.075f
        val size = w * 0.16f
        val r = RectF(pad, pad, pad + size, pad + size)
        val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); setShadowLayer(w * 0.025f, 0f, w * 0.008f, 0x40000000) }
        c.drawRoundRect(r, size * 0.22f, size * 0.22f, sh)
        if (cover != null) drawBitmapRounded(c, cover, r, size * 0.22f)
        else {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(r.left, r.top, r.right, r.bottom, theme.eye, theme.block, Shader.TileMode.CLAMP) }
            c.drawRoundRect(r, size * 0.22f, size * 0.22f, p)
        }
        val f = font(ctx)
        val tx = r.right + w * 0.045f
        val maxW = w - tx - pad
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF15161B.toInt(); textSize = w * 0.056f; typeface = Typeface.create(f, Typeface.BOLD) }
        val s = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x9915161B.toInt(); textSize = w * 0.034f; typeface = f }
        c.drawText(ellipsize(title, t, maxW), tx, r.centerY() - w * 0.004f, t)
        c.drawText(ellipsize(subtitle, s, maxW), tx, r.centerY() + w * 0.048f, s)
    }

    private fun footer(ctx: Context, c: Canvas, w: Float, h: Float, line1: String, line2: String) {
        val f = font(ctx)
        val a = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF15161B.toInt(); textSize = w * 0.038f; typeface = Typeface.create(f, Typeface.BOLD); textAlign = Paint.Align.CENTER }
        val b = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x8015161B.toInt(); textSize = w * 0.03f; typeface = f; textAlign = Paint.Align.CENTER }
        c.drawText(line1, w / 2, h - w * 0.13f, a)
        c.drawText(line2, w / 2, h - w * 0.085f, b)
        // Wordmark pill
        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x6615161B; textSize = w * 0.026f; typeface = Typeface.create(f, Typeface.BOLD); textAlign = Paint.Align.CENTER; letterSpacing = 0.2f }
        c.drawText("BOPDOP", w / 2, h - w * 0.035f, brand)
    }

    fun drawBitmapRounded(c: Canvas, b: Bitmap, dst: RectF, radius: Float) {
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

    private fun ellipsize(s: String, p: Paint, max: Float): String {
        if (p.measureText(s) <= max) return s
        var e = s
        while (e.isNotEmpty() && p.measureText("$e…") > max) e = e.dropLast(1)
        return "$e…"
    }
}
