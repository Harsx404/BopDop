package com.dopdop.bopdop.data

import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.net.TtlCache
import com.dopdop.bopdop.data.ytm.ShortClip
import com.dopdop.bopdop.data.ytm.YtMusic
import com.dopdop.bopdop.data.ytm.YtShorts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The full song for a Short. [tagged]: it's the song the Short itself uses (YouTube's sound tag,
 * matched to YouTube Music's song); otherwise a suggestion, because the Short uses its own sound.
 */
data class ShortSong(val track: Track, val tagged: Boolean)

/**
 * Finds the full recording behind a Short's sound, so the feed never plays a clip's audio as a
 * song. The sound's own video is looked up on YouTube Music and swapped for its song version; when
 * the sound isn't a song (someone's own audio), the sound's name is searched, and failing that the
 * Short keeps the song it was found with as a suggestion.
 */
object ShortsSongs {
    private val found = TtlCache<String, ShortSong>(6 * 3600_000L, 400)
    private val missing = TtlCache<String, Boolean>(30 * 60_000L, 400)

    fun known(clipId: String): ShortSong? = found.get(clipId)

    suspend fun of(clip: ShortClip, fallback: Track?): ShortSong? = withContext(Dispatchers.IO) {
        found.get(clip.id)?.let { return@withContext it }
        if (missing.get(clip.id) == true) return@withContext fallback?.let { ShortSong(it, tagged = false) }
        val sound = attempt { YtShorts.sound(clip.id) }
        val song = sound?.sourceId?.let { src -> attempt { songOf(src) } }?.let { ShortSong(it, tagged = true) }
            ?: sound?.takeUnless { it.original }?.let { s -> attempt { byName(s.title, s.by) } }?.let { ShortSong(it, tagged = true) }
        if (song != null) { found.put(clip.id, song); return@withContext song }
        missing.put(clip.id, true)
        fallback?.let { ShortSong(it, tagged = false) }
    }

    /** The song version of [videoId] on YouTube Music, or null when it isn't a released song. */
    private suspend fun songOf(videoId: String): Track? {
        val version = YtMusic.songVersionOf(videoId)
        if (version != null && version != videoId) return YtMusic.trackInfo(version)?.takeIf(::isSong)
        return YtMusic.trackInfo(videoId)?.takeIf { it.albumId != null && isSong(it) }
    }

    /** A sound named like a song ("AZUL") but with no linked video: the best song search hit that shares its words. */
    private suspend fun byName(title: String, by: String): Track? {
        val clean = title.replace(Regex("\\((?i:lyrics?|official.*?|audio|video|slowed.*?|sped up.*?)\\)"), "").replace(Regex("\\s+"), " ").trim()
        if (clean.length < 2) return null
        val artist = by.removePrefix("@").removeSuffix(" - Topic").takeIf { it.isNotBlank() && !it.contains("Release", ignoreCase = true) }.orEmpty()
        val words = clean.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.ifEmpty { listOf(clean.lowercase()) }
        return YtMusic.searchSongs("$clean $artist".trim()).firstOrNull { t -> isSong(t) && words.any { it in t.title.lowercase() } }
    }

    private fun isSong(t: Track) = !t.isPodcast && !t.isShortAudio && t.title.isNotBlank()

    private suspend fun <T> attempt(block: suspend () -> T?): T? = try { block() }
        catch (e: CancellationException) { throw e } catch (_: Exception) { null }
}
