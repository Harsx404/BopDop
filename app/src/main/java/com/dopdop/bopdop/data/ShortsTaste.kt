package com.dopdop.bopdop.data

import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.ytm.ShortClip
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/** Metadata tags only: no image analysis or guesses about a viewer's identity. */
object ShortsTags {
    private val hashtag = Regex("#[\\p{L}\\p{N}_]+")
    private val words = Regex("[\\p{L}\\p{N}]+")
    private val noise = setOf("short", "shorts", "ytshorts", "youtubeshorts", "youtube", "viral", "trending", "fyp", "foryou", "reels", "reel", "video", "subscribe", "like", "share", "explore", "shortvideo")
    private val aliases = mapOf(
        "dance" to setOf("dance", "dances", "dancing", "dancer", "dancers", "choreography", "dancechallenge", "dancecover"),
        "women" to setOf("women", "woman", "female", "girl", "girls", "girldance", "girlsdance", "womandance", "femaledance", "womendance", "ladies", "lady", "actress"),
        "girls" to setOf("girl", "girls", "girldance", "girlsdance"),
        "boys" to setOf("boy", "boys", "boysdance", "boydance"),
        "comedy" to setOf("comedy", "funny", "humour", "humor"),
        "romantic" to setOf("romantic", "romance", "love"),
        "live" to setOf("concert", "concerts", "liveperformance"),
        "singing" to setOf("singing", "singer", "vocals", "singingcover"),
        "remix" to setOf("remix", "remixed", "mashup", "dj"),
        "lofi" to setOf("lofi", "chill", "chillout"),
        "sad" to setOf("sad", "sadness", "sorrow", "heartbreak", "heartbroken", "emotional", "broken", "crying", "pain", "dard"),
        "motivation" to setOf("motivation", "motivational", "inspiration", "attitude"),
        "movie" to setOf("movie", "film", "scene", "trailer"),
        "anime" to setOf("anime", "amv", "edit", "edits", "manga"),
    )

    fun extract(title: String, supplied: Set<String> = emptySet()): Set<String> {
        val tags = linkedSetOf<String>()
        val explicit = (hashtag.findAll(title).map { it.value.drop(1) }.toList() + supplied)
            .map(::normalize).filter { it.length in 2..40 && it !in noise }
        val tokens = words.findAll(title.lowercase(Locale.ROOT)).map { it.value }.toSet() + explicit
        aliases.forEach { (tag, matches) -> if (tokens.any { it in matches }) tags += tag }
        // Compound hashtags still contribute their visible content categories.
        if (explicit.any {
            it.startsWith("girls") || (it.startsWith("girl") && it.endsWith("dance")) ||
            it.startsWith("women") || it.startsWith("woman") || it.startsWith("female") ||
            it.startsWith("lady") || it.startsWith("ladies")
        }) {
            tags += "women"
            tags += "girls"
        }
        if (explicit.any { it.contains("dance") || it.contains("dancing") }) tags += "dance"
        explicit.forEach { raw -> tags += aliases.entries.firstOrNull { raw in it.value }?.key ?: raw }
        return tags.take(12).toSet()
    }

    fun normalize(tag: String) = tag.lowercase(Locale.ROOT).removePrefix("#").replace(Regex("[^\\p{L}\\p{N}]"), "")
}

data class ShortWatch(
    val item: FeedShort,
    val at: Long,
    /** Actual foreground playback, excluding paused/buffering/background time. */
    val watchedMs: Long,
    val durationMs: Long,
    val completedLoops: Int = 0,
    val eventId: String = UUID.randomUUID().toString(),
    /** "Not interested": the strongest negative there is. */
    val dismissed: Boolean = false,
    /** Played, added or shared the Short's song, or shared the Short: a strong positive. */
    val acted: Boolean = false,
) {
    val tags get() = ShortsTags.extract(item.clip.title, item.clip.tags)
    val fraction get() = if (durationMs > 0) (watchedMs.toDouble() / durationMs).coerceIn(0.0, 3.0) else 0.0
    // A swipe after a tiny fraction is a weak negative, never stronger than an explicit like.
    val quickSkip get() = watchedMs in 250..1_999 && (durationMs <= 0 || fraction < .25)
    fun signal(): Double = when {
        dismissed -> -3.0
        acted -> 3.2
        quickSkip -> -.8
        completedLoops > 0 || fraction >= .9 -> 2.4 + completedLoops.coerceAtMost(2) * .6
        watchedMs >= 8_000 -> 1.4
        fraction >= .5 && watchedMs >= 2_000 -> 1.0
        watchedMs >= 3_000 -> .4
        else -> 0.0
    }

    fun toJson() = JSONObject().put("item", item.toJson()).put("at", at).put("watchedMs", watchedMs)
        .put("durationMs", durationMs).put("loops", completedLoops).put("eventId", eventId)
        .put("dismissed", dismissed).put("acted", acted)

    companion object {
        fun fromJson(o: JSONObject) = ShortWatch(FeedShort.fromJson(o.getJSONObject("item")), o.getLong("at"),
            o.optLong("watchedMs").coerceAtLeast(0), o.optLong("durationMs").coerceAtLeast(0), o.optInt("loops").coerceAtLeast(0),
            o.optString("eventId").ifBlank { UUID.randomUUID().toString() }, o.optBoolean("dismissed"), o.optBoolean("acted"))
    }
}

