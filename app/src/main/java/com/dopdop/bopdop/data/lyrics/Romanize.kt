package com.dopdop.bopdop.data.lyrics

/**
 * Offline Devanagari (Hindi) and Gurmukhi (Punjabi) → Latin, the way the songs are usually
 * typed: "तुम ही हो" → "tum hi ho", "ਤੇਰੇ ਨਾਲ" → "tere naal". Inherent vowels are dropped at
 * word ends and between a vowel-consonant and a consonant-vowel (Hindi schwa deletion), so
 * "समझना" reads "samajhna", not "samajhanaa".
 */
object Romanize {
    fun hasIndic(text: String) = text.any { it in 'ऀ'..'ॿ' || it in '਀'..'੿' }

    fun line(text: String): String = Regex("[\\u0900-\\u097F\\u0A00-\\u0A7F]+").replace(text) { word(it.value) }

    /** A unit is one consonant (with or without its own vowel), or a standalone vowel. */
    private class Unit(var cons: String?, var vowel: String?, var inherent: Boolean, var nasal: String = "")

    private fun word(w: String): String {
        val gurmukhi = w.any { it in '਀'..'੿' }
        val t = if (gurmukhi) GURMUKHI else DEVANAGARI
        val units = ArrayList<Unit>()
        var double = false
        var i = 0
        while (i < w.length) {
            val c = w[i]
            val next = w.getOrNull(i + 1)
            when {
                next != null && t.nukta == next && t.nuktaCons.containsKey(c) -> {
                    units += Unit(t.nuktaCons[c], null, true); i++
                }
                t.cons.containsKey(c) -> {
                    val u = Unit(t.cons[c], null, true)
                    if (double) { u.cons = u.cons!!.take(1) + u.cons; double = false }
                    units += u
                }
                t.signs.containsKey(c) -> units.lastOrNull()?.let { it.vowel = t.signs[c]; it.inherent = false }
                t.vowels.containsKey(c) -> units += Unit(null, t.vowels[c], false)
                c == t.virama -> units.lastOrNull()?.let { it.inherent = false }
                c in t.nasals -> units.lastOrNull()?.let { u ->
                    if (u.cons != null && u.vowel == null && u.inherent) { u.vowel = "a"; u.inherent = false }
                    u.nasal = "n"
                }
                c == t.addak -> double = true
                t.digits.containsKey(c) -> units += Unit(t.digits[c], null, false)
                else -> units += Unit(c.toString(), null, false)
            }
            i++
        }
        // Schwa deletion, right to left so a later deletion is known when judging earlier ones.
        for (k in units.indices.reversed()) {
            val u = units[k]
            if (u.cons == null || !u.inherent) continue
            val last = k == units.lastIndex
            val prevVowel = k > 0 && units[k - 1].let { it.vowel != null || it.inherent }
            val nextVowel = units.getOrNull(k + 1)?.let { it.cons != null && (it.vowel != null || it.inherent) } == true
            if ((last && units.size > 1) || (k > 0 && prevVowel && nextVowel)) u.inherent = false
        }
        return buildString {
            units.forEachIndexed { k, u ->
                u.cons?.let(::append)
                when {
                    // Long ā / ū closing a word are typed short: "mera", "sohna", "tu".
                    u.vowel == "aa" && u.cons != null && k == units.lastIndex -> append('a')
                    u.vowel == "oo" && u.cons != null && k == units.lastIndex -> append('u')
                    // Nasal e is typed "ein": "mein", "hamein".
                    u.vowel == "e" && u.nasal.isNotEmpty() && k == units.lastIndex -> append("ei")
                    u.vowel != null -> append(u.vowel)
                    u.inherent -> append('a')
                }
                append(u.nasal)
            }
        }
    }

    private class Script(
        val cons: Map<Char, String>, val nuktaCons: Map<Char, String>, val vowels: Map<Char, String>, val signs: Map<Char, String>,
        val virama: Char, val nukta: Char, val nasals: Set<Char>, val addak: Char, val digits: Map<Char, String>,
    )

    private fun digits(zero: Char) = (0..9).associate { (zero + it) to it.toString() }

    private val DEVANAGARI = Script(
        cons = mapOf(
            'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n", 'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
            'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n", 'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
            'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m", 'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
            'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h", 'क़' to "q", 'ख़' to "kh", 'ग़' to "gh", 'ज़' to "z", 'ड़' to "r", 'ढ़' to "rh", 'फ़' to "f",
        ),
        nuktaCons = mapOf('क' to "q", 'ख' to "kh", 'ग' to "gh", 'ज' to "z", 'ड' to "r", 'ढ' to "rh", 'फ' to "f"),
        vowels = mapOf('अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o"),
        signs = mapOf('ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "oo", 'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o"),
        virama = '्', nukta = '़', nasals = setOf('ं', 'ँ'), addak = '\u0000', digits = digits('०'),
    )

    private val GURMUKHI = Script(
        cons = mapOf(
            'ਕ' to "k", 'ਖ' to "kh", 'ਗ' to "g", 'ਘ' to "gh", 'ਙ' to "n", 'ਚ' to "ch", 'ਛ' to "chh", 'ਜ' to "j", 'ਝ' to "jh", 'ਞ' to "n",
            'ਟ' to "t", 'ਠ' to "th", 'ਡ' to "d", 'ਢ' to "dh", 'ਣ' to "n", 'ਤ' to "t", 'ਥ' to "th", 'ਦ' to "d", 'ਧ' to "dh", 'ਨ' to "n",
            'ਪ' to "p", 'ਫ' to "ph", 'ਬ' to "b", 'ਭ' to "bh", 'ਮ' to "m", 'ਯ' to "y", 'ਰ' to "r", 'ਲ' to "l", 'ਵ' to "v", 'ੜ' to "r",
            'ਸ' to "s", 'ਹ' to "h", 'ਸ਼' to "sh", 'ਖ਼' to "kh", 'ਗ਼' to "gh", 'ਜ਼' to "z", 'ਫ਼' to "f", 'ਲ਼' to "l",
        ),
        nuktaCons = mapOf('ਸ' to "sh", 'ਖ' to "kh", 'ਗ' to "gh", 'ਜ' to "z", 'ਫ' to "f", 'ਲ' to "l"),
        vowels = mapOf('ਅ' to "a", 'ਆ' to "aa", 'ਇ' to "i", 'ਈ' to "ee", 'ਉ' to "u", 'ਊ' to "oo", 'ਏ' to "e", 'ਐ' to "ai", 'ਓ' to "o", 'ਔ' to "au"),
        signs = mapOf('ਾ' to "aa", 'ਿ' to "i", 'ੀ' to "i", 'ੁ' to "u", 'ੂ' to "oo", 'ੇ' to "e", 'ੈ' to "ai", 'ੋ' to "o", 'ੌ' to "au"),
        virama = '੍', nukta = '਼', nasals = setOf('ਂ', 'ੰ'), addak = 'ੱ', digits = digits('੦'),
    )
}
