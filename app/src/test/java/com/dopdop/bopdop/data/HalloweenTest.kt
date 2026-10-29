package com.dopdop.bopdop.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HalloweenTest {
    private val outsideSeason = LocalDate.of(2026, 3, 15)

    @Test fun nothingShowsOutsideTheFestivalWindows() {
        assertNull(Festivals.current(FestiveLevel.FULL, FestivePick.AUTO, "IN", outsideSeason))
        assertNull(Festivals.current(FestiveLevel.FULL, FestivePick.ALL, "US", outsideSeason))
    }

    @Test fun indianAndNepaliTimeZonesDecideTheRegion() {
        assertEquals("IN", Festivals.zoneCountry("Asia/Kolkata"))
        assertEquals("IN", Festivals.zoneCountry("Asia/Calcutta"))
        assertEquals("NP", Festivals.zoneCountry("Asia/Kathmandu"))
        assertNull(Festivals.zoneCountry("America/New_York"))
    }

    @Test fun seasonalCompanionFollowsTheFestivalWithoutChangingExplicitChoices() {
        assertEquals(Mascot.WISP, Mascot.SEASONAL.forFestival(Festival.HALLOWEEN))
        assertEquals(Mascot.BUNNY, Mascot.SEASONAL.forFestival(null))
        assertEquals(Mascot.JYOTI, Mascot.SEASONAL.forFestival(Festival.DIWALI))
        assertEquals(Mascot.BUNNY, Mascot.SEASONAL.forFestival(Festival.CHHATH))
        for (choice in listOf(Mascot.NONE, Mascot.BUNNY, Mascot.EMBER, Mascot.ECHO, Mascot.WISP, Mascot.TARA)) {
            assertEquals(choice, choice.forFestival(Festival.HALLOWEEN))
            assertEquals(choice, choice.forFestival(null))
        }
    }

    @Test fun companionAndStorySurviveSettingsRoundTrip() {
        val original = Settings(diwaliStory = 0b101, mascot = Mascot.ECHO, seekStyle = SeekStyle.DOTS, reduceMotion = true)
        val restored = Settings.fromJson(JSONObject(original.toJson().toString()))
        assertEquals(0b101, restored.diwaliStory)
        assertEquals(Mascot.ECHO, restored.mascot)
        assertEquals(SeekStyle.DOTS, restored.seekStyle)
        assertTrue(restored.reduceMotion)
    }

    @Test fun existingMascotsAndNoPetMigrationArePreserved() {
        assertEquals(Mascot.BUNNY, Settings.fromJson(JSONObject().put("mascot", "BUNNY")).mascot)
        // Retired mascots fall back to Seasonal.
        for (gone in listOf("KITTY", "BIKE", "WAGON", "DINO", "CRAFTER"))
            assertEquals(Mascot.SEASONAL, Settings.fromJson(JSONObject().put("mascot", gone)).mascot)
        assertEquals(Mascot.NONE, Settings.fromJson(JSONObject().put("seekPet", "NONE")).mascot)
        assertEquals(Mascot.SEASONAL, Settings.fromJson(JSONObject().put("seekPet", "OLD_PET")).mascot)
        assertEquals(Mascot.SEASONAL, Settings.fromJson(JSONObject()).mascot)
        assertEquals(0, Settings.fromJson(JSONObject()).diwaliStory)
    }
}