/** One settled page visit. Checkpoints share an id, so they update rather than multiply evidence. */
class ShortWatchSession(private val item: FeedShort, private val at: Long = System.currentTimeMillis()) {
    private val id = UUID.randomUUID().toString()
    private var lastTick: Long? = null
    private var wasPlaying = false
    private var watched = 0L
    private var duration = 0L
    private var loops = 0
    private var dismissed = false
    private var acted = false

    fun sample(nowElapsedMs: Long, playing: Boolean, durationMs: Long) {
        val previous = lastTick
        if (previous != null && wasPlaying && playing) watched += (nowElapsedMs - previous).coerceIn(0, 1_000)
        lastTick = nowElapsedMs
        wasPlaying = playing
        if (durationMs > 0 && playing) duration = durationMs
    }

    fun loopCompleted() { if (wasPlaying) loops++ }
    fun dismiss() { dismissed = true }
    fun act() { acted = true }
    fun snapshot() = ShortWatch(item, at, watched, duration, loops, id, dismissed, acted)
}

data class LikedShort(val item: FeedShort, val at: Long) {
    fun toJson() = JSONObject().put("item", item.toJson()).put("at", at)
    companion object { fun fromJson(o: JSONObject) = LikedShort(FeedShort.fromJson(o.getJSONObject("item")), o.getLong("at")) }
}

