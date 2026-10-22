package com.dopdop.bopdop.data.model

import java.text.Normalizer
import kotlin.math.abs

/** A title split into the song's own name and what kind of version it is. */
data class SongTitle(val core: String, val tags: Set<String>)

/**
 * One reading of song titles for every place that asks "is this the same song?": Hi-Res matching,
 * search merging, "Not for me", music videos, playlist import, the queue and radio.
 *
 * "Alfaaz - Vocals", "Kesariya (Lofi Flip)", "Tum Hi Ho (Slowed + Reverb)", "Naina | Unplugged"
 * and "Kesariya lofi" are versions: same core name, different tags. Credits and packaging —
 * "(From "Film")", "feat. X", "Remastered", "Radio Edit", "Official Video", "Lyrical" — are not.
 */
object SongVersion {
    /** Version words → the tag they stand for. Order matters: longer phrases first. */
    private val TAGS = listOf(
        "lo ?fi" to "lofi",
        "slowed" to "slowed",
        "reverb" to "reverb",
        "sped ?up|sped|speed ?up|spedup" to "sped",
        "nightcore" to "nightcore",
        "8d(?: audio)?" to "8d",
        "unplugged" to "unplugged",
        "acoustic" to "acoustic",
        "live" to "live",
        "remix|rmx|reprise mix|club mix|dj mix" to "remix",
        "cover" to "cover",
        "karaoke" to "karaoke",
        "instrumental|beat only|music only" to "instrumental",
        "vocals?(?: only)?|acapella|a cappella|acappella" to "vocals",
        "reprise" to "reprise",
        "mashup|mash up" to "mashup",
        "extended(?: mix| version)?" to "extended",
        "female(?: version| cover)?" to "female",
        "male(?: version| cover)?" to "male",
        "sad(?: version)?" to "sad",
        "duet" to "duet",
    ).map { (re, tag) -> Regex("\\b(?:$re)\\b") to tag }

    /** A generic mix/flip/edit is a remix only when nothing more specific (Lofi Mix, Lofi Flip) named it. */
    private val GENERIC_MIX = Regex("\\b(?:mix|flip|rework|bootleg|vip)\\b")

    /** Bare words at the end of a title that only ever mean a version ("Kesariya lofi"). */
    private val TRAILING = setOf("lofi", "slowed", "reverb", "sped", "nightcore", "8d", "unplugged", "remix", "mashup")

    private val SEPARATOR = Regex("\\s+[-–—|/]{1,2}\\s+|\\s*\\|\\s*")
    private val BRACKETS = Regex("[(\\[{]([^)\\]}]*)[)\\]}]")
    private val FEAT = Regex("\\b(?:feat|ft|featuring)\\b\\.?.*$")
    private val CREDIT = Regex("^(?:from|feat|ft|featuring|with|prod|produced by|music by)\\b")

    // Parsing takes a few dozen regexes, and queues compare every song with every other one:
    // each title and artist line is read once and remembered.
    private val titles = java.util.concurrent.ConcurrentHashMap<String, SongTitle>()
    private val artistSets = java.util.concurrent.ConcurrentHashMap<String, Set<String>>()
    private const val REMEMBER = 5000

    fun parse(title: String): SongTitle = titles[title] ?: read(title).also {
        if (titles.size > REMEMBER) titles.clear()
        titles[title] = it
    }

    private fun read(title: String): SongTitle {
        val text = fold(title)
        val segments = BRACKETS.findAll(text).map { it.groupValues[1] }.toMutableList()
        val rest = BRACKETS.replace(text, " ").split(SEPARATOR)
        segments += rest.drop(1)
        var core = clean(FEAT.replace(rest.first(), " "))
        val tags = sortedSetOf<String>()
        for (s in segments) tags += tagsIn(clean(s))
        // "Kesariya lofi", "Tum Hi Ho slowed reverb": version words written straight onto the name.
        while (true) {
            val words = core.split(' ')
            if (words.size < 2) break
            val tag = tagsIn(words.last()).singleOrNull()?.takeIf { it in TRAILING } ?: break
            tags += tag
            core = words.dropLast(1).joinToString(" ")
        }
        return SongTitle(core, tags)
    }

