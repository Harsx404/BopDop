package com.dopdop.bopdop.data

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A font file the user brings (Settings › Appearance › Font › Import). It's copied into the app's
 * own storage, so it keeps working after the original is moved or deleted. Each import gets a new
 * file name: Compose caches typefaces by file, and the name in settings is what points at it.
 */
object CustomFont {
    private const val DIR = "fonts"

    fun file(ctx: Context, name: String): File? = name.takeIf { it.isNotBlank() }?.let { File(File(ctx.filesDir, DIR), it) }?.takeIf { it.isFile }

    /**
     * Copies the font at [uri] in and returns (stored file name, display name), or null if it isn't
     * a font Android can draw (a renamed image, a broken download…). Older imports are removed.
     */
    suspend fun import(ctx: Context, uri: Uri): Pair<String, String>? = withContext(Dispatchers.IO) {
        val dir = File(ctx.filesDir, DIR).apply { mkdirs() }
        val display = runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull().orEmpty()
        val ext = display.substringAfterLast('.', "ttf").lowercase().takeIf { it in setOf("ttf", "otf", "ttc") } ?: "ttf"
        val name = "custom-${System.currentTimeMillis()}.$ext"
        val out = File(dir, name)
        val copied = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } != null
        }.getOrDefault(false)
        // Typeface.Builder returns null for anything that isn't a usable font file.
        if (!copied || out.length() == 0L || runCatching { Typeface.Builder(out).build() }.getOrNull() == null) {
            out.delete()
            return@withContext null
        }
        dir.listFiles()?.filter { it.name != name }?.forEach { it.delete() }
        name to display.substringBeforeLast('.').ifBlank { "Your font" }
    }
}
