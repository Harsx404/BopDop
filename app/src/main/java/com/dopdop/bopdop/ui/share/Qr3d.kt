package com.dopdop.bopdop.ui.share

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.encoder.ByteMatrix
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Colours for one diorama, after the reference's pastel voxel tiles. */
data class IsoTheme(
    val name: String,
    /** Light ground the code sits on (the tile's top face). */
    val ground: Int,
    /** Dark data modules. */
    val block: Int,
    /** Finder "eyes" — the hero colour. */
    val eye: Int,
    /** Tile body / soil band under the ground. */
    val soil: Int,
    /** Grass tufts along the rim. */
    val grass: Int,
)

val IsoThemes = listOf(
    IsoTheme("Lagoon", 0xFFF6F3EC.toInt(), 0xFF3F6B5C.toInt(), 0xFF12B5C9.toInt(), 0xFFB9A58E.toInt(), 0xFFA8D5C8.toInt()),
    IsoTheme("Peach", 0xFFF7F2EE.toInt(), 0xFF7A4B3A.toInt(), 0xFFE07A55.toInt(), 0xFFC4A189.toInt(), 0xFFB9D2C9.toInt()),
    IsoTheme("Lilac", 0xFFF6F3F1.toInt(), 0xFF4E3C78.toInt(), 0xFF8B6CD6.toInt(), 0xFFB79A84.toInt(), 0xFFE3D44A.toInt()),
    IsoTheme("Matcha", 0xFFF4F4EC.toInt(), 0xFF3E5A2A.toInt(), 0xFF7FA33A.toInt(), 0xFFB6A07F.toInt(), 0xFFC9DC8E.toInt()),
    IsoTheme("Graphite", 0xFFF3F4F6.toInt(), 0xFF22252C.toInt(), 0xFF15161B.toInt(), 0xFFA6ABB4.toInt(), 0xFFC4CCD6.toInt()),
)

/** A theme derived from cover colours, kept dark enough on the blocks to scan. */
fun coverTheme(accent: Int, deep: Int): IsoTheme {
    fun tone(c: Int, lMin: Float, lMax: Float, s: Float = 1f): Int {
        val hsl = FloatArray(3); ColorUtils.colorToHSL(c, hsl)
        hsl[1] = (hsl[1] * s).coerceIn(0f, 0.9f); hsl[2] = hsl[2].coerceIn(lMin, lMax)
        return ColorUtils.HSLToColor(hsl)
    }
    return IsoTheme("Cover", 0xFFF6F4F0.toInt(), tone(deep, 0.18f, 0.30f), tone(accent, 0.36f, 0.44f, 1.2f), tone(deep, 0.58f, 0.66f, 0.5f), tone(accent, 0.74f, 0.82f, 0.7f))
}

enum class BlockShape(val label: String) { CUBE("Voxels"), PEBBLE("Pebbles") }

/**
 * Renders a QR matrix as a little isometric world: a thick tile whose top face
 * is the code, data modules extruded as voxels, finder eyes raised higher, an
 * optional cover pedestal in the middle and grass tufts around the rim.
 *
 * [tilt] 0 = flat, straight-on (pixel-perfect scannable code), 1 = full
 * isometric. [yawDeg] rotates the tile around its vertical axis.
 */
