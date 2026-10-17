package com.dopdop.bopdop.data

/** How the player's seek bar draws the song. */
enum class SeekStyle(val label: String) {
    WAVE("Wave"), LINE("Line"), BARS("Bars"), DOTS("Dots"),
}

/** A little friend that runs along the seek bar with the song. */
enum class SeekPet(val label: String) {
    NONE("None"), CAT("Cat"), BUNNY("Bunny"), DUCK("Duck"), DINO("Dino"),
}
