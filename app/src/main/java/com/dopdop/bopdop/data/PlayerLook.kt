package com.dopdop.bopdop.data

/** How the player's seek bar draws the song. */
enum class SeekStyle(val label: String) {
    WAVE("Wave"), LINE("Line"), BARS("Bars"), DOTS("Dots"),
}

/** BopDop's mascot, the headphone kitty, walking along the seek bar with the song (or nobody). */
enum class Mascot(val label: String) { NONE("None"), KITTY("Kitty") }
