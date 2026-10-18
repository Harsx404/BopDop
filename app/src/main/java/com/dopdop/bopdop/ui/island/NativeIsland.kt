package com.dopdop.bopdop.ui.island

import android.os.Build
import java.util.Locale

/**
 * Phones whose system has its own island-style surface: Samsung's Now Bar (One UI 7), OnePlus
 * Live Alerts and OPPO Fluid Cloud (OxygenOS / ColorOS 15), vivo and iQOO's Origin Island
 * (OriginOS), Xiaomi's Super Island (HyperOS 3). They only take ordinary media notifications from
 * apps they list, so BopDop reaches them with an Android 16 Live Update (see LiveUpdate); this
 * names the phone's island in Settings.
 */
object NativeIsland {
    /** The name of the phone's own island, or null when it has none we know of. */
    val name: String? by lazy { detect(Build.MANUFACTURER, Build.BRAND, Build.VERSION.SDK_INT) }

    internal fun detect(manufacturer: String?, brand: String?, sdk: Int): String? {
        val m = "${manufacturer.orEmpty()} ${brand.orEmpty()}".lowercase(Locale.ROOT)
        return when {
            "samsung" in m && sdk >= 35 -> "Now Bar"
            "oneplus" in m && sdk >= 35 -> "Live Alerts"
            ("oppo" in m || "realme" in m) && sdk >= 35 -> "Fluid Cloud"
            ("vivo" in m || "iqoo" in m) && sdk >= 35 -> "Origin Island"
            ("xiaomi" in m || "redmi" in m || "poco" in m) && sdk >= 36 -> "Super Island"
            else -> null
        }
    }
}
