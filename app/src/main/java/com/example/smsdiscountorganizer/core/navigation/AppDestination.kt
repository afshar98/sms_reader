package com.example.smsdiscountorganizer.core.navigation

enum class AppDestination(val route: String, val title: String) {
    Dashboard(route = "dashboard", title = "Home"),
    Offers(route = "offers", title = "Offers"),
    Details(route = "details", title = "Offer details"),
    SmsDiagnostics(route = "sms_diagnostics", title = "SMS diagnostics"),
    Settings(route = "settings", title = "Settings");

    companion object {
        val startDestination: AppDestination = Dashboard
        val bottomNavigation: List<AppDestination> = listOf(Dashboard, Offers, Settings)

        fun selectedBottomNavigation(route: String?): AppDestination? = when (route) {
            Details.route -> Offers
            SmsDiagnostics.route -> Settings
            else -> bottomNavigation.firstOrNull { it.route == route }
        }
    }
}
