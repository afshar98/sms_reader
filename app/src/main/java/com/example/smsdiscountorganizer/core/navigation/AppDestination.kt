package com.example.smsdiscountorganizer.core.navigation

enum class AppDestination(val route: String, val title: String) {
    Dashboard(route = "dashboard", title = "خانه"),
    Offers(route = "offers", title = "پیشنهادها"),
    Details(route = "details", title = "جزئیات پیشنهاد"),
    SmsDiagnostics(route = "sms_diagnostics", title = "وضعیت پیامک"),
    Settings(route = "settings", title = "تنظیمات");

    companion object {
        val startDestination: AppDestination = Dashboard
        val bottomNavigation: List<AppDestination> = listOf(Dashboard, Offers, Settings)

        fun selectedBottomNavigation(route: String?): AppDestination? = when (route?.substringBefore('/')) {
            Details.route -> Offers
            SmsDiagnostics.route -> Settings
            else -> bottomNavigation.firstOrNull { it.route == route }
        }
    }
}
