package uk.co.asmcoach.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import uk.co.asmcoach.AppContainer
import uk.co.asmcoach.ui.components.LocalBottomInset
import uk.co.asmcoach.ui.components.rememberHaptics
import uk.co.asmcoach.ui.jobs.JobsScreen
import uk.co.asmcoach.ui.prep.PrepScreen
import uk.co.asmcoach.ui.roleplay.RoleplayScreen
import uk.co.asmcoach.ui.settings.SettingsScreen
import uk.co.asmcoach.ui.strategy.StrategyScreen
import uk.co.asmcoach.ui.theme.AsmTheme
import uk.co.asmcoach.ui.theme.IosMotion
import uk.co.asmcoach.ui.theme.IosType

private enum class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Jobs("jobs", "Jobs", Icons.Outlined.WorkOutline, Icons.Rounded.Work),
    Prep("prep", "Prep", Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Rounded.MenuBook),
    Roleplay("roleplay", "Interview", Icons.Outlined.RecordVoiceOver, Icons.Rounded.RecordVoiceOver),
}

private object Routes {
    const val STRATEGY = "strategy"
    const val SETTINGS = "settings"
}

/** UITabBar height without the home-indicator inset. */
private val TabBarHeight = 49.dp

// iOS stack navigation: the new screen slides in from the trailing edge while the one beneath
// moves a third of the way with a slight dim; popping reverses it. Sheets rise from the bottom.
private val pushIn: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { slideInHorizontally(IosMotion.push<IntOffset>()) { it } }
private val pushOutBehind: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { slideOutHorizontally(IosMotion.push<IntOffset>()) { -it / 3 } + fadeOut(tween(350), targetAlpha = 0.85f) }
private val popInBehind: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { slideInHorizontally(IosMotion.push<IntOffset>()) { -it / 3 } + fadeIn(tween(350), initialAlpha = 0.85f) }
private val popOut: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { slideOutHorizontally(IosMotion.push<IntOffset>()) { it } }
private val sheetIn: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { slideInVertically(IosMotion.push<IntOffset>()) { it } }
private val sheetOut: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { slideOutVertically(IosMotion.push<IntOffset>()) { it } }

@Composable
fun AsmCoachRoot(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    val onTopLevel = Tab.entries.any { tab -> current?.hierarchy?.any { it.route == tab.route } == true }
    val c = AsmTheme.colors
    val hazeState = remember { HazeState() }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = if (onTopLevel) TabBarHeight + navBottom else navBottom

    Box(Modifier.fillMaxSize().background(c.background)) {
        CompositionLocalProvider(LocalBottomInset provides bottomInset) {
            NavHost(
                navController = nav,
                startDestination = Tab.Jobs.route,
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                // Switching tabs on iOS is instant; a very short fade avoids a hard flash.
                enterTransition = { fadeIn(tween(120)) },
                exitTransition = { fadeOut(tween(120)) },
            ) {
                val openSettings = { nav.navigate(Routes.SETTINGS) }
                composable(Tab.Jobs.route, exitTransition = { if (targetState.destination.route == Routes.STRATEGY) pushOutBehind(this) else fadeOut(tween(120)) }, popEnterTransition = { if (initialState.destination.route == Routes.STRATEGY) popInBehind(this) else fadeIn(tween(120)) }) {
                    JobsScreen(
                        container = container,
                        onOpenStrategy = { nav.navigate(Routes.STRATEGY) },
                        onOpenSettings = openSettings,
                    )
                }
                composable(Tab.Prep.route) {
                    PrepScreen(
                        container,
                        onOpenSettings = openSettings,
                        onOpenJobs = { nav.switchTab(Tab.Jobs.route) },
                        onInterview = { nav.switchTab(Tab.Roleplay.route) },
                    )
                }
                composable(Tab.Roleplay.route) {
                    RoleplayScreen(container, onOpenSettings = openSettings, onOpenJobs = { nav.switchTab(Tab.Jobs.route) })
                }
                composable(Routes.STRATEGY, enterTransition = pushIn, popExitTransition = popOut, exitTransition = { fadeOut(tween(120)) }) {
                    StrategyScreen(
                        container = container,
                        onBack = { nav.popBackStack() },
                        onPrep = { nav.switchTab(Tab.Prep.route) },
                        onPractise = { nav.switchTab(Tab.Roleplay.route) },
                    )
                }
                composable(Routes.SETTINGS, enterTransition = sheetIn, popExitTransition = sheetOut) {
                    SettingsScreen(container, onBack = { nav.popBackStack() })
                }
            }
        }

        // Status bar scrim so scrolled content never collides with the clock.
        Box(
            Modifier
                .fillMaxWidth()
                .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .background(c.barMaterial),
        )

        if (onTopLevel) {
            IosTabBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = c.background,
                            tint = HazeTint(c.barMaterial),
                            blurRadius = 20.dp,
                            noiseFactor = 0f,
                        ),
                    ),
                current = Tab.entries.firstOrNull { tab -> current?.hierarchy?.any { it.route == tab.route } == true },
                bottomPadding = navBottom,
                onSelect = { nav.switchTab(it.route) },
            )
        }
    }
}

/** UITabBar: blurred material, hairline top edge, filled glyph + 10pt label for the selected tab. */
@Composable
private fun IosTabBar(modifier: Modifier, current: Tab?, bottomPadding: androidx.compose.ui.unit.Dp, onSelect: (Tab) -> Unit) {
    val c = AsmTheme.colors
    val haptics = rememberHaptics()
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.hairline))
        Row(Modifier.fillMaxWidth().height(TabBarHeight)) {
            Tab.entries.forEach { tab ->
                val selected = tab == current
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (!selected) haptics.selection()
                            onSelect(tab)
                        }
                        .padding(top = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        if (selected) tab.selectedIcon else tab.icon,
                        contentDescription = null,
                        tint = if (selected) c.accent else c.gray,
                        modifier = Modifier.size(26.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tab.label,
                        style = IosType.caption2.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        color = if (selected) c.accent else c.gray,
                    )
                }
            }
        }
        Spacer(Modifier.height(bottomPadding))
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
