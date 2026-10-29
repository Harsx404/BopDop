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
import com.dopdop.bopdop.data.model.Source
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.model.matchKey
import com.dopdop.bopdop.data.saavn.SaavnApi
import com.dopdop.bopdop.data.ytm.YtMusic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * One catalogue over two engines: YouTube Music (global catalogue, best
 * discovery) and JioSaavn (320 kbps CDN audio, strong Indian catalogue).
 */
object MusicRepository {

    /**
     * Home is YouTube Music's own feed (yours when signed in); [mood] is one of its chips. It arrives
     * as it loads: the first page, then each continuation, then Spotify's shelves when they come.
     * The finished All feed is kept on disk so the next launch has something to show at once.
     */
    fun home(mood: String? = null): Flow<HomeFeed> = channelFlow {
        val lock = Mutex()
        var yt = emptyList<Shelf>(); var chips = emptyList<Pair<String, String>>(); var sp = emptyList<Shelf>()
        var shown: HomeFeed? = null
        suspend fun publish() = lock.withLock {
            if (yt.isEmpty()) return@withLock
            val feed = mergeHomeRecommendations(yt, emptyList())
            // After YouTube Music's first two rows (Quick picks and the next), then YouTube's rows go on.
            feed.copy(shelves = if (sp.isEmpty()) feed.shelves else feed.shelves.take(2) + sp + feed.shelves.drop(2), chips = chips)
                .also { shown = it; send(it) }
        }
        // Signed in to Spotify: its personal shelves join in under All (never under a mood). Home
        // never waits on them; they slot in whenever they answer.
        val spotify = if (mood == null && com.dopdop.bopdop.data.spotify.SpotifyCatalog.available) launch {
            withTimeoutOrNull(10_000) { runCatching { com.dopdop.bopdop.data.spotify.SpotifyCatalog.homeShelves() }.getOrNull() }
                ?.takeIf { it.isNotEmpty() }?.let { lock.withLock { sp = it }; publish() }
        } else null
        YtMusic.homeFeed(mood) { shelves, c -> lock.withLock { yt = shelves; chips = c }; publish() }
        spotify?.join()
        if (mood == null) lock.withLock { shown }?.let { CatalogCache.saveHome(it) }
    }.flowOn(Dispatchers.IO)

    /** The All feed from the last launch, for showing while a fresh one loads. It was saved already merged. */
    suspend fun lastHome(): HomeFeed? = CatalogCache.lastHome()

    /** [lastHome] without touching the disk: null until it has been read (it's warmed at launch). */
    fun lastHomeNow(): HomeFeed? = CatalogCache.lastHomeNow()

