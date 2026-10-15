package com.dopdop.bopdop.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.data.MusicRepository
import com.dopdop.bopdop.data.model.songs
import com.dopdop.bopdop.data.share.SharePayload
import com.dopdop.bopdop.ui.LocalApp
import com.dopdop.bopdop.ui.SeeAllHolder
import com.dopdop.bopdop.ui.components.Load
import com.dopdop.bopdop.ui.components.rememberLoad
import com.dopdop.bopdop.ui.screens.Collage
import com.dopdop.bopdop.ui.screens.CollectionLayout

/** A playlist someone shared by code / link: resolve the songs, play it, keep it. */
@Composable
fun ImportScreen(key: String) {
    val app = LocalApp.current
    val p = SeeAllHolder.get(key)?.second as? SharePayload.Playlist ?: run { app.nav.popBackStack(); return }
    var found by remember { mutableIntStateOf(0) }
    val load = rememberLoad("import:${p.ids.hashCode()}", maxAgeMs = 60 * 60_000L) {
        MusicRepository.tracksByIds(p.ids) { found = it }.also { if (it.isEmpty()) error("None of these songs could be found") }
    }
    val tracks = (load.state as? Load.Ready)?.value
    var savedId by remember { mutableStateOf<String?>(null) }
    CollectionLayout(
        title = p.name,
        subtitle = "Shared with you",
        meta = when {
            tracks == null -> "Finding songs… ${found.coerceAtMost(p.ids.size)} / ${p.ids.size}"
            p.total > p.ids.size -> "${songs(tracks.size)} · first ${p.ids.size} of ${p.total}"
            else -> songs(tracks.size)
        },
        artUrl = tracks?.firstOrNull()?.artwork,
        art = tracks?.let { ts -> { m -> Collage(ts.map { it.artwork }.distinct(), m) } },
        tracks = tracks,
        context = p.name,
        numbered = false,
        saved = savedId != null,
        onSave = {
            val ts = tracks
            when {
                savedId != null -> app.openLocal(savedId!!)
                ts.isNullOrEmpty() -> app.toast("Still finding songs…")
                else -> { savedId = LibraryStore.createPlaylist(p.name, ts).id; app.toast("Saved “${p.name}” to your Library") }
            }
        },
        loading = load.state is Load.Loading,
        error = (load.state as? Load.Failed)?.message,
        onRetry = load.retry,
    )
}
