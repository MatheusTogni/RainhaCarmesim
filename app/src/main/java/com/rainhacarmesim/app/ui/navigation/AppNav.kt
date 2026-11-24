package com.rainhacarmesim.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rainhacarmesim.app.domain.AlertType
import com.rainhacarmesim.app.feature.alerts.AlertsScreen
import com.rainhacarmesim.app.feature.dashboard.DashboardScreen
import com.rainhacarmesim.app.feature.devices.DevicesScreen
import com.rainhacarmesim.app.feature.monitor.MonitorScreen
import com.rainhacarmesim.app.feature.settings.SettingsScreen
import com.rainhacarmesim.app.feature.tools.ToolsScreen
import com.rainhacarmesim.app.ui.appContainer
import com.rainhacarmesim.app.ui.theme.RcColors
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val DEVICES = "devices"
    const val MONITOR = "monitor"
    const val TOOLS = "tools"
    const val SETTINGS = "settings"
    const val ALERTS = "alerts"

    fun devices(focus: String? = null) = if (focus == null) DEVICES else "$DEVICES?focus=${Uri.encode(focus)}"
    fun tools(target: String? = null) = if (target == null) TOOLS else "$TOOLS?target=${Uri.encode(target)}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Rounded.Home),
    Tab(Routes.DEVICES, "Devices", Icons.Rounded.Devices),
    Tab(Routes.MONITOR, "Monitor", Icons.Rounded.ShowChart),
    Tab(Routes.TOOLS, "Tools", Icons.Rounded.Build),
    Tab(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
)

private fun NavHostController.goToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Navegação para uma aba com argumentos (sem restaurar estado antigo, para valer o argumento novo). */
private fun NavHostController.goWithArgs(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}

@Composable
fun RainhaRoot() {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val container = appContainer()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route?.substringBefore('?')

    // Alertas gerados durante a sessão aparecem imediatamente, além da central de alertas.
    LaunchedEffect(Unit) {
        container.scanCoordinator.newAlerts.collect { alerts ->
            val text = if (alerts.size == 1) {
                val a = alerts.first()
                "${AlertType.from(a.type)?.title ?: "Alerta"}: ${a.deviceLabel}"
            } else "${alerts.size} novos alertas de rede"
            val result = snackbar.showSnackbar(text, actionLabel = "Ver", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) nav.navigate(Routes.ALERTS) { launchSingleTop = true }
        }
    }

    Scaffold(
        containerColor = RcColors.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = RcColors.Surface, tonalElevation = 0.dp) {
                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route || (tab.route == Routes.HOME && currentRoute == Routes.ALERTS)
                    NavigationBarItem(
                        selected = selected,
                        onClick = { nav.goToTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RcColors.Text,
                            selectedTextColor = RcColors.Crimson,
                            indicatorColor = RcColors.Bordeaux,
                            unselectedIconColor = RcColors.TextDim,
                            unselectedTextColor = RcColors.TextDim,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                DashboardScreen(
                    onOpenDevices = { nav.goToTab(Routes.DEVICES) },
                    onOpenAlerts = { nav.navigate(Routes.ALERTS) { launchSingleTop = true } },
                    onOpenDevice = { key -> nav.goWithArgs(Routes.devices(key)) },
                    onOpenSettings = { nav.goToTab(Routes.SETTINGS) },
                )
            }
            composable(
                route = "${Routes.DEVICES}?focus={focus}",
                arguments = listOf(navArgument("focus") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                DevicesScreen(
                    focusDeviceKey = entry.arguments?.getString("focus"),
                    onPing = { ip -> nav.goWithArgs(Routes.tools(ip)) },
                )
            }
            composable(Routes.MONITOR) { MonitorScreen() }
            composable(
                route = "${Routes.TOOLS}?target={target}",
                arguments = listOf(navArgument("target") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                ToolsScreen(initialTarget = entry.arguments?.getString("target"))
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(showMessage = { msg -> scope.launch { snackbar.showSnackbar(msg) } })
            }
            composable(Routes.ALERTS) {
                AlertsScreen(
                    onBack = { nav.popBackStack() },
                    onOpenDevice = { key -> nav.goWithArgs(Routes.devices(key)) },
                )
            }
        }
    }
}
