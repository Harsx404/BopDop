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
import com.dopdop.bopdop.ui.theme.BopDopTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Bumped when the notification is tapped so the root expands the player. */
    val openPlayer = MutableStateFlow(0)
    /** A `bopdop://share/…` link waiting for the UI to open it. */
    val sharedLink = MutableStateFlow<String?>(null)

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
            BopDopTheme {
                BopDopRoot(openPlayer, sharedLink)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) openPlayer.value++
        intent?.data?.takeIf { it.scheme == "bopdop" && it.host == "share" }?.let { sharedLink.value = it.toString() }
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
    }
}
