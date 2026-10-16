package com.dopdop.bopdop.data.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Locale

/** One audio file MediaStore knows about, before the music filter. */
internal data class LocalFile(
    val id: Long, val title: String, val artist: String, val album: String, val albumId: Long,
    val durationMs: Long, val folder: String, val mime: String, val isMusic: Boolean,
)

/** Short clips, voice notes, call/screen recordings, WAV captures and system sounds aren't songs. */
internal fun LocalFile.looksLikeMusic(): Boolean {
    if (durationMs < 30_000) return false
    if (mime.contains("wav", ignoreCase = true)) return false
    val path = folder.lowercase(Locale.ROOT)
    val noise = listOf("record", "voice", "call", "ringtone", "notification", "alarm", "whatsapp audio", "whatsapp voice", "telegram audio", "screen")
    if (noise.any { it in path }) return false
    return isMusic
}

/** Music on this phone, read from MediaStore. */
object LocalMusic {
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()
    private val _folders = MutableStateFlow<List<String>>(emptyList())
    /** Folders that hold music, for the folder picker. */
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    val permission: String get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(context: Context) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Re-reads MediaStore with the current folder and filter settings. */
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) { _tracks.value = emptyList(); _folders.value = emptyList(); return@withContext }
        val s = LibraryStore.settings
        val files = query(context).filter { !s.filterNonMusic || it.looksLikeMusic() }
        _folders.value = files.map { it.folder }.distinct().sorted()
        _tracks.value = files.filter { s.localFolder == null || it.folder == s.localFolder }
            .sortedBy { it.title.lowercase(Locale.ROOT) }
            .map { f ->
                Track(
                    id = "lc:${f.id}", title = f.title, artist = f.artist, album = f.album,
                    durationSec = (f.durationMs / 1000).toInt(),
                    artwork = ContentUris.withAppendedId(ALBUM_ART, f.albumId).toString(),
                    streamUrl = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, f.id).toString(),
                )
            }
    }

    private val ALBUM_ART = android.net.Uri.parse("content://media/external/audio/albumart")

    private fun query(context: Context): List<LocalFile> {
        val folderCol = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA
        val cols = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION, folderCol, MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.IS_MUSIC,
        )
        val out = ArrayList<LocalFile>()
        context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val rawFolder = c.getString(6).orEmpty()
                // Pre-Q gives the file path; keep just its folder, shaped like a relative path.
                val folder = if (Build.VERSION.SDK_INT >= 29) rawFolder.trimEnd('/')
                    else rawFolder.substringBeforeLast('/').substringAfter("/storage/emulated/0/").substringAfter("/sdcard/")
                out += LocalFile(
                    id = c.getLong(0),
                    title = c.getString(1)?.ifBlank { null } ?: "Unknown",
                    artist = c.getString(2)?.takeUnless { it.isBlank() || it == "<unknown>" } ?: "Unknown artist",
                    album = c.getString(3).orEmpty(),
                    albumId = c.getLong(4),
                    durationMs = c.getLong(5),
                    folder = folder,
                    mime = c.getString(7).orEmpty(),
                    isMusic = c.getInt(8) != 0,
                )
            }
        }
        return out
    }
}
