package com.dopdop.bopdop.data.spotify

import android.util.Log
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.model.SongVersion
import com.dopdop.bopdop.data.model.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Where an import is: which playlist, how far through its songs, and how many playlists are done. */
data class ImportProgress(val playlist: String, val songsDone: Int, val songsTotal: Int, val listsDone: Int, val listsTotal: Int, val finished: Boolean = false, val summary: String = "")

/**
 * Copies Spotify playlists into BopDop's own library. Each song is matched to the same recording on
 * YouTube Music (see [SpotifyMatch]); songs with no match are left out and counted. A playlist that
 * already exists here under the same name gets only the songs it doesn't have yet, so importing
 * twice never duplicates anything.
 */
object SpotifyImport {
    private val _progress = MutableStateFlow<ImportProgress?>(null)
    val progress: StateFlow<ImportProgress?> = _progress.asStateFlow()

    /** Playlists you made yourself (owned by your account), plus Liked Songs. */
    fun yours(all: List<SpotifyPlaylist>, me: String?): List<SpotifyPlaylist> =
        all.filter { it.id == SpotifyApi.LIKED || (me != null && it.owner.equals(me, ignoreCase = true)) }

    suspend fun run(lists: List<SpotifyPlaylist>) {
        if (_progress.value?.finished == false) return
        var added = 0; var missing = 0; var created = 0
        lists.forEachIndexed { i, p ->
            _progress.value = ImportProgress(p.name, 0, 0, i, lists.size)
            val songs = runCatching { SpotifyApi.tracks(p.id) }.onFailure { Log.w("SpotifyImport", "${p.name}: ${it.message}") }.getOrNull() ?: return@forEachIndexed
            var done = 0
            val gate = Semaphore(6)
            val matched: List<Track?> = coroutineScope {
                songs.map { s ->
                    async {
                        gate.withPermit {
                            SpotifyMatch.match(s).also { synchronized(this@SpotifyImport) { done++; _progress.value = ImportProgress(p.name, done, songs.size, i, lists.size) } }
                        }
                    }
                }.awaitAll()
            }
            val found = matched.filterNotNull().distinctBy { it.id }
            missing += songs.size - found.size
            val existing = LibraryStore.state.value.playlists.firstOrNull { it.name.equals(p.name, ignoreCase = true) }
            if (existing == null) {
                if (found.isNotEmpty()) { LibraryStore.createPlaylist(p.name, found); created++; added += found.size }
            } else {
                // Copies of a song from another catalogue count as already there.
                val fresh = found.filter { t -> existing.tracks.none { SongVersion.sameRecording(it, t) } }
                LibraryStore.addToPlaylist(existing.id, fresh)
                added += fresh.size
            }
            Log.i("SpotifyImport", "${p.name}: ${found.size}/${songs.size} matched")
            SpotifyMatch.save()
        }
        _progress.value = ImportProgress("", 0, 0, lists.size, lists.size, finished = true,
            summary = "Imported ${lists.size} playlists: $added songs added" + (if (created > 0) ", $created new playlists" else "") +
                (if (missing > 0) ". $missing songs aren't on YouTube Music." else "."))
    }

    fun dismiss() { if (_progress.value?.finished == true) _progress.value = null }

    private const val EVERY_MS = 12 * 3600_000L

    /**
     * Automatic import: right after connecting ([force]), then whenever the app starts and the
     * last sync is over 12 hours old. Only your own playlists and Liked Songs; new songs are added,
     * nothing is removed.
     */
    suspend fun auto(context: android.content.Context, force: Boolean = false) {
        if (!SpotifyAccount.signedIn || !LibraryStore.settings.spotifyAutoImport) return
        val prefs = context.getSharedPreferences("spotify_sync", android.content.Context.MODE_PRIVATE)
        if (!force && System.currentTimeMillis() - prefs.getLong("last", 0) < EVERY_MS) return
        val me = SpotifyAccount.profile.value?.name?.takeIf { it != "Spotify" } ?: SpotifyApi.profile()?.also(SpotifyAccount::setProfile)?.name
        val lists = runCatching { yours(SpotifyApi.playlists(), me) }.onFailure { Log.w("SpotifyImport", "auto: ${it.message}") }.getOrNull() ?: return
        run(lists)
        SpotifyMatch.save()
        prefs.edit().putLong("last", System.currentTimeMillis()).apply()
    }
}