class IsoQr(
    private val m: ByteMatrix,
    private val theme: IsoTheme,
    private val shape: BlockShape = BlockShape.CUBE,
    private val cover: Bitmap? = null,
    private val decor: Boolean = true,
    seed: Int = 7,
) {
    private val n = m.width
    private val quiet = 2
    private val rim = 4 // decorative ground beyond the quiet zone
    private val span = n + 2 * (quiet + rim)
    private val holeSize = if (cover != null) ((n * 0.22f).toInt() or 1).coerceAtLeast(5) else 0
    private val h0 = (n - holeSize) / 2

    private fun inHole(x: Int, y: Int) = holeSize > 0 && x in (h0 - 1)..(h0 + holeSize) && y in (h0 - 1)..(h0 + holeSize)
    private fun isEye(x: Int, y: Int) = (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7)
    private fun dark(x: Int, y: Int) = x in 0 until n && y in 0 until n && m.get(x, y).toInt() == 1 && !inHole(x, y)

    private data class Block(val x: Int, val y: Int, val h: Float, val color: Int)

    private val blocks: List<Block> = buildList {
        for (y in 0 until n) for (x in 0 until n) {
            if (!dark(x, y)) continue
            // Every top sits on one plane — raising the eyes shifts them off the module grid and breaks decoding.
            add(Block(x, y, 0.38f, if (isEye(x, y)) theme.eye else theme.block))
        }
    }

    /** Grass tufts on the decorative rim (never inside the code + quiet zone). */
    private val tufts: List<Triple<Float, Float, Float>> = if (!decor) emptyList() else {
        val r = Random(seed)
        val lo = -(quiet + rim).toFloat(); val hi = (n + quiet + rim).toFloat()
        List(150) {
            // Pick a point inside the rim band.
            var px: Float; var py: Float
            do {
                px = lo + r.nextFloat() * (hi - lo); py = lo + r.nextFloat() * (hi - lo)
            } while (px > -quiet - 0.6f && px < n + quiet + 0.6f && py > -quiet - 0.6f && py < n + quiet + 0.6f)
            Triple(px, py, 0.7f + r.nextFloat() * 1.1f)
        }
    }

    // --- projection ------------------------------------------------------------------------

    private var yaw = 0.0; private var pitch = 0.0; private var cell = 1f
    private var cx = 0f; private var cy = 0f

    /** Grid point (x, y on the code plane, z up) → screen. */
    private fun px(x: Float, y: Float, z: Float): Float {
        val gx = x - n / 2f; val gy = y - n / 2f
        return cx + ((gx * cos(yaw) - gy * sin(yaw)) * cell).toFloat()
    }

    private fun py(x: Float, y: Float, z: Float): Float {
        val gx = x - n / 2f; val gy = y - n / 2f
        val yr = gx * sin(yaw) + gy * cos(yaw)
        return cy + ((yr * cos(pitch) - z * sin(pitch)) * cell).toFloat()
    }

    private fun depth(x: Float, y: Float): Double {
        val gx = x - n / 2f; val gy = y - n / 2f
        return gx * sin(yaw) + gy * cos(yaw)
    }

    private fun shade(c: Int, f: Float): Int = ColorUtils.blendARGB(c, if (f < 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(), abs(f))

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private fun quad(c: Canvas, color: Int, vararg p: Float) {
        path.reset()
        path.moveTo(p[0], p[1]); path.lineTo(p[2], p[3]); path.lineTo(p[4], p[5]); path.lineTo(p[6], p[7]); path.close()
        fill.shader = null; fill.color = color
        c.drawPath(path, fill)
    }

    /** Faces of an axis-aligned box from (x0,y0,z0) to (x1,y1,z1) that face the viewer. */
    private fun box(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, z0: Float, z1: Float, color: Int, sideA: Int, sideB: Int, hideSides: BooleanArray? = null) {
        // Side normals in grid space: -x, +x, -y, +y. Visible when their rotated y-component points at the viewer.
        val sinY = sin(yaw); val cosY = cos(yaw)
        if (pitch > 0.02) {
            val faces = listOf(
                Triple(-1.0 * sinY, 0, floatArrayOf(x0, y1, x0, y0)), // -x face: edge from (x0,y1) to (x0,y0)
                Triple(sinY, 1, floatArrayOf(x1, y0, x1, y1)),          // +x
                Triple(-cosY, 2, floatArrayOf(x0, y0, x1, y0)),         // -y
                Triple(cosY, 3, floatArrayOf(x1, y1, x0, y1)),          // +y
            )
            for ((ny, idx, e) in faces) {
                if (ny <= 0.001 || hideSides?.get(idx) == true) continue
                val col = if (idx == 0 || idx == 1) sideA else sideB
                quad(c, col,
                    px(e[0], e[1], z1), py(e[0], e[1], z1), px(e[2], e[3], z1), py(e[2], e[3], z1),
                    px(e[2], e[3], z0), py(e[2], e[3], z0), px(e[0], e[1], z0), py(e[0], e[1], z0))
            }
        }
        quad(c, color,
            px(x0, y0, z1), py(x0, y0, z1), px(x1, y0, z1), py(x1, y0, z1),
            px(x1, y1, z1), py(x1, y1, z1), px(x0, y1, z1), py(x0, y1, z1))
    }

    /**
     * Draw centred in a [w]×[h] area. The code (with quiet zone) is scaled so
     * the whole tile fits; returns nothing — pure drawing into [c].
     */
    fun draw(c: Canvas, w: Float, h: Float, tilt: Float, yawDeg: Float, pitchDeg: Float = 56f) {
        val t = tilt.coerceIn(0f, 1f)
        yaw = Math.toRadians((yawDeg * t).toDouble())
        pitch = Math.toRadians((pitchDeg * t).toDouble())
        // Fit: the tile's diagonal grows with yaw; leave headroom for the slab + eyes.
        val extent = span * (abs(cos(yaw)) + abs(sin(yaw))).toFloat()
        val vScale = (extent * cos(pitch) + 3.5f * sin(pitch)).toFloat()
        cell = minOf(w / extent, h / vScale.coerceAtLeast(1f)) * 0.98f
        cx = w / 2f; cy = h / 2f + (1.2f * sin(pitch) * cell).toFloat()

        val lo = -(quiet + rim).toFloat(); val hi = (n + quiet + rim).toFloat()
        val slab = 3.4f * t
        // Tile body: soil band, then the ground slab — layered like the reference's tile edges.
        val soilA = shade(theme.soil, -0.10f); val soilB = shade(theme.soil, -0.22f)
        if (t > 0.01f) {
            box(c, lo, lo, hi, hi, -slab, -slab * 0.55f, shade(theme.soil, -0.08f), shade(soilA, -0.06f), shade(soilB, -0.06f))
            box(c, lo, lo, hi, hi, -slab * 0.55f, -slab * 0.22f, theme.soil, soilA, soilB)
            box(c, lo, lo, hi, hi, -slab * 0.22f, 0f, theme.ground, shade(theme.ground, -0.06f), shade(theme.ground, -0.13f))
        } else {
            fill.color = theme.ground
            c.drawRect(px(lo, lo, 0f), py(lo, lo, 0f), px(hi, hi, 0f), py(hi, hi, 0f), fill)
        }

        // Soft contact shade under the code area for depth.
        if (t > 0.05f) {
            fill.color = ColorUtils.setAlphaComponent(theme.soil, (40 * t).toInt())
            quad(c, fill.color,
                px(-quiet.toFloat(), -quiet.toFloat(), 0f), py(-quiet.toFloat(), -quiet.toFloat(), 0f),
                px(n + quiet.toFloat(), -quiet.toFloat(), 0f), py(n + quiet.toFloat(), -quiet.toFloat(), 0f),
                px(n + quiet.toFloat(), n + quiet.toFloat(), 0f), py(n + quiet.toFloat(), n + quiet.toFloat(), 0f),
                px(-quiet.toFloat(), n + quiet.toFloat(), 0f), py(-quiet.toFloat(), n + quiet.toFloat(), 0f))
        }

        // Painter's order: far → near.
        data class Item(val d: Double, val draw: () -> Unit)
        val items = ArrayList<Item>(blocks.size + tufts.size + 1)
        val hz = t
        for (b in blocks) {
            val x0 = b.x.toFloat(); val y0 = b.y.toFloat()
            val top = b.h * hz
            val hide = booleanArrayOf(
                dark(b.x - 1, b.y) && isEye(b.x - 1, b.y) == isEye(b.x, b.y),
                dark(b.x + 1, b.y) && isEye(b.x + 1, b.y) == isEye(b.x, b.y),
                dark(b.x, b.y - 1) && isEye(b.x, b.y - 1) == isEye(b.x, b.y),
                dark(b.x, b.y + 1) && isEye(b.x, b.y + 1) == isEye(b.x, b.y),
            )
            items += Item(depth(x0 + 0.5f, y0 + 0.5f)) {
                if (shape == BlockShape.PEBBLE && !isEye(b.x, b.y)) pebble(c, x0, y0, top, b.color)
                else {
                    // Sides lean toward the ground colour so the decoder still sees crisp dark squares on top.
                    val sideA = ColorUtils.blendARGB(b.color, theme.ground, 0.5f)
                    val sideB = ColorUtils.blendARGB(b.color, theme.ground, 0.32f)
                    val e = 0.03f // overlap neighbours: no hairline seams between tops
                    box(c, x0 - e, y0 - e, x0 + 1f + e, y0 + 1f + e, 0f, top.coerceAtLeast(0.001f), b.color, sideA, sideB, hide)
                }
            }
        }
        for ((gx, gy, s) in tufts) items += Item(depth(gx, gy)) { tuft(c, gx, gy, s * t) }
        if (holeSize > 0) {
            val a = h0.toFloat() - 0.2f; val bnd = (h0 + holeSize).toFloat() + 0.2f
            items += Item(depth(n / 2f, n / 2f) + 0.01) { pedestal(c, a, bnd, 1.3f * hz) }
        }
        items.sortBy { it.d }
        items.forEach { it.draw() }
    }

    private fun pebble(c: Canvas, x0: Float, y0: Float, top: Float, color: Int) {
        // A rounded cylinder: side band + elliptical top.
        val cxp = x0 + 0.5f; val cyp = y0 + 0.5f; val r = 0.44f
        val steps = 14
        val topPath = Path(); val side = Path()
        for (i in 0..steps) {
            val a = 2 * Math.PI * i / steps
            val gx = cxp + (r * cos(a)).toFloat(); val gy = cyp + (r * sin(a)).toFloat()
            if (i == 0) topPath.moveTo(px(gx, gy, top), py(gx, gy, top)) else topPath.lineTo(px(gx, gy, top), py(gx, gy, top))
        }
        topPath.close()
        if (pitch > 0.02) {
            for (i in 0..steps) {
                val a = 2 * Math.PI * i / steps
                val gx = cxp + (r * cos(a)).toFloat(); val gy = cyp + (r * sin(a)).toFloat()
                if (i == 0) side.moveTo(px(gx, gy, 0f), py(gx, gy, 0f)) else side.lineTo(px(gx, gy, 0f), py(gx, gy, 0f))
            }
            side.close()
            fill.shader = null; fill.color = shade(color, -0.25f)
            c.drawPath(side, fill)
            // Connect the two ellipses with a band (approximate by drawing the bottom then the top).
        }
        fill.color = color
        c.drawPath(topPath, fill)
    }

    /** Low-poly grass: three blades leaning out from a point. */
    private val bladeSet = listOf(
        floatArrayOf(-0.42f, 0.05f, -0.30f, 0.70f, -0.14f), floatArrayOf(-0.20f, -0.12f, -0.12f, 0.95f, 0.10f),
        floatArrayOf(0.0f, 0.10f, 0.05f, 1.0f, -0.06f), floatArrayOf(0.22f, -0.06f, 0.18f, 0.85f, 0.12f),
        floatArrayOf(0.40f, 0.08f, 0.34f, 0.65f, -0.10f),
    )

    /** Low-poly grass clump: five spiky blades fanning out, lit from the left. */
    private fun tuft(c: Canvas, gx: Float, gy: Float, s: Float) {
        if (s <= 0.02f) return
        val base = theme.grass
        for (b in bladeSet) {
            val x = gx + b[0]; val y = gy + b[1]
            val hgt = 1.7f * s * b[3]
            val w = 0.17f
            path.reset()
            path.moveTo(px(x - w, y, 0f), py(x - w, y, 0f))
            path.lineTo(px(x + b[2] * 0.5f, y, hgt), py(x + b[2] * 0.5f, y, hgt))
            path.lineTo(px(x + w, y, 0f), py(x + w, y, 0f))
            path.close()
            fill.shader = null
            fill.color = shade(base, b[4])
            c.drawPath(path, fill)
        }
    }

    /** The raised cover-art pedestal in the code's centre. */
    private fun pedestal(c: Canvas, a: Float, b: Float, top: Float) {
        box(c, a, a, b, b, 0f, top.coerceAtLeast(0.001f), 0xFFFFFFFF.toInt(), 0xFFEDEBE6.toInt(), 0xFFD9D5CE.toInt())
        val art = cover ?: return
        val inset = 0.35f
        val x0 = a + inset; val x1 = b - inset
        // Map the bitmap onto the (projected) top face with a polygon-to-polygon matrix.
        val src = floatArrayOf(0f, 0f, art.width.toFloat(), 0f, art.width.toFloat(), art.height.toFloat(), 0f, art.height.toFloat())
        val dst = floatArrayOf(
            px(x0, x0, top), py(x0, x0, top), px(x1, x0, top), py(x1, x0, top),
            px(x1, x1, top), py(x1, x1, top), px(x0, x1, top), py(x0, x1, top),
        )
        val mtx = Matrix().apply { setPolyToPoly(src, 0, dst, 0, 4) }
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        p.shader = BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(mtx) }
        path.reset()
        path.moveTo(dst[0], dst[1]); path.lineTo(dst[2], dst[3]); path.lineTo(dst[4], dst[5]); path.lineTo(dst[6], dst[7]); path.close()
        c.drawPath(path, p)
    }

    companion object {
        /** True when ZXing can read [bmp] — used to decide whether the 3D code needs a flat backup. */
        fun scans(bmp: Bitmap, expected: String): Boolean = runCatching {
            val small = if (bmp.width > 900) Bitmap.createScaledBitmap(bmp, 900, (bmp.height * 900f / bmp.width).toInt(), true) else bmp
            val px = IntArray(small.width * small.height)
            small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
            val src = RGBLuminanceSource(small.width, small.height, px)
            val res = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(src)), mapOf(DecodeHintType.TRY_HARDER to true))
            res.text == expected
        }.getOrDefault(false)
    }
}

/** Soft ellipse shadow drawn under the floating tile. */
fun drawGroundShadow(c: Canvas, w: Float, h: Float, alpha: Int = 40) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = android.graphics.RadialGradient(w / 2, h * 0.86f, w * 0.42f, intArrayOf(alpha shl 24 or 0x1A1F2A, 0x001A1F2A), null, Shader.TileMode.CLAMP)
    c.save(); c.scale(1f, 0.32f, w / 2, h * 0.86f)
    c.drawCircle(w / 2, h * 0.86f, w * 0.42f, p)
    c.restore()
}
