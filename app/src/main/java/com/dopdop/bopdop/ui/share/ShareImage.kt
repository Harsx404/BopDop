package com.dopdop.bopdop.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File

/** Writes [bmp] to the shared cache folder and opens the share sheet (Instagram Stories, chats…). */
fun shareBitmap(ctx: Context, bmp: Bitmap, name: String, text: String? = null, chooserTitle: String = "Share") {
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    val safe = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "bopdop" }
    val f = File(dir, "bopdop-$safe.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    val send = Intent(Intent.ACTION_SEND).setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .apply { text?.let { putExtra(Intent.EXTRA_TEXT, it) } }
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
