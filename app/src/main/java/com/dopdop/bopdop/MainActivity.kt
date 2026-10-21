package com.dopdop.bopdop

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.dopdop.bopdop.playback.PlayerConnection
import androidx.lifecycle.lifecycleScope
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.ui.BopDopRoot
import com.dopdop.bopdop.ui.applyRefreshRate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.dopdop.bopdop.ui.theme.BopDopTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Bumped when the notification is tapped so the root expands the player. */
    val openPlayer = MutableStateFlow(0)
    /** A `bopdop://share/…` link waiting for the UI to open it. */
    val sharedLink = MutableStateFlow<String?>(null)
    /** A quick action from the Quick mixes widget, waiting for the UI. */
    val quick = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        PlayerConnection.connect(applicationContext)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        handle(intent)
        lifecycleScope.launch {
            LibraryStore.state.map { it.settings.refreshRate }.distinctUntilChanged().collect { applyRefreshRate(it) }
        }
        setContent {
            // Light or dark, from Settings (Auto follows the phone); set before anything reads the tokens.
            val mode by LibraryStore.state.map { it.settings.themeMode }.collectAsState(LibraryStore.settings.themeMode)
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            com.dopdop.bopdop.ui.theme.Bop.dark = when (mode) {
                com.dopdop.bopdop.data.ThemeMode.DARK -> true
                com.dopdop.bopdop.data.ThemeMode.LIGHT -> false
                com.dopdop.bopdop.data.ThemeMode.SYSTEM -> systemDark
            }
            BopDopTheme {
                BopDopRoot(openPlayer, sharedLink, quick)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        com.dopdop.bopdop.playback.AppVisibility.set(true)
    }

    override fun onStop() {
        super.onStop()
        com.dopdop.bopdop.playback.AppVisibility.set(false)
        // A new home-screen icon is switched in only now: doing it while BopDop is on screen
        // closes the app (see AppIcon.choose).
        if (!isChangingConfigurations) AppIcon.applyPending(applicationContext)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) openPlayer.value++
        intent?.getStringExtra(EXTRA_QUICK)?.let { quick.value = it }
        intent?.data?.takeIf { it.scheme == "bopdop" && (it.host == "share" || it.host == "together") }?.let { sharedLink.value = it.toString() }
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
        const val EXTRA_QUICK = "quick"
        const val QUICK_LIKED = "liked"
        const val QUICK_DISCOVER = "discover"
        const val QUICK_REPLAY = "replay"
        const val QUICK_SEARCH = "search"
    }
}
