package com.nakshatra.heritage.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nakshatra.heritage.AppViewModel
import com.nakshatra.heritage.ui.screens.ArchiveScreen
import com.nakshatra.heritage.ui.screens.AskScreen
import com.nakshatra.heritage.ui.screens.CategoryScreen
import com.nakshatra.heritage.ui.screens.ExploreScreen
import com.nakshatra.heritage.ui.screens.HomeScreen
import com.nakshatra.heritage.ui.screens.MapScreen
import com.nakshatra.heritage.ui.screens.SettingsScreen
import com.nakshatra.heritage.ui.screens.TopicScreen

private class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab("explore", "Explore", Icons.Outlined.Explore, Icons.Filled.Explore),
    Tab("ask", "Ask", Icons.Outlined.GraphicEq, Icons.Filled.GraphicEq),
    Tab("map", "Map", Icons.Outlined.Place, Icons.Filled.Place),
    Tab("archive", "Archive", Icons.Outlined.AutoStories, Icons.Filled.AutoStories),
)

/** Which tab a destination belongs to, so the bar stays lit on detail screens. */
private fun tabOf(route: String?): String? = when {
    route == null -> null
    route.startsWith("category") -> "explore"
    route.startsWith("map") -> "map"
    else -> TABS.firstOrNull { it.route == route }?.route
}

private fun NavHostController.openTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun NakshatraRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val current = tabOf(route)
    val showBar = route != "settings"

    val kb by vm.kb.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val voice by vm.voice.collectAsStateWithLifecycle()
    val levels by vm.levels.collectAsStateWithLifecycle()
    val speaking by vm.speaking.collectAsStateWithLifecycle()
    val ttsAvailable by vm.ttsAvailable.collectAsStateWithLifecycle()
    val check by vm.check.collectAsStateWithLifecycle()

    val openSettings = { nav.navigate("settings") { launchSingleTop = true } }
    val openTopic = { id: String -> nav.navigate("topic/$id") }
    // Every "Ask Nakshatra" button goes through the same answer engine, on the Ask tab.
    val ask = { question: String ->
        vm.ask(question)
        nav.navigate("ask") {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // A rail beside the content in landscape and on tablets, a bar below it otherwise.
        val rail = maxWidth >= 600.dp && maxWidth > maxHeight
        val scheme = MaterialTheme.colorScheme
        Scaffold(
            containerColor = scheme.background,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (showBar && !rail) {
                    NavigationBar(containerColor = scheme.surfaceContainerLow) {
                        TABS.forEach { tab ->
                            NavigationBarItem(
                                selected = current == tab.route,
                                onClick = { nav.openTab(tab.route) },
                                icon = { Icon(if (current == tab.route) tab.selectedIcon else tab.icon, null) },
                                label = { Text(tab.label, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = scheme.onPrimary, indicatorColor = scheme.primary,
                                    selectedTextColor = scheme.primary,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            // The bottom bar pads itself for the system navigation bar; without it the content must.
            val systemBars = if (showBar && !rail) WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal) else WindowInsets.navigationBars
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).windowInsetsPadding(systemBars)) {
                if (showBar && rail) {
                    NavigationRail(containerColor = scheme.surfaceContainerLow) {
                        TABS.forEach { tab ->
                            NavigationRailItem(
                                selected = current == tab.route,
                                onClick = { nav.openTab(tab.route) },
                                icon = { Icon(if (current == tab.route) tab.selectedIcon else tab.icon, null) },
                                label = { Text(tab.label, maxLines = 1) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = scheme.onPrimary, indicatorColor = scheme.primary,
                                    selectedTextColor = scheme.primary,
                                ),
                            )
                        }
                    }
                }
                Box(
                    Modifier.weight(1f).fillMaxSize(),
                ) {
                    NavHost(
                        nav, startDestination = "home",
                        enterTransition = { fadeIn() }, exitTransition = { fadeOut() },
                        popEnterTransition = { fadeIn() }, popExitTransition = { fadeOut() },
                    ) {
                        composable("home") {
                            HomeScreen(
                                state = kb, voice = voice, level = levels.last(), serverUrl = settings.serverUrl,
                                onRefresh = vm::refresh,
                                onOpenAsk = { nav.openTab("ask") }, onOpenExplore = { nav.openTab("explore") },
                                onOpenArchive = { nav.openTab("archive") }, onOpenSettings = openSettings,
                                onOpenTopic = { openTopic(it.id) }, onOpenCategory = { nav.navigate("category/${it.id}") },
                            )
                        }
                        composable("explore") {
                            ExploreScreen(kb, vm::refresh, openSettings) { nav.navigate("category/${it.id}") }
                        }
                        composable("category/{id}", listOf(navArgument("id") { type = NavType.StringType })) { e ->
                            CategoryScreen(kb, e.arguments?.getString("id").orEmpty(), { nav.popBackStack() }, vm::refresh, openSettings) { openTopic(it.id) }
                        }
                        composable("topic/{id}", listOf(navArgument("id") { type = NavType.StringType })) { e ->
                            TopicScreen(
                                state = kb, topicId = e.arguments?.getString("id").orEmpty(), serverUrl = settings.serverUrl,
                                speaking = speaking, ttsAvailable = ttsAvailable,
                                onBack = { nav.popBackStack() }, onRefresh = vm::refresh, onOpenSettings = openSettings,
                                onReadAloud = vm::toggleReadAloud, onAsk = ask,
                                onShowOnMap = { nav.navigate("map?focus=${it.id}") },
                                onOpenTopic = openTopic,
                            )
                        }
                        composable("ask") {
                            AskScreen(
                                kb = kb.kb, voice = voice, levels = levels, serverUrl = settings.serverUrl,
                                ttsOn = settings.ttsOn, ttsAvailable = ttsAvailable,
                                onAsk = vm::ask, onSetTts = vm::setTts, onOpenTopic = openTopic, onOpenSettings = openSettings,
                            )
                        }
                        composable("map") {
                            MapScreen(kb, null, vm::refresh, openSettings, ask) { openTopic(it.id) }
                        }
                        composable(
                            "map?focus={focus}",
                            listOf(navArgument("focus") { type = NavType.StringType; nullable = true }),
                        ) { e ->
                            MapScreen(kb, e.arguments?.getString("focus"), vm::refresh, openSettings, ask) { openTopic(it.id) }
                        }
                        composable("archive") {
                            ArchiveScreen(
                                state = kb, speaking = speaking, ttsAvailable = ttsAvailable,
                                onRefresh = vm::refresh, onOpenSettings = openSettings,
                                onReadAloud = vm::toggleReadAloud, onAsk = ask, onOpenTopic = { openTopic(it.id) },
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                settings = settings, check = check, ttsAvailable = ttsAvailable,
                                onBack = { nav.popBackStack() },
                                onSaveServer = vm::saveServer, onCheckServer = vm::checkServer,
                                onSetTts = vm::setTts, onSetTheme = vm::setTheme,
                            )
                        }
                    }
                    // Scrolling content passes under the status bar; keep the clock and icons readable.
                    // Topic and theme pages open on a coloured header that runs to the top edge instead.
                    if (route?.startsWith("topic") != true && route?.startsWith("category") != true) {
                        Box(
                            Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                                .background(scheme.background.copy(alpha = 0.96f)),
                        )
                    }
                }
            }
        }
    }
}
