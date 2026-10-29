package com.dopdop.bopdop.data

import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * Home's big greeting. Mostly everyday lines about music that suit any hour, with a light touch of
 * the time of day and the day of the week now and then. A new one is picked each time the app is
 * opened, never the same as the one before.
 */
object Greetings {
    /** Lines that fit any moment. */
    val everyday = listOf(
        "What's playing today?", "Press play on something new", "Your soundtrack awaits", "Find your next favourite",
        "Turn it up", "Music for every mood", "Let's find your sound", "Ready when you are", "Hit play, feel better",
        "What are we vibing to?", "Your music, your way", "Something fresh for you", "Headphones on?",
        "Let the music move you", "Sing like no one's listening", "Discover something new", "Back for more?",
        "Pick a song, any song", "Keep the rhythm going", "Feel every beat", "Music makes it better",
        "Start with a favourite", "Time for a little music", "Your next obsession is here", "Every day needs a soundtrack",
        "Dance like nobody's watching", "Good songs, good mood", "Queue up something great", "What's the vibe today?",
        "Play it loud", "One more song?", "The perfect song is waiting", "Explore new sounds", "Hum along",
        "Rewind, replay, repeat", "Bring the beat", "Let's go", "Find your groove", "Made for listening",
        "Your ears deserve this", "New finds, old favourites", "Let's hear it", "Tune in, zone out",
        "Songs for right now", "Pure good vibes", "Shuffle something brilliant", "Everything sounds better with music",
        "Here's to great music", "Follow the rhythm", "Make some noise", "Lose yourself in a song",
        "Fresh picks, just for you", "Ready to press play?", "Set the mood", "There's a song for that",
    )

    /** Now and then, a word about the part of the day. */
    private fun ofTheDay(hour: Int): List<String> = when (hour) {
        in 5..11 -> listOf("Good morning", "Rise and shine", "Morning, music lover", "Coffee and a good song")
        in 12..16 -> listOf("Good afternoon", "Midday music break", "Afternoon groove", "Keep the day moving")
        in 17..21 -> listOf("Good evening", "Evening tunes", "Unwind with something good", "Golden hour sounds")
        else -> listOf("Hello again", "Something calm, maybe?", "Soft songs, quiet hours")
    }

    private fun ofTheWeek(day: DayOfWeek): List<String> = when (day) {
        DayOfWeek.MONDAY -> listOf("New week, new music")
        DayOfWeek.FRIDAY -> listOf("Happy Friday", "Friday feels")
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> listOf("Weekend mode: on", "Happy weekend")
        else -> emptyList()
    }

    /**
     * One greeting for [now], different from [previous]. About one time in four it speaks to the
     * hour or the day; "Good …" lines and "Hello again" take the listener's [name] when known.
     */
    fun pick(now: LocalDateTime, previous: String?, name: String?, random: Random = Random.Default): String {
        val timely = ofTheDay(now.hour) + ofTheWeek(now.dayOfWeek)
        val pool = (if (random.nextInt(4) == 0) timely else everyday).filter { it != previous && withName(it, name) != previous }
        val line = pool.random(random)
        return withName(line, name)
    }

    private fun withName(line: String, name: String?) =
        if (name != null && (line.startsWith("Good ") || line == "Hello again")) "$line, $name" else line
}
