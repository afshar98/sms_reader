package com.example.smsdiscountorganizer.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDestinationTest {
    @Test
    fun destinationsHaveUniqueRoutes() {
        val routes = AppDestination.entries.map(AppDestination::route)

        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun dashboardIsTheStartDestination() {
        assertEquals(AppDestination.Dashboard, AppDestination.startDestination)
    }

    @Test
    fun allPlaceholderScreensAreRegistered() {
        val routes = AppDestination.entries.map(AppDestination::route).toSet()

        assertTrue("offers" in routes)
        assertTrue("details" in routes)
        assertTrue("sms_diagnostics" in routes)
        assertTrue("settings" in routes)
    }

    @Test
    fun detailsKeepsOffersSelectedInBottomNavigation() {
        assertEquals(
            AppDestination.Offers,
            AppDestination.selectedBottomNavigation(AppDestination.Details.route),
        )
    }

    @Test
    fun selectedOfferDetailsRouteKeepsOffersSelected() {
        assertEquals(
            AppDestination.Offers,
            AppDestination.selectedBottomNavigation("details/42"),
        )
    }
}
