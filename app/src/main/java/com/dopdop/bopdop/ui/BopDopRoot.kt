package com.dopdop.bopdop.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dopdop.bopdop.data.LibraryStore
import com.dopdop.bopdop.playback.PlayerConnection
import com.dopdop.bopdop.ui.components.bounceClick
import com.dopdop.bopdop.ui.glass.LiquidMetalBackground
import com.dopdop.bopdop.ui.glass.softShadow
import com.dopdop.bopdop.ui.glass.LocalGlassLevel
import com.dopdop.bopdop.ui.glass.LocalHaze
import com.dopdop.bopdop.ui.glass.LocalReduceMotion
import com.dopdop.bopdop.ui.glass.glass
import com.dopdop.bopdop.ui.glass.rememberArtPalette
import com.dopdop.bopdop.ui.player.MiniPlayer
import com.dopdop.bopdop.ui.player.NowPlaying
import com.dopdop.bopdop.ui.player.SheetHost
import com.dopdop.bopdop.ui.player.ToastHost
import com.dopdop.bopdop.ui.screens.ArtistScreen
import com.dopdop.bopdop.ui.screens.CollectionScreen
import com.dopdop.bopdop.ui.screens.HomeScreen
import com.dopdop.bopdop.ui.screens.LibraryScreen
import com.dopdop.bopdop.ui.screens.LocalCollectionScreen
import com.dopdop.bopdop.ui.screens.MoodScreen
import com.dopdop.bopdop.ui.screens.SearchScreen
import com.dopdop.bopdop.ui.screens.SeeAllScreen
import com.dopdop.bopdop.ui.screens.SettingsScreen
import com.dopdop.bopdop.ui.theme.Bop
import com.dopdop.bopdop.ui.theme.BopType
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

/** Bottom inset screens must leave for the floating chrome (tab bar + mini player). */
val LocalChromePadding = compositionLocalOf { PaddingValues(0.dp) }

@Composable
fun BopDopRoot(openPlayer: StateFlow<Int>) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val app = remember { AppState(nav, scope) }
    val lib by LibraryStore.state.collectAsState()
    val current by PlayerConnection.current.collectAsState()
    val palette = rememberArtPalette(current?.artwork)
    val haze = rememberHazeState()
    val open by openPlayer.collectAsState()

    LaunchedEffect(open) { if (open > 0 && PlayerConnection.current.value != null) app.expandPlayer() }

    // Graphite status-bar icons on metal; white ones over the Now Playing artwork.
    val view = androidx.compose.ui.platform.LocalView.current
    val overMedia = app.player.value > 0.5f
    LaunchedEffect(overMedia) {
        val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !overMedia
            isAppearanceLightNavigationBars = !overMedia
        }
    }

    val navBottom = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val chrome = navBottom + 84.dp + (if (current != null) 70.dp else 0.dp)

    CompositionLocalProvider(
        LocalApp provides app,
        LocalHaze provides haze,
        LocalGlassLevel provides lib.settings.glass,
        LocalReduceMotion provides lib.settings.reduceMotion,
        LocalChromePadding provides PaddingValues(bottom = chrome + 12.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                LiquidMetalBackground(palette, Modifier.fillMaxSize())
                AppNavHost(app)
            }

            BottomChrome(app, hasTrack = current != null, navBottom = navBottom)
            NowPlaying(app)
            SheetHost(app)
            ToastHost(app)
        }
    }

    BackHandler(enabled = app.sheet != null || app.player.targetValue > 0.5f) {
        if (app.sheet != null) app.sheet = null else app.collapsePlayer()
    }
}

