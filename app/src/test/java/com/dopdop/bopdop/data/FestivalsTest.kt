package com.dopdop.bopdop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FestivalsTest {
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d)

    @Test fun navaratriStartsFiveDaysEarlyAndEndsAfterDussehra() {
        assertNull(Festivals.current(FestiveLevel.FULL, FestivePick.AUTO, "IN", day(10, 5)))
        assertEquals(Festival.NAVARATRI, Festivals.current(FestiveLevel.FULL, FestivePick.AUTO, "IN", day(10, 6)))
        assertEquals(Festival.NAVARATRI, Festivals.current(FestiveLevel.FULL, FestivePick.AUTO, "IN", day(10, 20)))
        assertNull(Festivals.current(FestiveLevel.FULL, FestivePick.AUTO, "IN", day(10, 21)))
    }

    @Test fun halloweenFollowsTheRegionAndVanishesAfterTheDay() {
        assertEquals(Festival.HALLOWEEN, Festivals.current(FestiveLevel.FULL, FestivePick.AUTO, "us", day(10, 26)))
        // In India Halloween fills in while no Indian festival is on (Navaratri ended Oct 20, Diwali's lead-up starts Nov 1).
        assertEquals(Festival.HALLOWEEN, Festivals.current(FestiveLevel.FULL, FestivePick.AUTO, "IN", day(10, 28)))
        // Outside India Diwali follows (its lead-up starts Nov 1 in 2026).
        assertEquals(Festival.DIWALI, Festivals.current(FestiveLevel.FULL, FestivePick.AUTO, "US", day(11, 1)))
        assertNull(Festivals.current(FestiveLevel.FULL, FestivePick.AUTO, "US", day(10, 15)))
    }

    @Test fun pickingSwapsTheThemeOnlyWhileAFestivalIsOn() {
        assertEquals(Festival.HALLOWEEN, Festivals.current(FestiveLevel.FULL, FestivePick.HALLOWEEN, "IN", day(10, 15)))
        assertEquals(Festival.NAVARATRI, Festivals.current(FestiveLevel.FULL, FestivePick.ALL, "US", day(10, 15)))
        assertEquals(Festival.HALLOWEEN, Festivals.current(FestiveLevel.FULL, FestivePick.ALL, "IN", day(10, 30)))
        assertNull(Festivals.current(FestiveLevel.FULL, FestivePick.HALLOWEEN, "US", day(12, 1)))
        assertNull(Festivals.current(FestiveLevel.OFF, FestivePick.ALL, "US", day(10, 30)))
    }

    @Test fun greetingCountsDownThenCountsTheDaysThenSaysDussehra() {
        assertEquals("Navaratri in 3 days", Festivals.greeting(Festival.NAVARATRI, day(10, 8)))
        assertEquals("Navaratri is tomorrow", Festivals.greeting(Festival.NAVARATRI, day(10, 10)))
        assertEquals("Happy Navratri · Day 1", Festivals.greeting(Festival.NAVARATRI, day(10, 11)))
        assertEquals("Happy Navratri · Day 8", Festivals.greeting(Festival.NAVARATRI, day(10, 18)))
        assertEquals("Happy Dussehra", Festivals.greeting(Festival.NAVARATRI, day(10, 20)))
        assertEquals("Halloween in 5 days", Festivals.greeting(Festival.HALLOWEEN, day(10, 26)))
        assertEquals("Happy Halloween", Festivals.greeting(Festival.HALLOWEEN, day(10, 31)))
    }

    @Test fun festivalsThatAreOverDropOffTheList() {
        val day = java.time.LocalDate.of(2026, 10, 21) // Navaratri 2026 ended on the 20th
        org.junit.Assert.assertTrue(Festivals.passed(Festival.NAVARATRI, day))
        org.junit.Assert.assertFalse(Festivals.passed(Festival.HALLOWEEN, day))
        org.junit.Assert.assertEquals(listOf(FestivePick.AUTO, FestivePick.DIWALI, FestivePick.CHHATH, FestivePick.HALLOWEEN, FestivePick.ALL), Festivals.picks(day))
        // Picking Navaratri after it ended shows nothing, even during Halloween week.
        org.junit.Assert.assertNull(Festivals.current(FestiveLevel.FULL, FestivePick.NAVARATRI, "IN", java.time.LocalDate.of(2026, 10, 28)))
    }

    @Test fun diwaliAndChhathTakeTheirTurnsInIndia() {
        assertEquals(Festival.DIWALI, Festivals.current(FestiveLevel.FULL, FestivePick.AUTO, "IN", day(11, 1)))
        assertEquals(Festival.DIWALI, Festivals.current(FestiveLevel.FULL, FestivePick.AUTO, "IN", day(11, 8)))
        // Chhath's lead-up overlaps Diwali's days: Diwali, being on, wins until Bhai Dooj.
        assertEquals(Festival.DIWALI, Festivals.current(FestiveLevel.FULL, FestivePick.AUTO, "IN", day(11, 10)))
        assertEquals(Festival.CHHATH, Festivals.current(FestiveLevel.FULL, FestivePick.AUTO, "IN", day(11, 11)))
        assertEquals(Festival.CHHATH, Festivals.current(FestiveLevel.FULL, FestivePick.AUTO, "IN", day(11, 15)))
        assertNull(Festivals.current(FestiveLevel.FULL, FestivePick.AUTO, "IN", day(11, 16)))
        assertEquals("Happy Diwali", Festivals.greeting(Festival.DIWALI, day(11, 8)))
        assertEquals("Chhath Puja · Sandhya Arghya", Festivals.greeting(Festival.CHHATH, day(11, 14)))
    }
}
