package com.dopdop.bopdop.data

/** How the player's seek bar draws the song. */
enum class SeekStyle(val label: String) {
    WAVE("Wave"), LINE("Line"), BARS("Bars"), DOTS("Dots"),
}

/** BopDop's mascots, walking (or hopping) along the seek bar with the song; or nobody. */
enum class Mascot(val label: String) { NONE("None"), KITTY("Kitty"), BUNNY("Bunny"), BIKE("Bike"), WAGON("Wagon"), DINO("Dino"), CRAFTER("Crafter"),
    HANA("Hana"), MEI("Mei"), YUI("Yui"), FIGHTER("Fighter"), SAMURAI("Samurai"), SHINOBI("Shinobi") }