@Composable
private fun AppNavHost(app: AppState) {
    val enter = slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { it / 5 } + fadeIn(tween(260))
    val exit = slideOutHorizontally(tween(340, easing = FastOutSlowInEasing)) { -it / 8 } + fadeOut(tween(200))
    val popEnter = slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { -it / 8 } + fadeIn(tween(260))
    val popExit = slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 4 } + fadeOut(tween(200))
    val str = { name: String -> navArgument(name) { type = NavType.StringType; defaultValue = "" } }

    NavHost(
        navController = app.nav,
        startDestination = Routes.HOME,
        enterTransition = { if (targetState.destination.route in Routes.tabs && initialState.destination.route in Routes.tabs) fadeIn(tween(220)) + scaleIn(tween(260), 0.985f) else enter },
        exitTransition = { if (targetState.destination.route in Routes.tabs && initialState.destination.route in Routes.tabs) fadeOut(tween(160)) else exit },
        popEnterTransition = { popEnter },
        popExitTransition = { popExit },
    ) {
        composable(Routes.HOME) { HomeScreen() }
        composable(Routes.SEARCH) { SearchScreen() }
        composable(Routes.LIBRARY) { LibraryScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
        composable(Routes.COLLECTION, listOf(str("id"), str("kind"), str("title"), str("sub"), str("art"))) { e ->
            val a = e.arguments!!
            CollectionScreen(a.getString("id")!!, a.getString("kind")!!, a.getString("title")!!, a.getString("sub")!!, a.getString("art")!!)
        }
        composable(Routes.ARTIST, listOf(str("id"), str("name"), str("art"))) { e ->
            val a = e.arguments!!
            ArtistScreen(a.getString("id")!!, a.getString("name")!!, a.getString("art")!!)
        }
        composable(Routes.LOCAL, listOf(str("id"))) { e -> LocalCollectionScreen(e.arguments!!.getString("id")!!) }
        composable(Routes.MOOD, listOf(str("title"), str("params"))) { e ->
            MoodScreen(e.arguments!!.getString("title")!!, e.arguments!!.getString("params")!!)
        }
        composable(Routes.SEE_ALL, listOf(str("key"))) { e -> SeeAllScreen(e.arguments!!.getString("key")!!) }
    }
}

@Composable
private fun BottomChrome(app: AppState, hasTrack: Boolean, navBottom: Dp) {
    val p = app.player.value
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = p * 260.dp.toPx()
                alpha = (1f - p * 1.6f).coerceIn(0f, 1f)
            }
            .padding(bottom = navBottom + 10.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        AnimatedVisibility(
            visible = hasTrack,
            enter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = 500f)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            MiniPlayer(app, Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp))
        }
        TabBar(app)
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

/** Floating capsule; the selection "lens" glides on a spring and stretches while moving, like a liquid droplet. */
@Composable
private fun TabBar(app: AppState) {
    val tabs = remember {
        listOf(Tab(Routes.HOME, "Home", Icons.Rounded.Home), Tab(Routes.SEARCH, "Search", Icons.Rounded.Search), Tab(Routes.LIBRARY, "Library", Icons.Rounded.LibraryMusic))
    }
    val entry by app.nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    // Remember the last tab root we were under so pushed pages keep their tab lit.
    val lastTab = remember { androidx.compose.runtime.mutableStateOf(Routes.HOME) }
    if (route in Routes.tabs) lastTab.value = route!!
    val selected = tabs.indexOfFirst { it.route == lastTab.value }.coerceAtLeast(0)
    val lens = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected) { lens.animateTo(selected.toFloat(), spring(dampingRatio = 0.62f, stiffness = 420f)) }

    BoxWithConstraints(
        Modifier
            .padding(horizontal = 40.dp)
            .fillMaxWidth()
            .height(64.dp)
            .glass(RoundedCornerShape(32.dp), emphasis = 1.1f, shadow = 18.dp),
    ) {
        val slot = maxWidth / tabs.size
        val stretch = 1f + (abs(lens.velocity) / 9f).coerceAtMost(0.35f)
        Box(
            Modifier
                .offset { IntOffset(((slot * lens.value).toPx() + 5.dp.toPx()).roundToInt(), 5.dp.roundToPx()) }
                .width(slot - 10.dp)
                .fillMaxHeight()
                .padding(bottom = 10.dp)
                .graphicsLayer { scaleX = stretch; scaleY = 1f / (1f + (stretch - 1f) * 0.5f) }
                .softShadow(RoundedCornerShape(27.dp), 10.dp, alpha = 0.14f, dy = 3.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(Color.White.copy(alpha = 0.92f)),
        )
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, t ->
                val on = i == selected
                Column(
                    Modifier.weight(1f).fillMaxHeight().bounceClick(0.9f) {
                        if (route == t.route) {
                            // Re-tap: pop to the tab root.
                            app.nav.popBackStack(t.route, inclusive = false)
                        } else app.goTab(t.route)
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(t.icon, t.label, tint = if (on) Bop.Ink else Bop.Text2, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(t.label, style = BopType.Micro.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), color = if (on) Bop.Ink else Bop.Text3)
                }
            }
        }
    }
}

/** Common top padding for screens drawing under the status bar. */
@Composable
fun Modifier.statusBarsPad(extra: Dp = 0.dp) = this.windowInsetsPadding(WindowInsets.statusBars).padding(top = extra)
