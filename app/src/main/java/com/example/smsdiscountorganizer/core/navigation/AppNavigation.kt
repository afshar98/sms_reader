package com.example.smsdiscountorganizer.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smsdiscountorganizer.feature.dashboard.DashboardScreen
import com.example.smsdiscountorganizer.feature.dashboard.DashboardViewModel
import com.example.smsdiscountorganizer.feature.details.DetailsScreen
import com.example.smsdiscountorganizer.feature.offers.OffersScreen
import com.example.smsdiscountorganizer.feature.settings.SettingsScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selectedDestination = AppDestination.selectedBottomNavigation(currentRoute)

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppDestination.bottomNavigation.forEach { destination ->
                    NavigationBarItem(
                        selected = selectedDestination == destination,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(destination.title.take(1)) },
                        label = { Text(destination.title) },
                    )
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
                    onOpenOffers = { navController.navigate(AppDestination.Offers.route) },
                )
            }
            composable(AppDestination.Offers.route) {
                OffersScreen(onOpenDetails = { navController.navigate(AppDestination.Details.route) })
            }
            composable(AppDestination.Details.route) {
                DetailsScreen()
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
