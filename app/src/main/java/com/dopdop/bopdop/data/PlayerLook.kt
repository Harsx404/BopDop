package com.dopdop.bopdop.data

/** How the player's seek bar draws the song. */
enum class SeekStyle(val label: String) {
    WAVE("Wave"), LINE("Line"), BARS("Bars"), DOTS("Dots"), COIL("Coil"), PULSE("Pulse"),
}

/** BopDop's mascots, walking (or hopping) along the seek bar with the song; or nobody. */
enum class Mascot(val label: String) { NONE("None"), SEASONAL("Seasonal"), WISP("Wisp"), EMBER("Ember"), ECHO("Echo"),
    JYOTI("Jyoti"), TARA("Tara"), GAJJU("Gajju"),
    BUNNY("Bunny"), HANA("Hana"), MEI("Mei"), YUI("Yui"), FIGHTER("Fighter"), SAMURAI("Samurai"), SHINOBI("Shinobi");

    /** Explicit choices always win, including None. Only Seasonal follows the theme. */
    fun forFestival(festival: Festival?): Mascot = when {
        this != SEASONAL -> this
        festival == Festival.HALLOWEEN -> WISP
        festival == Festival.DIWALI -> JYOTI
        else -> BUNNY
    }
}
