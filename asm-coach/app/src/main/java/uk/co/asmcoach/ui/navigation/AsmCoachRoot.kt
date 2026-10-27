package uk.co.asmcoach.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.MaterialTheme
import uk.co.asmcoach.AppContainer
import uk.co.asmcoach.ui.jobs.JobsScreen
import uk.co.asmcoach.ui.prep.PrepScreen
import uk.co.asmcoach.ui.roleplay.RoleplayScreen
import uk.co.asmcoach.ui.settings.SettingsScreen
import uk.co.asmcoach.ui.strategy.StrategyScreen
import uk.co.asmcoach.ui.theme.AsmTheme

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Jobs("jobs", "Jobs", Icons.Rounded.WorkOutline),
    Prep("prep", "Prep", Icons.Rounded.AutoStories),
    Roleplay("roleplay", "Roleplay", Icons.Rounded.RecordVoiceOver),
}

private object Routes {
    const val STRATEGY = "strategy"
    const val SETTINGS = "settings"
}

@Composable
fun AsmCoachRoot(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    val onTopLevel = Tab.entries.any { tab -> current?.hierarchy?.any { it.route == tab.route } == true }
    val c = AsmTheme.colors

    Scaffold(
        containerColor = c.background,
        bottomBar = {
            if (onTopLevel) {
                Column {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
                    NavigationBar(containerColor = c.surface, tonalElevation = 0.dp) {
                        Tab.entries.forEach { tab ->
                            val selected = current?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { nav.switchTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = c.accent,
                                    selectedTextColor = c.accent,
                                    unselectedIconColor = c.textTertiary,
                                    unselectedTextColor = c.textTertiary,
                                    indicatorColor = Color.Transparent,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = Tab.Jobs.route,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            val openSettings = { nav.navigate(Routes.SETTINGS) }
            composable(Tab.Jobs.route) {
                JobsScreen(
                    container = container,
                    onOpenStrategy = { nav.navigate(Routes.STRATEGY) },
                    onOpenSettings = openSettings,
                )
            }
            composable(Tab.Prep.route) { PrepScreen(container, onOpenSettings = openSettings) }
            composable(Tab.Roleplay.route) { RoleplayScreen(container, onOpenSettings = openSettings) }
            composable(
                Routes.STRATEGY,
                enterTransition = { slideInHorizontally { it } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it } + fadeOut() },
            ) {
                StrategyScreen(
                    container = container,
                    onBack = { nav.popBackStack() },
                    onPractise = { nav.switchTab(Tab.Roleplay.route) },
                )
            }
            composable(
                Routes.SETTINGS,
                enterTransition = { slideInHorizontally { it } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it } + fadeOut() },
            ) {
                SettingsScreen(container, onBack = { nav.popBackStack() })
            }
        }
            // Opaque strip behind the status bar so scrolled content never collides with the clock.
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(c.background),
            )
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
