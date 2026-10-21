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
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.layout
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

/** Bottom inset screens must leave for the floating chrome (tab bar + mini player). */
val LocalChromePadding = compositionLocalOf { PaddingValues(0.dp) }

@Composable
fun BopDopRoot(openPlayer: StateFlow<Int>, sharedLink: kotlinx.coroutines.flow.MutableStateFlow<String?>, quick: kotlinx.coroutines.flow.MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val app = remember { AppState(nav, scope) }
    val lib by LibraryStore.state.collectAsState()
    val current by PlayerConnection.current.collectAsState()
    val palette = rememberArtPalette(current?.artwork)
    val haze = rememberHazeState()
    val open by openPlayer.collectAsState()

    LaunchedEffect(open) { if (open > 0 && PlayerConnection.current.value != null) app.expandPlayer() }

    // bopdop://share/… opened from a camera app, a chat, or the browser.
    val link by sharedLink.collectAsState()
    LaunchedEffect(link) { link?.let { sharedLink.value = null; app.collapsePlayer(); app.openShared(it) } }
    // Quick mixes widget: start the mix (and show the player) or open the page.
    val quickAction by quick.collectAsState()
    LaunchedEffect(quickAction) {
        val q = quickAction ?: return@LaunchedEffect
        quick.value = null
        val library = com.dopdop.bopdop.data.LibraryStore.state.value
        when (q) {
            com.dopdop.bopdop.MainActivity.QUICK_LIKED ->
                if (library.liked.isEmpty()) app.toast("Like a few songs first") else { PlayerConnection.play(library.liked, context = "Liked Songs", shuffle = true); app.expandPlayer() }
            com.dopdop.bopdop.MainActivity.QUICK_DISCOVER ->
                library.history.firstOrNull()?.let { PlayerConnection.startRadio(it, "Discovery Station"); app.expandPlayer() } ?: app.goTab(Routes.EXPLORE)
            com.dopdop.bopdop.MainActivity.QUICK_REPLAY -> { app.collapsePlayer(); app.nav.navigate(Routes.WRAPPED) }
            com.dopdop.bopdop.MainActivity.QUICK_SEARCH -> { app.collapsePlayer(); app.nav.navigate(Routes.SEARCH) }
        }
    }

    // Keep the signed-in profile (name / photo) fresh.
    LaunchedEffect(Unit) {
        if (com.dopdop.bopdop.data.account.AccountStore.signedIn) runCatching { com.dopdop.bopdop.data.ytm.YtMusic.accountInfo() }.getOrNull()
            ?.let(com.dopdop.bopdop.data.account.AccountStore::setProfile)
    }

    // Graphite status-bar icons on light metal; white ones on dark metal and over the artwork.
    val view = androidx.compose.ui.platform.LocalView.current
    val overMedia = app.player.value > 0.5f
    val darkTheme = Bop.dark
    LaunchedEffect(overMedia, darkTheme) {
        val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !overMedia && !darkTheme
            isAppearanceLightNavigationBars = !overMedia && !darkTheme
        }
    }

    val navBottom = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val chrome = navBottom + 84.dp + (if (current != null) 70.dp else 0.dp)
    val bounceScope = androidx.compose.runtime.rememberCoroutineScope()
    val maxPull = with(LocalDensity.current) { 140.dp.toPx() }
    val bounceFactory = androidx.compose.runtime.remember(bounceScope, maxPull) { com.dopdop.bopdop.ui.components.BounceOverscrollFactory(bounceScope, maxPull) }

    CompositionLocalProvider(
        LocalApp provides app,
        LocalHaze provides haze,
        LocalGlassLevel provides lib.settings.glass.also { Bop.clear = it == com.dopdop.bopdop.data.GlassLevel.CLEAR },
        LocalReduceMotion provides lib.settings.reduceMotion,
        com.dopdop.bopdop.ui.festive.LocalFestive provides com.dopdop.bopdop.ui.festive.rememberFestive(lib.settings.festiveLevel, lib.settings.festivePick),
        com.dopdop.bopdop.ui.glass.LocalGlassLight provides com.dopdop.bopdop.ui.glass.rememberTiltLight(!lib.settings.reduceMotion),
        LocalChromePadding provides PaddingValues(bottom = chrome + 12.dp),
        // Rubber-band ends on every list (F39); reduced motion keeps Android's own stretch.
        androidx.compose.foundation.LocalOverscrollFactory provides if (lib.settings.reduceMotion) androidx.compose.foundation.LocalOverscrollFactory.current
            else bounceFactory,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                LiquidMetalBackground(palette, Modifier.fillMaxSize())
                TabsHost(app)
                AppNavHost(app)
            }

            BottomChrome(app, hasTrack = current != null, navBottom = navBottom)
            NowPlaying(app)
            com.dopdop.bopdop.ui.player.HoldMenuHost(app)
            SheetHost(app)
            app.shareTrack?.let { com.dopdop.bopdop.ui.share.ShareStudio(it) { app.shareTrack = null } }
            ToastHost(app)
        }
    }

    // A quiet look for a newer release, once the app has settled.
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(4_000); com.dopdop.bopdop.data.Updates.check() }
    // Back in the app: look for updates again (at most every 6 hours), and finish an update that
    // was waiting for the "Install unknown apps" switch.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) app.scope.launch {
                com.dopdop.bopdop.data.UpdateInstaller.resume(ctx.applicationContext)
                com.dopdop.bopdop.data.Updates.check()
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    BackHandler(enabled = app.sheet != null || app.player.targetValue > 0.5f) {
        if (app.sheet != null) app.sheet = null else app.collapsePlayer()
    }
}

