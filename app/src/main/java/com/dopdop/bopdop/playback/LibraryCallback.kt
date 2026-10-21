package com.dopdop.bopdop.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.dopdop.bopdop.data.DailyMixes
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.local.LocalMusic
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.ytm.YtMusic
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future

/**
 * What Android Auto (and any media browser) can browse: daily mixes, liked songs, recently
 * played, your playlists and music on the phone. Picking a song queues the list it sits in.
 */
@OptIn(UnstableApi::class)
internal class LibraryCallback(private val context: Context) : MediaLibrarySession.Callback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
        NotificationButtons.connect(session, controller)

    override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: androidx.media3.session.SessionCommand, args: Bundle): ListenableFuture<androidx.media3.session.SessionResult> =
        Futures.immediateFuture(NotificationButtons.result(NotificationButtons.handle(session.player, customCommand)))

    override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "BopDop", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED), params))

    override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
        val t = find(mediaId)
        if (t != null) LibraryResult.ofItem(t.toMediaItem(), null) else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
    }

    override fun onGetChildren(
        session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
        val items: List<MediaItem> = when {
            parentId == ROOT -> listOf(
                folder(MIXES, "Daily mixes", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                folder(LIKED, "Liked songs", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
                folder(RECENT, "Recently played", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
                folder(PLAYLISTS, "Your playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                folder(DEVICE, "On this phone", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            )
            parentId == MIXES -> { DailyMixes.refresh(); DailyMixes.mixes.value.map { folder("$MIX_PREFIX${it.id}", it.title, MediaMetadata.MEDIA_TYPE_PLAYLIST, it.subtitle, it.artworks.firstOrNull()) } }
            parentId == PLAYLISTS -> LibraryStore.state.value.playlists.map { folder("$PL_PREFIX${it.id}", it.name, MediaMetadata.MEDIA_TYPE_PLAYLIST, "${it.tracks.size} songs", it.artwork) }
            else -> tracksIn(parentId).map { it.toMediaItem() }
        }
        val from = (page * pageSize).coerceAtMost(items.size)
        LibraryResult.ofItemList(ImmutableList.copyOf(items.subList(from, minOf(items.size, from + pageSize))), params)
    }

    override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> =
        scope.future { mediaItems.flatMap { resolve(it) }.toMutableList() }

    /** One song picked from a list → play that list from that song, like tapping it in the app. */
    override fun onSetMediaItems(
        mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
        val single = mediaItems.singleOrNull()
        val parent = single?.mediaId?.let { lastParent[it] }
        val list = parent?.let { tracksIn(it) }.orEmpty()
        val at = list.indexOfFirst { it.id == single?.mediaId }
        if (single != null && at >= 0) MediaSession.MediaItemsWithStartPosition(list.map { it.toMediaItem() }, at, startPositionMs)
        else MediaSession.MediaItemsWithStartPosition(mediaItems.flatMap { resolve(it) }, startIndex, startPositionMs)
    }

    /** Play pressed on a headset, the widget or the car with nothing queued: pick up where you left off. */
    override fun onPlaybackResumption(mediaSession: MediaSession, controller: MediaSession.ControllerInfo): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        // The exact queue, song and second it was left at; recent songs only when nothing was saved.
        QueueStore.load()?.let { q ->
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(q.tracks.map { it.toMediaItem() }, q.index, q.positionMs))
        }
        val recent = LibraryStore.state.value.history.take(20)
        return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(recent.map { it.toMediaItem() }, 0, 0))
    }

    override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> =
        scope.future {
            val n = runCatching { search(query) }.getOrDefault(emptyList()).size
            session.notifySearchResultChanged(browser, query, n, params)
            LibraryResult.ofVoid()
        }

    override fun onGetSearchResult(
        session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, page: Int, pageSize: Int, params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
        val all = runCatching { search(query) }.getOrDefault(emptyList()).map { it.toMediaItem() }
        val from = (page * pageSize).coerceAtMost(all.size)
        LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, minOf(all.size, from + pageSize))), params)
    }

    // --- helpers ---------------------------------------------------------------------------

    /** Which folder each song was last listed in, so a pick can queue its neighbours. */
    private val lastParent = HashMap<String, String>()
    private val searched = HashMap<String, Track>()

    private suspend fun tracksIn(parentId: String): List<Track> {
        val lib = LibraryStore.state.value
        val list = when {
            parentId == LIKED -> lib.liked
            parentId == RECENT -> lib.history.take(100)
            parentId == DEVICE -> { LocalMusic.refresh(context); LocalMusic.tracks.value }
            parentId.startsWith(MIX_PREFIX) -> DailyMixes.byId(parentId.removePrefix(MIX_PREFIX))?.tracks.orEmpty()
            parentId.startsWith(PL_PREFIX) -> lib.playlists.firstOrNull { it.id == parentId.removePrefix(PL_PREFIX) }?.tracks.orEmpty()
            else -> emptyList()
        }
        list.forEach { lastParent[it.id] = parentId }
        return list
    }

    private suspend fun search(query: String): List<Track> = YtMusic.searchSongs(query).take(20).onEach { searched[it.id] = it }

    private fun find(id: String): Track? {
        val lib = LibraryStore.state.value
        return searched[id] ?: (lib.liked + lib.history + lib.playlists.flatMap { it.tracks } + DailyMixes.mixes.value.flatMap { it.tracks } + LocalMusic.tracks.value)
            .firstOrNull { it.id == id }
    }

    /** Items from controllers lose their uri; a voice request may arrive as a bare search query. */
    private suspend fun resolve(item: MediaItem): List<MediaItem> {
        if (item.localConfiguration != null) return listOf(item.withPlaybackUri())
        find(item.mediaId)?.let { return listOf(it.toMediaItem()) }
        item.withPlaybackUri().takeIf { it.localConfiguration != null }?.let { return listOf(it) }
        val query = item.requestMetadata.searchQuery ?: return emptyList()
        return search(query).map { it.toMediaItem() }
    }

    private fun folder(id: String, title: String, type: Int, subtitle: String? = null, art: String? = null) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle).setIsBrowsable(true).setIsPlayable(false).setMediaType(type)
                .apply { art?.takeIf { it.isNotBlank() }?.let { setArtworkUri(Uri.parse(it)) } }
                .setExtras(Bundle())
                .build(),
        ).build()

    private companion object {
        const val ROOT = "root"
        const val MIXES = "mixes"
        const val LIKED = "liked"
        const val RECENT = "recent"
        const val PLAYLISTS = "playlists"
        const val DEVICE = "device"
        const val MIX_PREFIX = "mix:"
        const val PL_PREFIX = "pl:"
    }
}
