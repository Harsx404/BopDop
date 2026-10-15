package com.dopdop.bopdop.data

import com.dopdop.bopdop.data.lyrics.LyricsApi
import com.dopdop.bopdop.data.model.ArtistPage
import com.dopdop.bopdop.data.model.Card
import com.dopdop.bopdop.data.model.CardKind
import com.dopdop.bopdop.data.model.Collection
import com.dopdop.bopdop.data.model.HomeFeed
import com.dopdop.bopdop.data.model.Lyrics
import com.dopdop.bopdop.data.model.SearchResults
import com.dopdop.bopdop.data.model.Shelf
import com.dopdop.bopdop.data.model.ShelfStyle
import com.dopdop.bopdop.data.model.Source
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.model.matchKey
import com.dopdop.bopdop.data.saavn.SaavnApi
import com.dopdop.bopdop.data.ytm.YtMusic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * One catalogue over two engines: YouTube Music (global catalogue, best
 * discovery) and JioSaavn (320 kbps CDN audio, strong Indian catalogue).
 */
object MusicRepository {

    suspend fun home(): HomeFeed = withContext(Dispatchers.IO) {
        coroutineScope {
            val yt = async { runCatching { YtMusic.home() } }
            val sv = async {
                if (LibraryStore.settings.showSaavnHome) runCatching { SaavnApi.home(LibraryStore.settings.languages) }
                else Result.success(emptyList())
            }
            val y = yt.await(); val s = sv.await()
            if (y.isFailure && s.isFailure) throw y.exceptionOrNull() ?: IOException("Offline")
            val ys = y.getOrDefault(emptyList()).toMutableList()
            val ss = s.getOrDefault(emptyList()).toMutableList()
            // Interleave: lead with YouTube's personal-feeling shelves, weave JioSaavn's editorial ones in.
            val out = mutableListOf<Shelf>()
            ys.firstOrNull { it.style == ShelfStyle.QUICK_PICKS }?.let { out += it; ys.remove(it) }
            ss.firstOrNull { it.style == ShelfStyle.LARGE_CARDS }?.let { out += it; ss.remove(it) }
            while (ys.isNotEmpty() || ss.isNotEmpty()) {
                if (ys.isNotEmpty()) out += ys.removeAt(0)
                if (ys.isNotEmpty()) out += ys.removeAt(0)
                if (ss.isNotEmpty()) out += ss.removeAt(0)
            }
            HomeFeed(out.filter { it.tracks.isNotEmpty() || it.cards.isNotEmpty() })
        }
    }

    suspend fun search(q: String): SearchResults = withContext(Dispatchers.IO) {
        coroutineScope {
            val yt = async { runCatching { YtMusic.search(q) }.getOrDefault(SearchResults()) }
            val sv = async { runCatching { SaavnApi.search(q) }.getOrDefault(SearchResults()) }
            val a = yt.await(); val b = sv.await()
            // Interleave songs by rank and drop the same recording appearing twice.
            val seen = HashSet<String>()
            val tracks = buildList {
                val n = maxOf(a.tracks.size, b.tracks.size)
                for (i in 0 until n) {
                    a.tracks.getOrNull(i)?.let { if (seen.add(it.matchKey())) add(it) }
                    b.tracks.getOrNull(i)?.let { if (seen.add(it.matchKey())) add(it) }
                }
            }
            SearchResults(
                tracks = tracks,
                albums = (a.albums + b.albums).distinctBy { it.title.lowercase() + it.subtitle.lowercase().take(12) }.take(24),
                artists = (a.artists + b.artists).distinctBy { it.title.lowercase() }.take(16),
                playlists = (a.playlists + b.playlists).take(24),
            )
        }
    }

    suspend fun suggestions(q: String): List<String> = withContext(Dispatchers.IO) {
        runCatching { YtMusic.suggestions(q) }.getOrDefault(emptyList())
    }

    suspend fun collection(card: Card): Collection = withContext(Dispatchers.IO) {
        when (card.source) {
            Source.SAAVN -> if (card.kind == CardKind.ALBUM) SaavnApi.album(card.rawId) else SaavnApi.playlist(card.rawId)
            else -> YtMusic.collection(card.kind, card.rawId)
        }
    }

    suspend fun artist(id: String): ArtistPage = withContext(Dispatchers.IO) {
        if (id.startsWith("sv:")) SaavnApi.artist(id.removePrefix("sv:")) else YtMusic.artist(id.removePrefix("yt:"))
    }

    /** Tracks for a radio-station card (JioSaavn artist / featured stations). */
    suspend fun station(card: Card): List<Track> = withContext(Dispatchers.IO) {
        val raw = card.rawId
        val lang = LibraryStore.settings.languages.split(",").firstOrNull().orEmpty()
        when {
            raw.startsWith("artist:") -> SaavnApi.station(artist = raw.removePrefix("artist:"), language = lang)
            raw.startsWith("featured:") -> SaavnApi.station(featured = raw.removePrefix("featured:"), language = lang)
            else -> SaavnApi.station(featured = card.title, language = lang)
        }
    }

    /** "Up next" continuation for a track — feeds autoplay and Start Radio. */
    suspend fun related(t: Track): List<Track> = withContext(Dispatchers.IO) {
        val list = when (t.source) {
            Source.SAAVN -> runCatching { SaavnApi.reco(t.rawId) }.getOrDefault(emptyList())
                .ifEmpty { runCatching { SaavnApi.station(ids = listOf(t.rawId)) }.getOrDefault(emptyList()) }
            else -> runCatching { YtMusic.next(t.rawId) }.getOrDefault(emptyList())
        }
        list.ifEmpty {
            runCatching { YtMusic.searchSongs("${t.artist.split(",").first()} songs") }.getOrDefault(emptyList())
        }.filter { it.id != t.id }
    }

    /** Tracks for shared ids ("yt:…" / "sv:…"), in the order given; unknown ids are dropped. */
    suspend fun tracksByIds(ids: List<String>, onProgress: (Int) -> Unit = {}): List<Track> = withContext(Dispatchers.IO) {
        val sv = ids.filter { it.startsWith("sv:") }.map { it.removePrefix("sv:") }
        val saavn = sv.chunked(40).flatMap { runCatching { SaavnApi.songs(it) }.getOrDefault(emptyList()) }.associateBy { it.id }
        var done = saavn.size
        onProgress(done)
        val gate = Semaphore(6)
        val yt = coroutineScope {
            ids.filter { it.startsWith("yt:") }.map { id ->
                async {
                    gate.withPermit {
                        runCatching { YtMusic.trackInfo(id.removePrefix("yt:")) }.getOrNull()
                            .also { synchronized(this@MusicRepository) { done++; onProgress(done) } }
                    }
                }
            }.awaitAll().filterNotNull().associateBy { it.id }
        }
        ids.mapNotNull { saavn[it] ?: yt[it] }
    }

    /** Global "right now": YouTube Music charts for the user's country. */
    suspend fun charts(): List<Shelf> = withContext(Dispatchers.IO) { YtMusic.charts() }

    suspend fun moods(): List<Pair<String, String>> = withContext(Dispatchers.IO) { YtMusic.moods() }
    suspend fun moodCategory(params: String): List<Shelf> = withContext(Dispatchers.IO) { YtMusic.moodCategory(params) }
    suspend fun explore(): List<Shelf> = withContext(Dispatchers.IO) { YtMusic.explore() }
    suspend fun lyrics(t: Track): Lyrics? = withContext(Dispatchers.IO) { LyricsApi.get(t) }
}