/**
 * The now-playing island around the camera, while the full player is closed. Only on screens
 * with a camera cutout at the top; tap to expand, tap outside to shrink, long-press to open the
 * player.
 */
@Composable
private fun InAppIsland(app: AppState, enabled: Boolean) {
    if (!enabled) return
    val cut = com.dopdop.bopdop.ui.island.rememberCutout() ?: return
    val track by PlayerConnection.current.collectAsState()
    val playing by PlayerConnection.isPlaying.collectAsState()
    val t = track ?: return
    if (app.player.value > 0.02f) return
    var expanded by remember { androidx.compose.runtime.mutableStateOf(false) }
    if (expanded) Box(
        Modifier.fillMaxSize().clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { expanded = false },
    )
    val model = com.dopdop.bopdop.ui.island.IslandModel(
        t, playing, { PlayerConnection.position }, { PlayerConnection.duration },
        toggle = { PlayerConnection.toggle() }, next = { PlayerConnection.next() }, previous = { PlayerConnection.previous() },
        open = { expanded = false; app.expandPlayer() },
    )
    com.dopdop.bopdop.ui.island.IslandPill(model, cut, expanded, { expanded = it }, androidx.compose.ui.platform.LocalView.current.rootView.width)
}

/** Whether the tab this content belongs to is on screen; hidden tabs pause their idle animations. */
val LocalTabActive = androidx.compose.runtime.compositionLocalOf { true }

/**
 * Home, Explore and Library, kept alive under the navigation stack. A page opened from a tab is
 * pushed over them; closing it (back button or gesture) just reveals the tab that was there,
 * already built and scrolled where you left it, so the close animation never stalls while a page
 * rebuilds. Tabs are built the first time they're opened and then kept; only the shown one (and
 * one fading out) is laid out, drawn and touchable.
 */
