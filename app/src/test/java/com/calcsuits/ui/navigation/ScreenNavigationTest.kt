package com.calcsuits.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScreenNavigationTest {

    @Test
    fun primaryNavigationContainsExactSixDestinationsInOrder() {
        val routes = Screen.items.map { it.route }
        assertEquals(
            listOf(
                Screen.Calculator.route,
                Screen.Ai.route,
                Screen.Converter.route,
                Screen.Finance.route,
                Screen.Engineering.route,
                Screen.Settings.route
            ),
            routes
        )
        assertEquals(routes.size, routes.toSet().size)
        assertFalse(routes.contains("graph"))
        assertFalse(routes.contains("programmer"))
    }
}
