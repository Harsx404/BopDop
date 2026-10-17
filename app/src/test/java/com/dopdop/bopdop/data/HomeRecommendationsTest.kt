package com.dopdop.bopdop.data

import com.dopdop.bopdop.data.model.Card
import com.dopdop.bopdop.data.model.CardKind
import com.dopdop.bopdop.data.model.Shelf
import com.dopdop.bopdop.data.model.ShelfStyle
import com.dopdop.bopdop.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRecommendationsTest {
    private fun song(id: String, title: String = "Song", artist: String = "Artist") = Track(id, title, artist)
    private fun album(id: String, title: String = "Album", artist: String = "Artist") = Card(id, CardKind.ALBUM, title, artist)

    @Test fun youtubeWinsEvenWhenItsMatchingRecommendationIsInALaterShelf() {
        val youtube = Shelf("For you", tracks = listOf(song("yt:song")), cards = listOf(album("yt:album")))
        val fresh = album("sv:fresh", "Fresh album")
        val saavn = Shelf("New releases", tracks = listOf(song("sv:song")),
            cards = listOf(album("sv:album"), fresh), style = ShelfStyle.LARGE_CARDS)

        val feed = mergeHomeRecommendations(listOf(youtube), listOf(saavn))

        assertEquals(listOf(fresh), feed.shelves.first().cards)
        assertTrue(feed.shelves.first().tracks.isEmpty())
        assertSame(youtube, feed.shelves.last())
    }

    @Test fun removesEmptyDuplicateShelvesButKeepsTheUniqueItemsAndTheirOrder() {
        val youtube = Shelf("Quick picks", tracks = listOf(song("yt:song")), style = ShelfStyle.QUICK_PICKS)
        val first = song("sv:first", "First")
        val second = song("sv:second", "Second")
        val saavn = listOf(
            Shelf("Only duplicates", tracks = listOf(song("sv:song"))),
            Shelf("Trending", "JioSaavn", tracks = listOf(first, song("sv:duplicate"), second)),
        )

        val feed = mergeHomeRecommendations(listOf(youtube), saavn)

        assertEquals(listOf("Quick picks", "Trending"), feed.shelves.map { it.title })
        assertEquals(listOf(first, second), feed.shelves.last().tracks)
        assertEquals("JioSaavn", feed.shelves.last().subtitle)
    }

    @Test fun removesSaavnRepeatsWithinAndAcrossShelvesWhenYoutubeIsUnavailable() {
        val first = song("sv:one")
        val firstAlbum = album("sv:album")
        val unique = song("sv:two", "Another song")
        val saavn = listOf(
            Shelf("Trending", tracks = listOf(first, first), cards = listOf(firstAlbum)),
            Shelf("Editorial", tracks = listOf(song("sv:copy"), unique), cards = listOf(firstAlbum)),
        )

        val feed = mergeHomeRecommendations(emptyList(), saavn)

        assertEquals(listOf(first, unique), feed.shelves.flatMap { it.tracks })
        assertEquals(listOf(firstAlbum), feed.shelves.flatMap { it.cards })
    }

    @Test fun matchesFormattingAndDifferentArtistCreditOrder() {
        val youtube = song("yt:song", "  SONG! (Official Audio)", "Artist One & Artist Two")
        val saavn = song("sv:song", "Song (From \"Film\")", "Artist Two, Artist One")

        val feed = mergeHomeRecommendations(listOf(Shelf("YouTube", tracks = listOf(youtube))),
            listOf(Shelf("Saavn", tracks = listOf(saavn))))

        assertEquals(listOf(youtube), feed.shelves.flatMap { it.tracks })
    }

    @Test fun keepsDifferentArtistsAndDifferentRecordings() {
        val youtube = Shelf("YouTube", tracks = listOf(song("yt:song")), cards = listOf(album("yt:album")))
        val otherArtist = song("sv:other", artist = "Another artist")
        val live = song("sv:live", "Song (Live)")
        val remix = song("sv:remix", "Song - Remix")
        val otherAlbum = album("sv:other-album", artist = "Another artist")

        val feed = mergeHomeRecommendations(listOf(youtube), listOf(
            Shelf("Saavn", tracks = listOf(otherArtist, live, remix), cards = listOf(otherAlbum))))

        assertEquals(listOf(otherArtist, live, remix), feed.shelves.last().tracks)
        assertEquals(listOf(otherAlbum), feed.shelves.last().cards)
    }

    @Test fun removesMatchingArtistsPlaylistsAndAlbumsWithDecoratedBylines() {
        val youtube = Shelf("YouTube", cards = listOf(
            Card("yt:artist", CardKind.ARTIST, "Artist", "10M subscribers"),
            Card("yt:playlist", CardKind.PLAYLIST, "Hindi Hits!", "YouTube Music"),
            album("yt:album", artist = "Album · Artist · 2026"),
        ))
        val saavn = Shelf("Saavn", cards = listOf(
            Card("sv:artist", CardKind.ARTIST, "ARTIST"),
            Card("sv:playlist", CardKind.PLAYLIST, "Hindi Hits", "50 songs"),
            album("sv:album"),
        ))

        assertEquals(listOf(youtube), mergeHomeRecommendations(listOf(youtube), listOf(saavn)).shelves)
    }

    @Test fun sameNameDoesNotMakeDifferentKindsOfCollectionDuplicates() {
        val youtube = Shelf("YouTube", cards = listOf(album("yt:album", "Shared title")))
        val playlist = Card("sv:playlist", CardKind.PLAYLIST, "Shared title")

        val feed = mergeHomeRecommendations(listOf(youtube), listOf(Shelf("Saavn", cards = listOf(playlist))))

        assertEquals(listOf(playlist), feed.shelves.last().cards)
    }

    @Test fun missingArtistMetadataDoesNotCollapseUnidentifiedSongs() {
        val youtube = Shelf("YouTube", tracks = listOf(song("yt:song", artist = "")))
        val saavn = song("sv:song", artist = "")

        val feed = mergeHomeRecommendations(listOf(youtube), listOf(Shelf("Saavn", tracks = listOf(saavn))))

        assertEquals(2, feed.shelves.flatMap { it.tracks }.size)
    }

    @Test fun aSongShowsOnceEvenWhenTwoYoutubeShelvesBothSuggestIt() {
        val first = song("yt:a", "Sahiba", "Jasleen Royal")
        val other = song("yt:b", "Other", "Someone")
        val youtube = listOf(
            Shelf("Quick picks", tracks = listOf(first), style = ShelfStyle.QUICK_PICKS),
            Shelf("Mixed for you", tracks = listOf(song("yt:c", "Sahiba", "Jasleen Royal, Stebin Ben"), other)),
        )

        val feed = mergeHomeRecommendations(youtube, emptyList())

        assertEquals(listOf(first, other), feed.shelves.flatMap { it.tracks })
    }

    @Test fun filmCopyOnSaavnMatchesTheSingleOnYoutube() {
        val youtube = song("yt:lutt", "Lutt Le Gaya", "Simran Choudhary")
        val saavn = song("sv:lutt", "Lutt Le Gaya (From \"Dhurandhar\")", "Shashwat Sachdev, Simran Choudhary")

        val feed = mergeHomeRecommendations(listOf(Shelf("YouTube", tracks = listOf(youtube))), listOf(Shelf("Saavn", tracks = listOf(saavn))))

        assertEquals(listOf(youtube), feed.shelves.flatMap { it.tracks })
    }
}
