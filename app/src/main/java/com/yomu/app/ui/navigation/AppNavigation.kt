package com.yomu.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.yomu.app.ui.theme.rememberReduceMotion
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yomu.app.ui.theme.ChromeContent
import com.yomu.app.ui.home.HomeScreen
import com.yomu.app.ui.history.HistoryScreen
import com.yomu.app.ui.settings.SettingsScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object History : Screen("history", "History", Icons.Default.DateRange)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(Screen.Home, Screen.History, Screen.Settings)

@Composable
fun AppNavigation(onRequestScreenCapture: () -> Unit = {}) {
    val navController = rememberNavController()
    val scheme = MaterialTheme.colorScheme
    val reduceMotion = rememberReduceMotion()
    Scaffold(
        bottomBar = {
            Surface(shadowElevation = 2.dp, color = scheme.surface) {
                NavigationBar(
                    modifier = Modifier.heightIn(min = 80.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)),
                    containerColor = scheme.surface, tonalElevation = 0.dp
                ) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(screen.title, style = MaterialTheme.typography.labelMedium) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = scheme.onPrimary,
                                selectedTextColor = scheme.onSurface,
                                indicatorColor = scheme.primary,
                                unselectedIconColor = scheme.onSurfaceVariant,
                                unselectedTextColor = scheme.onSurfaceVariant
                            ),
                            onClick = {
                                navController.navigate(screen.route) { launchSingleTop = true }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        ChromeContent(Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                enterTransition = {
                    if (reduceMotion) fadeIn(tween(180))
                    else fadeIn(tween(180)) + slideInHorizontally(tween(200)) { it / 12 }
                },
                exitTransition = {
                    if (reduceMotion) fadeOut(tween(180))
                    else fadeOut(tween(180)) + slideOutHorizontally(tween(200)) { -it / 12 }
                }
            ) {
                composable(Screen.Home.route) { HomeScreen(onRequestScreenCapture = onRequestScreenCapture) }
                composable(Screen.History.route) { HistoryScreen(onRequestScreenCapture = onRequestScreenCapture) }
                composable(Screen.Settings.route) { SettingsScreen() }
            }
        }
    }
}