    suspend fun search(q: String): SearchResults = withContext(Dispatchers.IO) {
        coroutineScope {
            // The catalogue is YouTube Music's; JioSaavn only supplies Hi-Res audio and lyrics behind the scenes.
            val yt = async { runCatching { YtMusic.search(q) }.getOrDefault(SearchResults()) }
            val sv = async { SearchResults() }
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
                podcasts = a.podcasts.take(16),
            )
        }
    }

    /** Spotify's catalogue for the same query (signed in to Spotify only). */
    suspend fun searchSpotify(q: String): SearchResults = withContext(Dispatchers.IO) {
        com.dopdop.bopdop.data.spotify.SpotifyCatalog.search(q)
    }

    suspend fun suggestions(q: String): List<String> = withContext(Dispatchers.IO) {
        runCatching { YtMusic.suggestions(q) }.getOrDefault(emptyList())
    }

    suspend fun collection(card: Card): Collection = withContext(Dispatchers.IO) {
        val load: suspend () -> Collection = {
            when (card.source) {
                Source.SAAVN -> if (card.kind == CardKind.ALBUM) SaavnApi.album(card.rawId) else SaavnApi.playlist(card.rawId)
                Source.SPOTIFY -> card.id.removePrefix("sp:").let { rest ->
                    if (rest.startsWith("album:")) com.dopdop.bopdop.data.spotify.SpotifyCatalog.album(rest.removePrefix("album:"))
                    else com.dopdop.bopdop.data.spotify.SpotifyCatalog.playlist(rest.removePrefix("playlist:"))
                }
                else -> YtMusic.collection(card.kind, card.rawId)
            }
        }
        // Your own YouTube Music likes change constantly and belong to the account — never cached.
        if (card.rawId == "LM") load() else CatalogCache.collection(card.id, load)
    }

    suspend fun artist(id: String): ArtistPage = withContext(Dispatchers.IO) {
        when {
            id.startsWith("sv:") -> SaavnApi.artist(id.removePrefix("sv:"))
            id.startsWith("sp:artist:") -> com.dopdop.bopdop.data.spotify.SpotifyCatalog.artist(id.removePrefix("sp:artist:"))
            else -> YtMusic.artist(id.removePrefix("yt:"))
        }
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

    /**
     * "Up next" continuation for a track — feeds autoplay, Start Radio, Daily Mixes and Discover.
     * [alsoLike] are a few more songs from the same session: their radios are woven in (two from the
     * main song's for each of theirs), so autoplay follows where the listening has gone without
     * drifting off. The result is ranked to your taste (see [Recommender] and [Taste]).
     */
    suspend fun related(t: Track, alsoLike: List<Track> = emptyList()): List<Track> = withContext(Dispatchers.IO) {
        val extras = alsoLike.filter { a -> a.id != t.id && !com.dopdop.bopdop.data.model.SongVersion.sameSong(a, t) }.take(2)
        val pools = coroutineScope { (listOf(t) + extras).map { async { radioPool(it) } }.awaitAll() }
        val main = pools.first()
        val others = pools.drop(1)
        val woven = buildList {
            val m = main.iterator(); val o = others.map { it.iterator() }
            while (m.hasNext() || o.any { it.hasNext() }) {
                repeat(2) { if (m.hasNext()) add(m.next()) }
                o.forEach { if (it.hasNext()) add(it.next()) }
            }
        }.filter { c -> extras.none { com.dopdop.bopdop.data.model.SongVersion.sameSong(it, c) } }
        val lib = LibraryStore.state.value
        Recommender.rank(t, woven, lib.liked, lib.history, Taste.from(PlayStats.state.value, lib.liked), LibraryStore::isDisliked)
    }

    /**
     * The best copy of [t] to play when you pick it from elsewhere (a Short's song): JioSaavn's 320 kbps
     * release of the same song when Hi-Res is on and one exists, else [t] itself.
     */
    suspend fun bestCopy(t: Track): Track = withContext(Dispatchers.IO) {
        if (t.source != Source.YTM || !LibraryStore.settings.hiRes || t.isPodcast) return@withContext t
        withTimeoutOrNull(4_000) { runCatching { SaavnApi.fullCopy(t) }.getOrNull() } ?: t
    }

    /** YouTube Music's radio for a song; JioSaavn and on-phone songs use their YouTube Music copy, else the artist's songs. */
    private suspend fun radioPool(t: Track): List<Track> {
        val seedId = if (t.source == Source.YTM) t.rawId else runCatching {
            YtMusic.searchSongs("${t.title} ${t.artist.split(",").first()}").firstOrNull { com.dopdop.bopdop.data.model.SongVersion.sameRecording(t, it) }?.rawId
        }.getOrNull()
        return seedId?.let { runCatching { YtMusic.next(it) }.getOrDefault(emptyList()) }.orEmpty()
            .ifEmpty { runCatching { YtMusic.searchSongs("${t.artist.split(",").first()} songs") }.getOrDefault(emptyList()) }
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
    /** The podcast catalogue: YouTube Music's shelves and JioSaavn's trending and top shows, whichever answer. */
    suspend fun podcasts(): List<Shelf> = withContext(Dispatchers.IO) {
        val yt = async { runCatching { YtMusic.podcasts() } }
        val sv = async { runCatching { com.dopdop.bopdop.data.saavn.SaavnApi.podcasts() } }
        val (a, b) = yt.await() to sv.await()
        // YouTube's popular shows lead; JioSaavn's Indian shows come right after.
        val ytShelves = a.getOrDefault(emptyList())
        val all = ytShelves.take(1) + b.getOrDefault(emptyList()) + ytShelves.drop(1)
        if (all.isEmpty()) throw (a.exceptionOrNull() ?: b.exceptionOrNull() ?: java.io.IOException("No podcasts right now"))
        all
    }
    suspend fun podcastShow(id: String): com.dopdop.bopdop.data.model.PodcastShow = withContext(Dispatchers.IO) {
        if (id.startsWith(com.dopdop.bopdop.data.model.SAAVN_SHOW)) com.dopdop.bopdop.data.saavn.SaavnApi.podcastShow(id.removePrefix(com.dopdop.bopdop.data.model.SAAVN_SHOW))
        else YtMusic.podcastShow(id.removePrefix("yt:"))
    }
    suspend fun lyrics(t: Track): Lyrics? = withContext(Dispatchers.IO) { LyricsApi.get(t) }
}
