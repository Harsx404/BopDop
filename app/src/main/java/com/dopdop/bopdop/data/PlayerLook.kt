package com.dopdop.bopdop.data

/** How the player's seek bar draws the song. */
enum class SeekStyle(val label: String) {
    WAVE("Wave"), LINE("Line"), BARS("Bars"), DOTS("Dots"),
}

/**
 * A little friend that runs along the seek bar with the song. Festival pets only come out while
 * their festival is on.
 */
enum class SeekPet(val label: String, val festival: Festival? = null) {
    NONE("None"), CAT("Cat"), BUNNY("Bunny"), DUCK("Duck"), DINO("Dino"),
    PEACOCK("Peacock", Festival.NAVARATRI), RAVANA("Ravana", Festival.NAVARATRI), GHOST("Ghost", Festival.HALLOWEEN);

    /** The pet to actually show today: a festival pet out of season hands over to the cat. */
    fun inSeason(now: Festival?): SeekPet = if (festival == null || festival == now) this else CAT
}
