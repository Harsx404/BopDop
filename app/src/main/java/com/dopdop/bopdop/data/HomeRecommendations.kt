package com.dopdop.bopdop.data

import com.dopdop.bopdop.data.model.Card
import com.dopdop.bopdop.data.model.CardKind
import com.dopdop.bopdop.data.model.HomeFeed
import com.dopdop.bopdop.data.model.Shelf
import com.dopdop.bopdop.data.model.ShelfStyle
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.playback.sameQueueSong
import java.text.Normalizer
import java.util.Locale

/**
 * One song or album appears once on Home. YouTube's shelves claim their items first, in order (they
 * are the personal ones), then JioSaavn's shelves keep only what YouTube didn't already show.
 */
internal fun mergeHomeRecommendations(youtube: List<Shelf>, saavn: List<Shelf>): HomeFeed {
    val tracks = mutableListOf<Track>()
    val cards = mutableListOf<Card>()
    fun unique(shelf: Shelf): Shelf? {
        val remainingTracks = shelf.tracks.filter { track ->
            if (tracks.any { sameHomeSong(it, track) }) false else { tracks += track; true }
        }
        val remainingCards = shelf.cards.filter { card ->
            if (cards.any { sameHomeCard(it, card) }) false else { cards += card; true }
        }
        return when {
            remainingTracks.isEmpty() && remainingCards.isEmpty() -> null
            remainingTracks.size == shelf.tracks.size && remainingCards.size == shelf.cards.size -> shelf
            else -> shelf.copy(tracks = remainingTracks, cards = remainingCards)
        }
    }
    val yt = youtube.mapNotNull(::unique).toMutableList()
    val uniqueSaavn = saavn.mapNotNull(::unique).toMutableList()
    val combined = mutableListOf<Shelf>()
    yt.firstOrNull { it.style == ShelfStyle.QUICK_PICKS }?.let { combined += it; yt.remove(it) }
    uniqueSaavn.firstOrNull { it.style == ShelfStyle.LARGE_CARDS }?.let { combined += it; uniqueSaavn.remove(it) }
    while (yt.isNotEmpty() || uniqueSaavn.isNotEmpty()) {
        if (yt.isNotEmpty()) combined += yt.removeAt(0)
        if (yt.isNotEmpty()) combined += yt.removeAt(0)
        if (uniqueSaavn.isNotEmpty()) combined += uniqueSaavn.removeAt(0)
    }
    return HomeFeed(combined.filter { it.tracks.isNotEmpty() || it.cards.isNotEmpty() })
}

private fun normalizedHomeText(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

private fun homeSongTitle(title: String): String = normalizedHomeText(title.lowercase(Locale.ROOT)
    .replace(Regex("[\\(\\[]\\s*(?:from\\b|feat\\b|ft\\b).*?[\\)\\]]"), " ")
    .replace(Regex("\\b(?:feat|ft)\\.?\\s+.*$"), " ")
    .replace(Regex("\\b(?:official (?:music )?(?:video|audio)|lyrical(?: video)?|lyrics(?: video)?)\\b"), " "))

private fun homeArtists(artists: String): Set<String> = artists
    .split(Regex("[,;&•·]|\\b(?:feat|ft)\\.?\\s+", RegexOption.IGNORE_CASE))
    .map(::normalizedHomeText).filter { it.isNotBlank() }.toSet()

private fun sameHomeSong(a: Track, b: Track): Boolean {
    if (a.id == b.id || sameQueueSong(a, b)) return true
    val title = homeSongTitle(a.title)
    return title.isNotBlank() && title == homeSongTitle(b.title) &&
        homeArtists(a.artist).intersect(homeArtists(b.artist)).isNotEmpty()
}

private fun homeAlbumCredits(subtitle: String): Set<String> = homeArtists(subtitle).filterNot {
    it in setOf("album", "single", "ep", "compilation", "soundtrack") ||
        it.matches(Regex("\\d{4}|\\d+ songs?|\\d+ tracks?"))
}.toSet()

private fun sameHomeCard(a: Card, b: Card): Boolean {
    if (a.kind != b.kind) return false
    if (a.id == b.id) return true
    val title = normalizedHomeText(a.title)
    if (title.isBlank() || title != normalizedHomeText(b.title)) return false
    if (a.kind != CardKind.ALBUM) return true
    val firstCredits = homeAlbumCredits(a.subtitle)
    val secondCredits = homeAlbumCredits(b.subtitle)
    return firstCredits.isEmpty() || secondCredits.isEmpty() || firstCredits.intersect(secondCredits).isNotEmpty()
}
