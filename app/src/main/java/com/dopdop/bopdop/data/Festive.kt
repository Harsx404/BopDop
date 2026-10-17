package com.dopdop.bopdop.data

import android.content.Context
import android.telephony.TelephonyManager
import java.time.LocalDate
import java.time.Month
import java.util.Locale

/** Festivals the app dresses up for. Each lasts from five days before it until its last day. */
enum class Festival(val label: String, val greeting: String) {
    NAVARATRI("Navaratri", "Happy Navratri"),
    HALLOWEEN("Halloween", "Happy Halloween"),
}

/** How much of a festival shows: everything (Home and pet), only the pet, or nothing. */
enum class FestiveLevel(val label: String) { FULL("Full"), LIMITED("Pet only"), OFF("Off") }

/** Which festival to celebrate: the one for your region, a particular one, or all of them. */
enum class FestivePick(val label: String) { AUTO("My region"), NAVARATRI("Navaratri"), HALLOWEEN("Halloween"), ALL("All") }

object Festivals {
    const val LEAD_DAYS = 5L

    /**
     * Navaratri (first day) to Vijayadashami / Dussehra (last day). The dates follow the lunar
     * calendar, so they move every year; Dussehra dates are from Drik Panchang, and the first day
     * of Navaratri is nine days before it. Years not listed simply get no Navaratri theme.
     */
    private val navaratri = mapOf(
        2025 to (LocalDate.of(2025, 9, 22) to LocalDate.of(2025, 10, 2)),
        2026 to (LocalDate.of(2026, 10, 11) to LocalDate.of(2026, 10, 20)),
        2027 to (LocalDate.of(2027, 9, 30) to LocalDate.of(2027, 10, 9)),
        2028 to (LocalDate.of(2028, 9, 18) to LocalDate.of(2028, 9, 27)),
    )

    /** The days a festival's theme shows in [year]: from five days before it until it ends. */
    fun window(f: Festival, year: Int): ClosedRange<LocalDate>? {
        val (start, end) = when (f) {
            Festival.NAVARATRI -> navaratri[year] ?: return null
            Festival.HALLOWEEN -> LocalDate.of(year, Month.OCTOBER, 31) to LocalDate.of(year, Month.OCTOBER, 31)
        }
        return start.minusDays(LEAD_DAYS)..end
    }

    /** Dussehra (Vijayadashami): the last day of the Navaratri window. */
    fun isDussehra(today: LocalDate): Boolean = navaratri[today.year]?.second == today

    /**
     * The line over the greeting on Home: a countdown in the days before the festival, the
     * day of Navratri while it runs ("Day 4"), and the festival's own greeting on its day.
     */
    fun greeting(f: Festival, today: LocalDate): String {
        val start = when (f) {
            Festival.NAVARATRI -> navaratri[today.year]?.first
            Festival.HALLOWEEN -> LocalDate.of(today.year, Month.OCTOBER, 31)
        } ?: return f.greeting
        val until = java.time.temporal.ChronoUnit.DAYS.between(today, start)
        return when {
            until == 1L -> "${f.label} is tomorrow"
            until > 1L -> "${f.label} in $until days"
            f == Festival.NAVARATRI && isDussehra(today) -> "Happy Dussehra"
            f == Festival.NAVARATRI -> "${f.greeting} · Day ${java.time.temporal.ChronoUnit.DAYS.between(start, today) + 1}"
            else -> f.greeting
        }
    }

    /** Festivals whose theme window includes [today]. */
    fun open(today: LocalDate): List<Festival> = Festival.entries.filter { f -> window(f, today.year)?.contains(today) == true }

    /** The festival people around [country] celebrate: Navaratri in India and Nepal, Halloween elsewhere. */
    fun forRegion(country: String): Festival = if (country.uppercase(Locale.ROOT) in setOf("IN", "NP")) Festival.NAVARATRI else Festival.HALLOWEEN

    /**
     * The festival to show now, if any. Auto shows your region's festival in its window; picking
     * one shows that theme whenever a festival is on (so you can swap Navaratri for Halloween or
     * the other way round); All shows each festival in its own window. Nothing shows outside the
     * festive windows, and Off shows nothing at all.
     */
    fun current(level: FestiveLevel, pick: FestivePick, country: String, today: LocalDate): Festival? {
        if (level == FestiveLevel.OFF) return null
        val open = open(today)
        if (open.isEmpty()) return null
        return when (pick) {
            FestivePick.AUTO -> forRegion(country).takeIf { it in open }
            FestivePick.NAVARATRI -> Festival.NAVARATRI
            FestivePick.HALLOWEEN -> Festival.HALLOWEEN
            FestivePick.ALL -> open.first()
        }
    }

    /** Where the phone is: the mobile network's country, then the SIM's, then the language setting. */
    fun country(ctx: Context): String {
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        return listOfNotNull(
            runCatching { tm?.networkCountryIso }.getOrNull(),
            runCatching { tm?.simCountryIso }.getOrNull(),
            Locale.getDefault().country,
        ).firstOrNull { it.length == 2 }.orEmpty()
    }
}
