package com.dopdop.bopdop

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** The home-screen icon: one launcher alias per look, exactly one enabled (see the manifest). */
enum class AppIcon(val alias: String, val label: String, val background: Int, val foreground: Int) {
    CLASSIC(".IconClassic", "Classic", R.drawable.ic_launcher_bg, R.drawable.ic_launcher_fg),
    DARK(".IconDark", "Orange glass", R.drawable.ic_launcher_bg_dark, R.drawable.ic_launcher_fg_dark),
    LIGHT(".IconLight", "Light glass", R.drawable.ic_launcher_bg_light, R.drawable.ic_launcher_fg_light),
    HALLOWEEN(".IconHalloween", "Halloween", R.drawable.ic_launcher_bg_halloween, R.drawable.halloween_launcher);

    private fun component(context: Context) = ComponentName(context.packageName, "com.dopdop.bopdop" + alias)

    companion object {
        private const val PREFS = "app_icon"

        /** The look picked in Settings: the waiting one if it hasn't been applied yet. */
        fun chosen(context: Context): AppIcon =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("pending", null)
                ?.let { n -> entries.firstOrNull { it.name == n } } ?: current(context)

        /**
         * Pick [icon]. It's applied when BopDop goes to the background ([applyPending]): the app
         * was opened through the current icon's launcher entry, and switching that entry off
         * while the app is on screen makes Android close it.
         */
        fun choose(context: Context, icon: AppIcon) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (icon == current(context)) prefs.edit().remove("pending").apply()
            else prefs.edit().putString("pending", icon.name).apply()
        }

        /** Apply a waiting icon change; call once the app is no longer on screen. */
        fun applyPending(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val name = prefs.getString("pending", null) ?: return
            prefs.edit().remove("pending").apply()
            entries.firstOrNull { it.name == name }?.let { runCatching { set(context, it) } }
        }

        fun current(context: Context): AppIcon = entries.firstOrNull { icon ->
            when (context.packageManager.getComponentEnabledSetting(icon.component(context))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == CLASSIC
                else -> false
            }
        } ?: CLASSIC

        /** Enable [icon]'s alias first, then turn the others off, so there is never a moment without a launcher entry. */
        fun set(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(icon.component(context), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(it.component(context), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
