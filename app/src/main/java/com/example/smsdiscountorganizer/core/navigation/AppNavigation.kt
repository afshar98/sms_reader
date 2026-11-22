package com.example.smsdiscountorganizer.core.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smsdiscountorganizer.feature.dashboard.DashboardScreen
import com.example.smsdiscountorganizer.feature.dashboard.DashboardViewModel
import com.example.smsdiscountorganizer.feature.details.DetailsScreen
import com.example.smsdiscountorganizer.feature.details.DetailsViewModel
import com.example.smsdiscountorganizer.feature.diagnostics.SmsDiagnosticsScreen
import com.example.smsdiscountorganizer.feature.diagnostics.SmsDiagnosticsViewModel
import com.example.smsdiscountorganizer.feature.offers.OffersScreen
import com.example.smsdiscountorganizer.feature.offers.OffersViewModel
import com.example.smsdiscountorganizer.feature.settings.SettingsScreen
import com.example.smsdiscountorganizer.feature.settings.SettingsViewModel
import java.time.Clock
import java.time.ZoneId

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selectedDestination = AppDestination.selectedBottomNavigation(currentRoute)
    val zoneId = ZoneId.systemDefault()
    val clock = Clock.system(zoneId)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail {
                    AppDestination.bottomNavigation.forEach { destination ->
                        NavigationRailItem(
                            selected = selectedDestination == destination,
                            onClick = { navigateTopLevel(navController, destination) },
                            icon = { DestinationIcon(destination) },
                            label = { Text(destination.title) },
                        )
                    }
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                bottomBar = {
                    if (!useRail) {
                        NavigationBar {
                            AppDestination.bottomNavigation.forEach { destination ->
                                NavigationBarItem(
                                    selected = selectedDestination == destination,
                                    onClick = { navigateTopLevel(navController, destination) },
                                    icon = { DestinationIcon(destination) },
                                    label = { Text(destination.title) },
                                )
                            }
                        }
                    }
                },
            ) { contentPadding ->
                NavHost(
                    navController = navController,
                    startDestination = AppDestination.startDestination.route,
                    modifier = Modifier.padding(contentPadding),
                ) {
                    composable(AppDestination.Dashboard.route) {
                        val viewModel: DashboardViewModel = hiltViewModel()
                        DashboardScreen(
                            viewModel = viewModel,
                            clock = clock,
                            zoneId = zoneId,
                            onOpenOffers = { navController.navigate(AppDestination.Offers.route) },
                            onOpenOffer = { id -> navController.navigate("${AppDestination.Details.route}/$id") },
                        )
                    }
                    composable(AppDestination.Offers.route) {
                        val viewModel: OffersViewModel = hiltViewModel()
                        OffersScreen(
                            viewModel = viewModel,
                            clock = clock,
                            zoneId = zoneId,
                            onOpenDetails = { id -> navController.navigate("${AppDestination.Details.route}/$id") },
                        )
                    }
                    composable(
                        route = "${AppDestination.Details.route}/{sourceSmsId}",
                        arguments = listOf(navArgument("sourceSmsId") { type = NavType.LongType }),
                    ) {
                        val viewModel: DetailsViewModel = hiltViewModel()
                        DetailsScreen(viewModel, clock, zoneId, onBack = { navController.popBackStack() })
                    }
                    composable(AppDestination.SmsDiagnostics.route) {
                        val viewModel: SmsDiagnosticsViewModel = hiltViewModel()
                        SmsDiagnosticsScreen(viewModel)
                    }
                    composable(AppDestination.Settings.route) {
                        val viewModel: SettingsViewModel = hiltViewModel()
                        SettingsScreen(
                            viewModel = viewModel,
                            onOpenSmsDiagnostics = {
                                navController.navigate(AppDestination.SmsDiagnostics.route)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationIcon(destination: AppDestination) {
    val imageVector = when (destination) {
        AppDestination.Dashboard -> Icons.Outlined.Home
        AppDestination.Offers -> Icons.Outlined.LocalOffer
        AppDestination.Settings -> Icons.Outlined.Settings
        else -> Icons.Outlined.Home
    }
    Icon(imageVector = imageVector, contentDescription = destination.title)
}

private fun navigateTopLevel(
    navController: androidx.navigation.NavHostController,
    destination: AppDestination,
) {
    navController.navigate(destination.route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
