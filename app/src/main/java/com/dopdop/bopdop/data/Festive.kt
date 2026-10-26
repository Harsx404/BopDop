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
    DIWALI("Diwali", "Happy Diwali"),
    CHHATH("Chhath Puja", "Happy Chhath Puja"),
}

/** Whether festival themes show on Home. */
enum class FestiveLevel(val label: String) { FULL("On"), OFF("Off") }

/** Which festival to celebrate: the one for your region, a particular one, or all of them. */
enum class FestivePick(val label: String, val festival: Festival? = null) {
    AUTO("My region"), NAVARATRI("Navaratri", Festival.NAVARATRI), DIWALI("Diwali", Festival.DIWALI),
    CHHATH("Chhath Puja", Festival.CHHATH), HALLOWEEN("Halloween", Festival.HALLOWEEN), ALL("All"),
}

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

    /**
     * Diwali (Lakshmi Puja) by year, from Drik Panchang. Dhanteras is two days before and Bhai Dooj
     * two days after. Chhath Puja's Sandhya Arghya is six days after Diwali (Kartik Shukla Shashthi),
     * from Nahay Khay (+4) to Usha Arghya (+7).
     */
    private val diwali = mapOf(
        2025 to LocalDate.of(2025, 10, 20),
        2026 to LocalDate.of(2026, 11, 8),
        2027 to LocalDate.of(2027, 10, 29),
        2028 to LocalDate.of(2028, 10, 17),
    )

    /** The festival's own days in [year] (no lead-up). */
    fun days(f: Festival, year: Int): ClosedRange<LocalDate>? = when (f) {
        Festival.NAVARATRI -> navaratri[year]?.let { it.first..it.second }
        Festival.HALLOWEEN -> LocalDate.of(year, Month.OCTOBER, 31).let { it..it }
        Festival.DIWALI -> diwali[year]?.let { it.minusDays(2)..it.plusDays(2) }
        Festival.CHHATH -> diwali[year]?.let { it.plusDays(4)..it.plusDays(7) }
    }

    /** The days a festival's theme shows in [year]: from five days before it until it ends. */
    fun window(f: Festival, year: Int): ClosedRange<LocalDate>? = days(f, year)?.let { it.start.minusDays(LEAD_DAYS)..it.endInclusive }

    /** Dussehra (Vijayadashami): the last day of the Navaratri window. */
    fun isDussehra(today: LocalDate): Boolean = navaratri[today.year]?.second == today

    /**
     * The line over the greeting on Home: a countdown in the days before the festival, the
     * day of Navratri while it runs ("Day 4"), and the festival's own greeting on its day.
     */
    fun greeting(f: Festival, today: LocalDate): String {
        // Diwali's five days and Chhath's four each have their own name.
        diwali[today.year]?.let { d ->
            val k = java.time.temporal.ChronoUnit.DAYS.between(d, today).toInt()
            if (f == Festival.DIWALI) when (k) {
                -2 -> return "Happy Dhanteras"; -1 -> return "Choti Diwali"; 0 -> return "Happy Diwali"
                1 -> return "Govardhan Puja"; 2 -> return "Happy Bhai Dooj"
            }
            if (f == Festival.CHHATH) when (k) {
                4 -> return "Chhath Puja · Nahay Khay"; 5 -> return "Chhath Puja · Kharna"
                6 -> return "Chhath Puja · Sandhya Arghya"; 7 -> return "Chhath Puja · Usha Arghya"
            }
        }
        val start = when (f) {
            Festival.NAVARATRI -> navaratri[today.year]?.first
            Festival.HALLOWEEN -> LocalDate.of(today.year, Month.OCTOBER, 31)
            Festival.DIWALI -> diwali[today.year]
            Festival.CHHATH -> diwali[today.year]?.plusDays(6)
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

    /** True once [f]'s last day this year has gone by (or it has no date this year). */
    fun passed(f: Festival, today: LocalDate): Boolean = window(f, today.year)?.let { today > it.endInclusive } ?: true

    /** The choices offered: My region and All, plus each festival that hasn't ended this year. */
    fun picks(today: LocalDate): List<FestivePick> = FestivePick.entries.filter { p -> p.festival?.let { !passed(it, today) } ?: true }

    /** Festivals whose theme window includes [today]. */
    fun open(today: LocalDate): List<Festival> = Festival.entries.filter { f -> window(f, today.year)?.contains(today) == true }

    /**
     * The festivals people around [country] celebrate, most important first: in India and Nepal
     * Navaratri, Diwali and Chhath Puja, with Halloween when none of them is on; elsewhere Halloween
     * and Diwali.
     */
    fun forRegion(country: String): List<Festival> =
        if (country.uppercase(Locale.ROOT) in setOf("IN", "NP")) listOf(Festival.NAVARATRI, Festival.DIWALI, Festival.CHHATH, Festival.HALLOWEEN)
        else listOf(Festival.HALLOWEEN, Festival.DIWALI)

    /**
     * Of several festivals in their windows, the one to show: one whose own days have begun wins
     * over one still in its lead-up; then the earlier in [order] (region priority).
     */
    private fun choose(open: List<Festival>, order: List<Festival>, today: LocalDate): Festival? =
        open.sortedWith(compareBy({ f -> if (days(f, today.year)?.contains(today) == true) 0 else 1 }, { f -> order.indexOf(f).let { if (it < 0) 99 else it } }))
            .firstOrNull()

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
            FestivePick.AUTO -> forRegion(country).let { region -> choose(open.filter { it in region }, region, today) }
            FestivePick.ALL -> choose(open, Festival.entries, today)
            // A picked festival whose dates have gone by this year is over: it never stands in for another.
            else -> pick.festival?.takeUnless { passed(it, today) }
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