data class ShortsHistoryState(
    val watches: List<ShortWatch> = emptyList(),
    val liked: List<LikedShort> = emptyList(),
    val revision: Long = 0,
    val error: String? = null,
) {
    val likedIds: Set<String> by lazy { liked.mapTo(HashSet()) { it.item.clip.id } }
    fun toJson() = JSONObject().put("version", 1)
        .put("watches", JSONArray().also { a -> watches.forEach { a.put(it.toJson()) } })
        .put("liked", JSONArray().also { a -> liked.forEach { a.put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject): ShortsHistoryState {
            fun <T> read(key: String, parse: (JSONObject) -> T): List<T> = o.optJSONArray(key)?.let { a ->
                (0 until a.length()).mapNotNull { i -> runCatching { parse(a.getJSONObject(i)) }.getOrNull() }
            }.orEmpty()
            return ShortsHistoryState(read("watches", ShortWatch::fromJson).takeLast(ShortsTaste.MAX_WATCHES),
                read("liked", LikedShort::fromJson).distinctBy { it.item.clip.id }.sortedByDescending { it.at })
        }
    }
}

/**
 * The on-device ranker, small and explainable. Each Short is scored on what this viewer's own
 * watching says about its topics (watch-through, rewatches, likes, using its song; skips and "Not
 * interested" against), a little popularity to break ties, and a penalty for anything seen lately.
 * Which kind of Short to fetch next is a bandit over the feed's sources, so the ones that hold this
 * viewer's attention come up more. Women content is prioritized and boosted in suggestions and
 * discovery.
 */
class ShortsTaste(private val history: ShortsHistoryState, private val now: Long = System.currentTimeMillis()) {
    val tagWeights: Map<String, Double> by lazy {
        val scores = HashMap<String, Double>()
        val evidence = HashMap<String, Double>()
        // Baseline prior favoring women content in suggestions:
        scores["women"] = BASE_WOMEN_AFFINITY
        evidence["women"] = 1.0
        scores["girls"] = BASE_WOMEN_AFFINITY
        evidence["girls"] = 1.0

        history.watches.takeLast(MAX_WATCHES).forEach { e ->
            val decay = exp(-((now - e.at).coerceAtLeast(0).toDouble()) / HALF_LIFE_MS)
            val signal = e.signal() * decay
            val tags = usable(e.tags)
            tags.forEach { tag ->
                val boost = if (tag in WOMEN_TAGS && signal > 0) 1.25 else 1.0
                scores[tag] = (scores[tag] ?: 0.0) + (signal * boost) / sqrt(tags.size.coerceAtLeast(1).toDouble())
                evidence[tag] = (evidence[tag] ?: 0.0) + decay
            }
        }
        history.liked.forEach { l ->
            usable(ShortsTags.extract(l.item.clip.title, l.item.clip.tags)).forEach { tag ->
                val likeBoost = if (tag in WOMEN_TAGS) 5.0 else 4.0
                scores[tag] = (scores[tag] ?: 0.0) + likeBoost
                evidence[tag] = (evidence[tag] ?: 0.0) + 1.0
            }
        }
        scores.mapValues { (tag, score) -> (score / sqrt(evidence.getValue(tag).coerceAtLeast(1.0))).coerceIn(-4.0, 6.0) }
    }

    /** The latest visit to each Short. */
    private val lastSeen: Map<String, ShortWatch> by lazy { history.watches.associateBy { it.item.clip.id } }

    /**
     * Kept out of the feed: anything marked "Not interested" (for a month), skipped in the last
     * three days, or mostly watched in the last week (liked Shorts stay reachable from Liked).
     */
    fun hidden(clipId: String): Boolean {
        val w = lastSeen[clipId] ?: return false
        val age = now - w.at
        return when {
            w.dismissed -> age < 30 * DAY
            w.quickSkip -> age < 3 * DAY
            w.fraction >= .5 || w.completedLoops > 0 -> age < 7 * DAY
            else -> false
        }
    }

    /** Topics "Not interested" was used on lately: Shorts about them sink hard. */
    private val dismissedTags: Set<String> by lazy {
        history.watches.filter { it.dismissed && now - it.at < 30 * DAY }.flatMap { usable(it.tags) }.toSet()
    }

    fun score(clip: ShortClip): Double {
        val tags = usable(ShortsTags.extract(clip.title, clip.tags))
        val affinity = tags.sumOf { tagWeights[it] ?: 0.0 } / sqrt(tags.size.coerceAtLeast(1).toDouble())
        val last = lastSeen[clip.id]
        val seen = if (last != null && now - last.at < DAY) if (last.quickSkip) 6.0 else 3.0 else 0.0
        val dismissed = if (tags.any { it in dismissedTags }) 2.5 else 0.0
        val womenBoost = if (tags.any { it in WOMEN_TAGS }) WOMEN_SUGGESTION_BOOST else 0.0
        // Popularity breaks close ties; a billion views cannot overpower learned preferences.
        return affinity + womenBoost + ln(1 + clip.viewCount.coerceAtLeast(0).toDouble()).coerceAtMost(22.0) * .015 - seen - dismissed
    }

    fun rank(clips: List<ShortClip>, random: Random): List<ShortClip> = clips.distinctBy { it.id }
        .map { it to (score(it) + random.nextDouble() * .3) }.sortedByDescending { it.second }.map { it.first }

    /**
     * Search tags for the next topic batch: one of the viewer's strongest topics (drawn in
     * proportion to how strong), sometimes with a second; or, to explore or with nothing learned
     * yet, one of the feed's discovery topics. [avoid] keeps the same topic from coming round again.
     */
    fun queryTags(random: Random, explore: Boolean, avoid: Set<String> = emptySet()): List<String> {
        val preferred = tagWeights.filterValues { it > .15 }.entries.filter { it.key !in avoid }.sortedByDescending { it.value }.take(8)
        if (explore || preferred.isEmpty()) {
            val candidates = EXPLORE.filter { it !in avoid }.ifEmpty { EXPLORE }
            val womenCandidates = candidates.filter { isWomenTopic(it) }
            val pick = if (womenCandidates.isNotEmpty() && random.nextDouble() < 0.6) {
                womenCandidates.random(random)
            } else {
                candidates.random(random)
            }
            return listOf(pick)
        }
        val total = preferred.sumOf { it.value }
        var ticket = random.nextDouble() * total
        val primary = preferred.firstOrNull { ticket -= it.value; ticket <= 0 }?.key ?: preferred.first().key
        val other = preferred.filter { it.key != primary }
        return if (other.isNotEmpty() && random.nextDouble() < .3) listOf(primary, other.random(random).key) else listOf(primary)
    }

    private fun isWomenTopic(topic: String): Boolean {
        val lower = topic.lowercase(Locale.ROOT)
        return lower.contains("women") || lower.contains("girls") || lower.contains("girl") || lower.contains("female")
    }

    /**
     * How much each feed source is worth fetching from next, drawn Thompson-style from how its
     * Shorts have done: a Short watched well or acted on is a win, a skip or "Not interested" a loss.
     */
    fun sourceDraw(sources: List<String>, random: Random): Map<String, Double> {
        val wins = HashMap<String, Double>(); val losses = HashMap<String, Double>()
        history.watches.takeLast(600).forEach { e ->
            val src = e.item.source.ifBlank { return@forEach }
            val decay = exp(-((now - e.at).coerceAtLeast(0).toDouble()) / HALF_LIFE_MS)
            val sig = e.signal()
            if (sig >= 1.0) wins[src] = (wins[src] ?: 0.0) + decay
            else if (sig <= 0.0) losses[src] = (losses[src] ?: 0.0) + decay
        }
        return sources.associateWith { s ->
            val a = 1.0 + (wins[s] ?: 0.0) + (PRIOR[s] ?: .5) * 2
            val b = 1.0 + (losses[s] ?: 0.0) + (1 - (PRIOR[s] ?: .5)) * 2
            val mean = a / (a + b)
            val sd = sqrt(mean * (1 - mean) / (a + b + 1))
            (mean + gaussian(random) * sd).coerceIn(0.01, 0.99)
        }
    }

    private fun gaussian(random: Random): Double {
        // Box–Muller.
        val u = random.nextDouble().coerceAtLeast(1e-9); val v = random.nextDouble()
        return sqrt(-2 * ln(u)) * kotlin.math.cos(2 * Math.PI * v)
    }

    companion object {
        const val MAX_WATCHES = 5_000
        private const val DAY = 86_400_000L
        private const val HALF_LIFE_MS = 14.0 * 86_400_000 / .69314718056
        const val BASE_WOMEN_AFFINITY = 2.0
        const val WOMEN_SUGGESTION_BOOST = 1.5
        val WOMEN_TAGS = setOf("women", "girls")
        /** Demographics excluded from topic ranking: boys/men are not ranked as topics. */
        private val PEOPLE = setOf("boys")
        fun usable(tags: Set<String>) = tags.filterTo(LinkedHashSet()) { it !in PEOPLE }
        /** Discovery topics: what holds attention on Shorts that's about music, movement, and performance. */
        val EXPLORE = listOf(
            "women dance", "girls dance", "female singer", "girl dance challenge", "women choreography",
            "dance", "dance challenge", "choreography", "trending dance", "sad song", "emotional song", "live performance",
            "singing cover", "remix", "movie song scene", "anime edit", "music cover", "street performance", "comedy"
        )
        /** Where a feed source starts before this viewer has taught it anything. */
        private val PRIOR = mapOf("related" to .55, "topic" to .55, "loved" to .5, "explore" to .45, "seed" to .6)
    }
}

/** Serializable feed payloads retain the soundtrack across restarts and a future repository change. */
fun FeedShort.toJson(): JSONObject = JSONObject().put("clip", JSONObject().put("id", clip.id).put("title", clip.title)
    .put("views", clip.views).put("viewCount", clip.viewCount).put("thumbnail", clip.thumbnail).put("tags", JSONArray(clip.tags.toList())))
    .put("song", (if (song.source == com.dopdop.bopdop.data.model.Source.YTM) song.copy(streamUrl = null) else song).toJson())
    .put("source", source)

internal fun readFeedShort(o: JSONObject): FeedShort {
    val c = o.getJSONObject("clip")
    val tags = c.optJSONArray("tags")
    return FeedShort(ShortClip(c.getString("id"), c.optString("title"), c.optString("views"), c.optLong("viewCount"),
        c.optString("thumbnail"), (0 until (tags?.length() ?: 0)).map { tags!!.getString(it) }.toSet()), Track.fromJson(o.getJSONObject("song")),
        o.optString("source"))
}
