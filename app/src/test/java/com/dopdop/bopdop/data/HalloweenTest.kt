package com.dopdop.bopdop.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HalloweenTest {
    private val outsideSeason = LocalDate.of(2026, 3, 15)

    @Test fun previewWorksOutsideTheFestivalWindowAndOffStillWins() {
        assertNull(Festivals.current(FestiveLevel.FULL, FestivePick.AUTO, "IN", outsideSeason))
        assertEquals(Festival.HALLOWEEN, Festivals.current(FestiveLevel.FULL, FestivePick.AUTO, "IN", outsideSeason, halloweenPreview = true))
        assertNull(Festivals.current(FestiveLevel.OFF, FestivePick.AUTO, "IN", outsideSeason, halloweenPreview = true))
    }

    @Test fun seasonalCompanionFollowsTheFestivalWithoutChangingExplicitChoices() {
        assertEquals(Mascot.WISP, Mascot.SEASONAL.forFestival(Festival.HALLOWEEN))
        assertEquals(Mascot.BUNNY, Mascot.SEASONAL.forFestival(null))
        assertEquals(Mascot.BUNNY, Mascot.SEASONAL.forFestival(Festival.DIWALI))
        for (choice in listOf(Mascot.NONE, Mascot.BUNNY, Mascot.EMBER, Mascot.ECHO, Mascot.WISP)) {
            assertEquals(choice, choice.forFestival(Festival.HALLOWEEN))
            assertEquals(choice, choice.forFestival(null))
        }
    }

    @Test fun previewAndCompanionSurviveSettingsRoundTrip() {
        val original = Settings(halloweenPreview = true, mascot = Mascot.ECHO, seekStyle = SeekStyle.DOTS, reduceMotion = true)
        val restored = Settings.fromJson(JSONObject(original.toJson().toString()))
        assertTrue(restored.halloweenPreview)
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
        assertFalse(Settings.fromJson(JSONObject()).halloweenPreview)
    }
}
