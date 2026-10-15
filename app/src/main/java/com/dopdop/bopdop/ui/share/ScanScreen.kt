package com.dopdop.bopdop.ui.share

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dopdop.bopdop.data.share.ShareCodec
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.components.PillButton
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.glass.glassLite
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * In-app code scanner: the camera fills the screen, a frosted surround with a
 * glass viewfinder frames the code, and a sweep line breathes while it looks.
 * Works on 3D BopDop codes (ZXing handles the perspective) and from photos.
 */
@Composable
fun ScanScreen() {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var handled by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    fun deliver(text: String) {
        if (handled) return
        if (ShareCodec.decode(text) == null) { app.toast("That isn’t a BopDop code"); return }
        handled = true
        app.nav.popBackStack()
        app.openShared(text)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.Default) { decodeImage(ctx, uri) }
            if (text != null) deliver(text) else app.toast("No code found in that image")
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0E0F13))) {
        if (granted) CameraView(torch) { deliver(it) }

        // Frosted surround with a clear rounded window.
        val sweep = rememberInfiniteTransition(label = "sweep").animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "s")
        Canvas(Modifier.fillMaxSize()) {
            val side = size.width * 0.72f
            val left = (size.width - side) / 2; val top = size.height * 0.42f - side / 2
            val window = Path().apply { addRoundRect(RoundRect(left, top, left + side, top + side, CornerRadius(42.dp.toPx()))) }
            clipPath(window, ClipOp.Difference) { drawRect(Color(0xCC0E0F13)) }
            // Corner brackets
            val l = 34.dp.toPx(); val w = 5.dp.toPx(); val r = 30.dp.toPx()
            val c = Color.White
            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                val p = Path().apply {
                    moveTo(x, y + dy * (r + l)); lineTo(x, y + dy * r)
                    quadraticTo(x, y, x + dx * r, y)
                    lineTo(x + dx * (r + l), y)
                }
                drawPath(p, c, style = Stroke(w, cap = StrokeCap.Round))
            }
            corner(left, top, 1f, 1f); corner(left + side, top, -1f, 1f)
            corner(left, top + side, 1f, -1f); corner(left + side, top + side, -1f, -1f)
            if (granted && !handled) {
                val y = top + side * (0.12f + 0.76f * sweep.value)
                drawLine(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, Color(0xCC8FE3FF), Color.Transparent), startX = left, endX = left + side),
                    Offset(left + 16.dp.toPx(), y), Offset(left + side - 16.dp.toPx(), y), 2.dp.toPx(),
                )
            }
        }

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Circle(Icons.Rounded.Close, "Close") { app.nav.popBackStack() }
                Spacer(Modifier.weight(1f))
                if (granted) Circle(if (torch) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff, "Torch", active = torch) { torch = !torch }
            }
            Text("Scan a BopDop code", style = BopType.Title, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            Text("Flat or 3D — hold it inside the frame", style = BopType.Body, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.weight(1f))
            if (!granted) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.NoPhotography, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (asked) "Camera access is off. You can still scan a code from a photo." else "Allow the camera to scan codes.",
                        style = BopType.Body, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                    )
                    if (!asked) {
                        Spacer(Modifier.height(12.dp))
                        PillButton("Allow camera", null, onMedia = true) { permission.launch(Manifest.permission.CAMERA) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 30.dp), horizontalArrangement = Arrangement.Center) {
                PillButton("From photo", Icons.Rounded.Image, Modifier.width(190.dp), filled = false, onMedia = true) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
        }
    }
}

@Composable
private fun Circle(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).bounceClick(0.88f, onClick = onClick).glassLite(CircleShape, tint = if (active) Color.White else Color.White, alpha = if (active) 0.9f else 0.18f),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = if (active) Bop.Ink else Color.White, modifier = Modifier.size(22.dp)) }
}

/** zxing-android-embedded's bare camera view — no stock UI, decoding QR only. */
@Composable
private fun CameraView(torch: Boolean, onText: (String) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val ctx = LocalContext.current
    val view = remember {
        BarcodeView(ctx).apply {
            decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE), mapOf(DecodeHintType.TRY_HARDER to true), "UTF-8", 0)
            decodeContinuous(BarcodeCallback { r -> r?.text?.let(onText) })
        }
    }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> view.resume()
                Lifecycle.Event.ON_PAUSE -> view.pause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.resume()
        onDispose { lifecycle.removeObserver(obs); view.pause() }
    }
    LaunchedEffect(torch) { runCatching { view.setTorch(torch) } }
    AndroidView({ view }, Modifier.fillMaxSize())
}

private fun decodeImage(ctx: android.content.Context, uri: Uri): String? = runCatching {
    val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28)
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, _, _ -> d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE; d.isMutableRequired = false }
    else @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(ctx.contentResolver, uri)
    val scaled = if (bmp.width > 1400) Bitmap.createScaledBitmap(bmp, 1400, (bmp.height * 1400f / bmp.width).toInt(), true) else bmp
    val px = IntArray(scaled.width * scaled.height)
    scaled.getPixels(px, 0, scaled.width, 0, 0, scaled.width, scaled.height)
    QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(scaled.width, scaled.height, px))), mapOf(DecodeHintType.TRY_HARDER to true)).text
}.getOrNull()
