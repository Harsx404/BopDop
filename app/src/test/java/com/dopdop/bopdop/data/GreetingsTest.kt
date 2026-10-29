package com.dopdop.bopdop.data

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import kotlin.random.Random

class GreetingsTest {
    private val lateNight = LocalDateTime.of(2026, 10, 30, 1, 15)

    @Test fun neverRepeatsTheLastLine() {
        val random = Random(7)
        var last: String? = null
        repeat(300) {
            val next = Greetings.pick(lateNight, last, null, random)
            assertNotEquals(last, next)
            last = next
        }
    }

    @Test fun mostlyEverydayLinesNotNightOnes() {
        val random = Random(3)
        val picks = (1..400).map { Greetings.pick(lateNight, null, null, random) }
        assertTrue(picks.count { it in Greetings.everyday } > 250)
        assertTrue(picks.toSet().size > 30)
    }

    @Test fun nameOnlyOnGreetingStyleLines() {
        val random = Random(11)
        val picks = (1..400).map { Greetings.pick(LocalDateTime.of(2026, 10, 30, 9, 0), null, "Asha", random) }
        assertTrue(picks.any { it == "Good morning, Asha" })
        assertTrue(picks.filter { it.endsWith(", Asha") }.all { it.startsWith("Good ") || it.startsWith("Hello again") })
    }
}