@Composable
private fun TabsHost(app: AppState) {
    val entry by app.nav.currentBackStackEntryAsState()
    val visible by app.nav.visibleEntries.collectAsState()
    val onTabs = (entry?.destination?.route ?: Routes.TABS) == Routes.TABS
    val tabsVisible = visible.any { it.destination.route == Routes.TABS }
    // Show the tabs while they're the current page, and while a back gesture or pop is
    // revealing them; hide them as soon as a page is pushed over them.
    var shown by remember { androidx.compose.runtime.mutableStateOf(true) }
    var wasOnTabs by remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(onTabs, tabsVisible) {
        shown = when {
            onTabs -> true
            !tabsVisible -> false
            else -> !wasOnTabs
        }
        wasOnTabs = onTabs
    }
    val reveal by androidx.compose.animation.core.animateFloatAsState(
        if (shown) 1f else 0f, if (shown) spring(dampingRatio = 0.9f, stiffness = 380f) else tween(160), label = "tabsReveal",
    )
    val opened = remember { androidx.compose.runtime.mutableStateListOf(Routes.HOME) }
    if (app.tab !in opened) opened += app.tab

    BackHandler(enabled = onTabs && app.tab != Routes.HOME && app.sheet == null && app.player.targetValue < 0.5f) {
        app.tab = Routes.HOME
    }

    Box(
        Modifier.fillMaxSize().graphicsLayer {
            alpha = reveal
            // Coming back, the tab settles up from slightly smaller, under the closing page.
            val s = 0.96f + 0.04f * reveal
            scaleX = s; scaleY = s
        },
    ) {
        opened.forEach { route ->
            val selected = route == app.tab
            val a by androidx.compose.animation.core.animateFloatAsState(if (selected) 1f else 0f, tween(if (selected) 220 else 160), label = "tab")
            val live = a > 0f && reveal > 0f
            androidx.compose.runtime.key(route) {
                CompositionLocalProvider(LocalTabActive provides (selected && shown)) {
                    Box(
                        Modifier.fillMaxSize()
                            // Kept composed but not placed while hidden: no drawing, no touches.
                            .layout { m, c -> val p = m.measure(c); layout(p.width, p.height) { if (live) p.place(0, 0) } }
                            .graphicsLayer { alpha = a; val s = 0.985f + 0.015f * a; scaleX = s; scaleY = s },
                    ) {
                        when (route) {
                            Routes.HOME -> HomeScreen()
                            Routes.EXPLORE -> com.dopdop.bopdop.ui.screens.ExploreScreen()
                            Routes.LIBRARY -> LibraryScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(app: AppState) {
    // Short and decisive: the page underneath fades out fast, so both pages (each with
    // its own glass and artwork) only render together for a few frames.
    // No delayed starts: with predictive back the pop runs as you swipe, and a delay reads as a stall.
    val enter = slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 6 } + fadeIn(tween(220))
    val exit = slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it / 10 } + fadeOut(tween(160))
    val popEnter = slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it / 10 } + fadeIn(tween(220))
    // Closing a page: it slides off to the right, shrinking a touch, as the tabs come back underneath.
    val popExit = slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 4 } + fadeOut(tween(200)) + scaleOut(tween(260), 0.96f)
    fun fromTabs(e: androidx.navigation.NavBackStackEntry) = e.destination.route == Routes.TABS
    val str = { name: String -> navArgument(name) { type = NavType.StringType; defaultValue = "" } }

    NavHost(
        navController = app.nav,
        startDestination = Routes.TABS,
        // The tabs root is an empty page: the tabs themselves are drawn by TabsHost underneath.
        enterTransition = { if (fromTabs(targetState)) EnterTransition.None else enter },
        exitTransition = { if (fromTabs(initialState)) ExitTransition.None else exit },
        popEnterTransition = { if (fromTabs(targetState)) EnterTransition.None else popEnter },
        popExitTransition = { popExit },
    ) {
        composable(Routes.TABS) { Box(Modifier.fillMaxSize()) }
        composable(Routes.SEARCH) { SearchScreen() }
        composable(Routes.TOGETHER, listOf(str("code"))) { e -> com.dopdop.bopdop.ui.screens.TogetherScreen(e.arguments!!.getString("code")!!) }
        composable(Routes.WRAPPED) { com.dopdop.bopdop.ui.screens.WrappedScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
        composable(Routes.EQ) { com.dopdop.bopdop.ui.screens.EqualizerScreen() }
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
        composable(Routes.SIGN_IN) { com.dopdop.bopdop.ui.screens.SignInScreen() }
        composable(Routes.SHARE_QR, listOf(str("key"))) { e -> com.dopdop.bopdop.ui.share.ShareQrScreen(e.arguments!!.getString("key")!!) }
        composable(Routes.IMPORT, listOf(str("key"))) { e -> com.dopdop.bopdop.ui.share.ImportScreen(e.arguments!!.getString("key")!!) }
        composable(Routes.SCAN) { com.dopdop.bopdop.ui.share.ScanScreen() }
        composable(Routes.SPOTIFY) { com.dopdop.bopdop.ui.screens.SpotifyLibraryScreen() }
        composable(Routes.UPDATE) { com.dopdop.bopdop.ui.screens.UpdateScreen() }
        composable(Routes.SPOTIFY_LOGIN) { com.dopdop.bopdop.ui.screens.SpotifyLoginScreen() }
        composable(Routes.SPOTIFY_PLAYLIST, listOf(str("id"), str("name"), str("art"))) { e ->
            val a = e.arguments!!
            com.dopdop.bopdop.ui.screens.SpotifyPlaylistScreen(a.getString("id")!!, a.getString("name")!!, a.getString("art")!!)
        }
    }
}

@Composable
private fun BottomChrome(app: AppState, hasTrack: Boolean, navBottom: Dp) {
    // Full-screen tools (share code, scanner, sign-in) get the whole screen.
    val route = app.nav.currentBackStackEntryAsState().value?.destination?.route
    val away by androidx.compose.animation.core.animateFloatAsState(
        if (route == Routes.SHARE_QR || route == Routes.SCAN || route == Routes.SIGN_IN || route == Routes.SPOTIFY_LOGIN || route == Routes.WRAPPED) 1f else 0f, tween(260), label = "chromeAway",
    )
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Full-screen tools: the chrome sinks away.
                translationY = away * 160.dp.toPx()
                val sc = 1f - 0.06f * away
                scaleX = sc; scaleY = sc
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                // The player: the chrome holds still (its cover is where the player genies into)
                // and fades in only as the player arrives.
                alpha = minOf(1f - away * 1.8f, 1f - app.player.value * 3f).coerceIn(0f, 1f)
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

/**
 * Floating Liquid Glass capsule. The selection is a clear glass lens that
 * refracts the tab icons beneath it: tap a tab and it glides there on a spring,
 * or press and drag it across the bar — it swells while held, stretches with
 * speed, and snaps to the nearest tab on release (iOS 26 style).
 */
@OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)
@Composable
private fun TabBar(app: AppState) {
    val tabs = remember {
        listOf(Tab(Routes.HOME, "Home", Icons.Rounded.Home), Tab(Routes.EXPLORE, "Explore", Icons.Rounded.Explore), Tab(Routes.LIBRARY, "Library", Icons.Rounded.LibraryMusic))
    }
    // The tab under any pushed pages stays lit.
    val selected = tabs.indexOfFirst { it.route == app.tab }.coerceAtLeast(0)
    val lens = remember { Animatable(selected.toFloat()) }
    var dragging by remember { androidx.compose.runtime.mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(selected) { if (!dragging) lens.animateTo(selected.toFloat(), spring(dampingRatio = 0.62f, stiffness = 420f)) }
    val held by androidx.compose.animation.core.animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 520f), label = "held")
    val iconsHaze = rememberHazeState()
    val level = com.dopdop.bopdop.ui.glass.LocalGlassLevel.current
    val light by com.dopdop.bopdop.ui.glass.LocalGlassLight.current
    val lensShape = RoundedCornerShape(27.dp)
    // At rest the lens is a milky pill marking the tab; held, it clears into a magnifying lens.
    val restTint = if (level == com.dopdop.bopdop.data.GlassLevel.CLEAR) Color.Transparent else Color.White.copy(alpha = 0.78f)
    val lensTint = androidx.compose.ui.graphics.lerp(restTint, Color.Transparent, held)
    val lensStyle = remember(level, light, lensTint) {
        com.dopdop.bopdop.ui.glass.liquidStyle(com.dopdop.bopdop.data.GlassLevel.CLEAR, light, lensShape, lensTint, strong = true)
    }

    fun go(i: Int) {
        app.goTab(tabs[i].route)
    }

    BoxWithConstraints(
        Modifier
            .padding(horizontal = 40.dp)
            .fillMaxWidth()
            .height(64.dp)
            .glass(RoundedCornerShape(32.dp), emphasis = 1.1f, shadow = 18.dp),
    ) {
        val slot = maxWidth / tabs.size
        val slotPx = with(LocalDensity.current) { slot.toPx() }
        val stretch = 1f + (abs(lens.velocity) / 9f).coerceAtMost(0.35f)
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        dragging = true
                        scope.launch { lens.animateTo((o.x / slotPx - 0.5f).coerceIn(0f, tabs.size - 1f), spring(dampingRatio = 0.7f, stiffness = 900f)) }
                    },
                    onDragEnd = {
                        dragging = false
                        val i = lens.value.roundToInt().coerceIn(tabs.indices)
                        scope.launch { lens.animateTo(i.toFloat(), spring(dampingRatio = 0.6f, stiffness = 420f)) }
                        go(i)
                    },
                    onDragCancel = { dragging = false; scope.launch { lens.animateTo(selected.toFloat(), spring(dampingRatio = 0.6f, stiffness = 420f)) } },
                ) { change, d ->
                    change.consume()
                    scope.launch { lens.snapTo((lens.value + d / slotPx).coerceIn(-0.15f, tabs.size - 0.85f)) }
                }
            },
        ) {
            // Icons — also the source the lens refracts while it's being dragged.
            TabIcons(tabs, selected, Modifier.fillMaxSize().hazeSource(iconsHaze), clickable = false, alpha = { held }) {}
            // The lens: real refractive glass over the icons.
            Box(
                Modifier
                    .offset { IntOffset(((slot * lens.value).toPx() + 4.dp.toPx()).roundToInt(), 4.dp.roundToPx()) }
                    .width(slot - 8.dp)
                    .height(56.dp)
                    .graphicsLayer {
                        val grow = 1f + 0.16f * held
                        scaleX = stretch * grow
                        scaleY = grow / (1f + (stretch - 1f) * 0.5f)
                    }
                    .softShadow(lensShape, 10.dp, alpha = 0.12f * (1f - held), dy = 3.dp)
                    .hazeGlass(HazeInput.Sources(iconsHaze), lensStyle, expandLayerBounds = false),
            )
            // Crisp icons on top at rest; they melt away while dragging so the lens can magnify.
            TabIcons(tabs, selected, Modifier.fillMaxSize(), clickable = true, alpha = { 1f - held }) { go(it) }
        }
    }
}

@Composable
private fun TabIcons(tabs: List<Tab>, selected: Int, modifier: Modifier, clickable: Boolean, alpha: () -> Float, onTap: (Int) -> Unit) {
    Row(modifier) {
        tabs.forEachIndexed { i, t ->
            val on = i == selected
            Column(
                Modifier.weight(1f).fillMaxHeight().then(if (clickable) Modifier.bounceClick(0.9f) { onTap(i) } else Modifier)
                    .graphicsLayer { this.alpha = alpha() },
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

/** Common top padding for screens drawing under the status bar. */
@Composable
fun Modifier.statusBarsPad(extra: Dp = 0.dp) = this.windowInsetsPadding(WindowInsets.statusBars).padding(top = extra)
