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
        assertNull(Festivals.current(FestiveLevel.FULL, FestivePick.AUTO, "IN", day(10, 28)))
        assertNull(Festivals.current(FestiveLevel.FULL, FestivePick.AUTO, "US", day(11, 1)))
        assertNull(Festivals.current(FestiveLevel.FULL, FestivePick.AUTO, "US", day(10, 15)))
    }

    @Test fun pickingSwapsTheThemeOnlyWhileAFestivalIsOn() {
        assertEquals(Festival.HALLOWEEN, Festivals.current(FestiveLevel.LIMITED, FestivePick.HALLOWEEN, "IN", day(10, 15)))
        assertEquals(Festival.NAVARATRI, Festivals.current(FestiveLevel.FULL, FestivePick.ALL, "US", day(10, 15)))
        assertEquals(Festival.HALLOWEEN, Festivals.current(FestiveLevel.FULL, FestivePick.ALL, "IN", day(10, 30)))
        assertNull(Festivals.current(FestiveLevel.FULL, FestivePick.HALLOWEEN, "US", day(12, 1)))
        assertNull(Festivals.current(FestiveLevel.OFF, FestivePick.ALL, "US", day(10, 30)))
    }
}
