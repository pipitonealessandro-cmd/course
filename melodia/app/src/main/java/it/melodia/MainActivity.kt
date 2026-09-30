package it.melodia

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import it.melodia.innertube.SongItem
import it.melodia.ui.AppActions
import it.melodia.ui.LocalActions
import it.melodia.ui.MenuRequest
import it.melodia.ui.Routes
import it.melodia.ui.components.AddToPlaylistDialog
import it.melodia.ui.components.ItemMenuSheet
import it.melodia.ui.player.FullPlayer
import it.melodia.ui.player.MiniPlayer
import it.melodia.ui.screens.ArtistScreen
import it.melodia.ui.screens.BrowseScreen
import it.melodia.ui.screens.HomeScreen
import it.melodia.ui.screens.LibraryListScreen
import it.melodia.ui.screens.LibraryScreen
import it.melodia.ui.screens.LoginScreen
import it.melodia.ui.screens.PlaylistScreen
import it.melodia.ui.screens.SearchScreen
import it.melodia.ui.screens.SettingsScreen
import it.melodia.ui.theme.MelodiaTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MelodiaApp
        app.player.connect()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MelodiaTheme { MelodiaRoot(app) }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "Home", Icons.Default.Home),
    Tab(Routes.SEARCH, "Cerca", Icons.Default.Search),
    Tab(Routes.LIBRARY, "La tua libreria", Icons.Default.LibraryMusic),
)

@Composable
private fun MelodiaRoot(app: MelodiaApp) {
    val nav = rememberNavController()
    val player = app.player
    val snackbar = remember { SnackbarHostState() }
    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf<MenuRequest?>(null) }
    var addToPlaylist by remember { mutableStateOf<SongItem?>(null) }
    val nowPlaying by player.nowPlaying.collectAsState()

    val actions = remember(nav) {
        AppActions(
            nav = nav,
            player = player,
            onShowMenu = { menu = it },
            onAddToPlaylist = { addToPlaylist = it },
            onOpenPlayer = { playerExpanded = true },
        )
    }

    LaunchedEffect(Unit) {
        player.messages.collect { snackbar.showSnackbar(it) }
    }

    CompositionLocalProvider(LocalActions provides actions) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    Column {
                        if (nowPlaying != null) {
                            MiniPlayer(player, onExpand = { playerExpanded = true }, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        BottomBar(nav)
                    }
                },
            ) { padding ->
                NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.fillMaxSize()) {
                    composable(Routes.HOME) { HomeScreen(padding) }
                    composable(Routes.SEARCH) { SearchScreen(padding) }
                    composable(Routes.LIBRARY) { LibraryScreen(padding) }
                    composable(Routes.SETTINGS) { SettingsScreen(padding) }
                    composable(Routes.LOGIN) { LoginScreen(padding) }
                    composable(Routes.PLAYLIST, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                        PlaylistScreen(it.arguments?.getString("id")!!, padding)
                    }
                    composable(Routes.ARTIST, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                        ArtistScreen(it.arguments?.getString("id")!!, padding)
                    }
                    composable(
                        Routes.BROWSE,
                        arguments = listOf(
                            navArgument("id") { type = NavType.StringType },
                            navArgument("params") { type = NavType.StringType; nullable = true; defaultValue = null },
                        ),
                    ) {
                        BrowseScreen(it.arguments?.getString("id")!!, it.arguments?.getString("params"), padding)
                    }
                    composable(Routes.LIBRARY_LIST, arguments = listOf(navArgument("kind") { type = NavType.StringType })) {
                        LibraryListScreen(it.arguments?.getString("kind")!!, padding)
                    }
                }
            }

            AnimatedVisibility(
                visible = playerExpanded && nowPlaying != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                FullPlayer(player, onCollapse = { playerExpanded = false })
            }
        }

        BackHandler(enabled = playerExpanded) { playerExpanded = false }

        menu?.let { req -> ItemMenuSheet(req, actions) { menu = null } }
        addToPlaylist?.let { song -> AddToPlaylistDialog(song) { addToPlaylist = null } }
    }
}

@Composable
private fun BottomBar(nav: androidx.navigation.NavHostController) {
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    NavigationBar(containerColor = Color.Black) {
        TABS.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = Color(0xFFB3B3B3),
                    unselectedTextColor = Color(0xFFB3B3B3),
                ),
            )
        }
    }
}