    private fun tagsIn(segment: String): Set<String> {
        // Credits name films and people, not versions: (From "Live Love Laugh"), (feat. Lofi Girl).
        if (segment.isEmpty() || CREDIT.containsMatchIn(segment)) return emptySet()
        val found = TAGS.filter { (re, _) -> re.containsMatchIn(segment) }.mapTo(HashSet()) { it.second }
        // "Slowed + Reverb Mix" is a slowed/reverb version, not a remix.
        if (found.isEmpty() && GENERIC_MIX.containsMatchIn(segment)) found += "remix"
        return found
    }

    /** Lowercase, accents off, punctuation to spaces (keeping the separators parse() splits on). */
    private fun fold(s: String) = Normalizer.normalize(s, Normalizer.Form.NFKD)
        .replace(Regex("\\p{Mn}+"), "").lowercase()

    private fun clean(s: String) = s.replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    fun artists(s: String): Set<String> = artistSets[s] ?: readArtists(s).also {
        if (artistSets.size > REMEMBER) artistSets.clear()
        artistSets[s] = it
    }

    private fun readArtists(s: String): Set<String> = fold(s)
        .split(Regex(",|&|;|/|\\band\\b|\\bx\\b|\\bwith\\b|\\b(?:feat|ft|featuring)\\b\\.?"))
        .map(::clean).filter { it.isNotEmpty() }.toSet()

    private fun shareArtist(a: Track, b: Track) = (artists(a.artist) intersect artists(b.artist)).isNotEmpty()

    /**
     * The title as a song name: uploads like "Prateek Kuhad - Co2 (Live…)" put the artist first,
     * which would otherwise be read as the song's name.
     */
    fun titleOf(t: Track): SongTitle {
        val parsed = parse(t.title)
        val names = artists(t.artist)
        if (parsed.core !in names) return parsed
        val rest = fold(t.title).split(SEPARATOR).drop(1).joinToString(" - ")
        return if (rest.isBlank()) parsed else parse(rest)
    }

    /**
     * One recording credited under two names ("girl in red" and her real name "Marie Ulven"):
     * the same album and lengths within [LENGTH_MATCH] s. Two different songs that only share a
     * title don't share an album too.
     */
    private fun sameRelease(a: Track, b: Track) =
        a.durationSec > 0 && b.durationSec > 0 && abs(a.durationSec - b.durationSec) <= LENGTH_MATCH &&
            a.album.isNotBlank() && fold(a.album).trim() == fold(b.album).trim()
    private const val LENGTH_MATCH = 2

    /** Version words that can trail a name inside a longer title ("Co2 Live Across The Tour"). */
    private val VERSION_WORDS = setOf("live", "acoustic", "unplugged", "remix", "lofi", "slowed", "reprise", "cover", "instrumental", "karaoke", "version")

    /** "co2 live across the tour" is a version of "co2". */
    private fun versionOf(longer: String, core: String) =
        longer.length > core.length && longer.startsWith("$core ") && longer.removePrefix("$core ").substringBefore(' ') in VERSION_WORDS

    /**
     * The same recording from any catalogue: same core name, the same version tags, an artist in
     * common and lengths within [toleranceSec]. "Alfaaz" and "Alfaaz - Vocals" are not.
     */
    fun sameRecording(a: Track, b: Track, toleranceSec: Int = 25): Boolean {
        if (a.id == b.id) return true
        val x = titleOf(a); val y = titleOf(b)
        if (x.core.isEmpty() || x != y) return false
        if (!shareArtist(a, b)) return sameRelease(a, b)
        return a.durationSec <= 0 || b.durationSec <= 0 || abs(a.durationSec - b.durationSec) <= toleranceSec
    }

    /** The same song in any version: "Alfaaz", "Alfaaz (Lofi)" and "Alfaaz - Vocals" by the same artist. */
    fun sameSong(a: Track, b: Track): Boolean {
        if (a.id == b.id) return true
        val x = titleOf(a).core; val y = titleOf(b).core
        if (x.isEmpty() || y.isEmpty()) return false
        val sameName = x == y || versionOf(x, y) || versionOf(y, x)
        return sameName && (shareArtist(a, b) || (x == y && sameRelease(a, b)))
    }

    /** "core|tags" — equal for copies of one recording (artist checked separately). */
    fun recordingKey(title: String) = parse(title).let { if (it.tags.isEmpty()) it.core else it.core + "|" + it.tags.joinToString("+") }
}
