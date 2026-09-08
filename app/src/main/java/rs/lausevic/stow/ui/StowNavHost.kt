// SPDX-License-Identifier: Apache-2.0
package rs.lausevic.stow.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.navArgument
import rs.lausevic.stow.AppContainer
import rs.lausevic.stow.R
import rs.lausevic.stow.data.Settings
import rs.lausevic.stow.ui.components.BottomDestination
import rs.lausevic.stow.ui.components.StowIcons
import rs.lausevic.stow.ui.screens.CatalogueScreen
import rs.lausevic.stow.ui.screens.LicencesScreen
import rs.lausevic.stow.ui.screens.ReturnSummaryScreen
import rs.lausevic.stow.ui.screens.SettingsScreen
import rs.lausevic.stow.ui.screens.ShoppingScreen
import rs.lausevic.stow.ui.screens.TripDetailScreen
import rs.lausevic.stow.ui.screens.TripsScreen
import rs.lausevic.stow.ui.screens.WizardScreen

object Routes {
    const val TRIPS = "trips"
    const val CATALOGUE = "catalogue"
    const val SHOPPING = "shopping"
    const val SETTINGS = "settings"
    const val WIZARD = "wizard"
    const val TRIP = "trip"
    const val RETURN_SUMMARY = "return-summary"
    const val LICENCES = "licences"

    fun trip(id: Long) = "$TRIP/$id"
    fun returnSummary(id: Long) = "$RETURN_SUMMARY/$id"
}

@Composable
fun StowNavHost(
    container: AppContainer,
    settings: Settings,
    startDeepLink: String? = null,
) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()

    val destinations = listOf(
        BottomDestination(Routes.TRIPS, stringResource(R.string.nav_trips), StowIcons.Trips),
        BottomDestination(Routes.CATALOGUE, stringResource(R.string.nav_catalogue), StowIcons.Catalogue),
        BottomDestination(Routes.SHOPPING, stringResource(R.string.nav_shopping), StowIcons.Shopping),
        BottomDestination(Routes.SETTINGS, stringResource(R.string.nav_settings), StowIcons.Settings),
    )

    // Prečice sa pokretača ne otvaraju svoj ekran preko liste putovanja nego umesto nje —
    // povratak vodi na Putovanja, ne nazad u prečicu.
    LaunchedEffect(startDeepLink) {
        when (startDeepLink) {
            "stow://new-trip" -> navController.navigate(Routes.WIZARD)
            "stow://shopping" -> navController.navigate(Routes.SHOPPING)
        }
    }

    NavHost(navController = navController, startDestination = Routes.TRIPS) {
        composable(Routes.TRIPS) {
            TripsScreen(
                container = container,
                destinations = destinations,
                route = route,
                onSelectTab = { navController.switchTab(it.route) },
                onOpenTrip = { navController.navigate(Routes.trip(it)) },
                onNewTrip = { navController.navigate(Routes.WIZARD) },
            )
        }
        composable(Routes.CATALOGUE) {
            CatalogueScreen(
                container = container,
                destinations = destinations,
                route = route,
                onSelectTab = { navController.switchTab(it.route) },
            )
        }
        composable(Routes.SHOPPING) {
            ShoppingScreen(
                container = container,
                destinations = destinations,
                route = route,
                onSelectTab = { navController.switchTab(it.route) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                settings = settings,
                destinations = destinations,
                route = route,
                onSelectTab = { navController.switchTab(it.route) },
                onOpenLicences = { navController.navigate(Routes.LICENCES) },
            )
        }
        composable(Routes.LICENCES) {
            LicencesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.WIZARD) {
            WizardScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onCreated = { id ->
                    navController.popBackStack()
                    navController.navigate(Routes.trip(id))
                },
            )
        }
        composable(
            route = "${Routes.TRIP}/{tripId}",
            arguments = listOf(navArgument("tripId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getLong("tripId") ?: return@composable
            TripDetailScreen(
                container = container,
                tripId = tripId,
                settings = settings,
                onBack = { navController.popBackStack() },
                onFinish = { navController.navigate(Routes.returnSummary(tripId)) },
            )
        }
        composable(
            route = "${Routes.RETURN_SUMMARY}/{tripId}",
            arguments = listOf(navArgument("tripId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getLong("tripId") ?: return@composable
            ReturnSummaryScreen(
                container = container,
                tripId = tripId,
                onBack = { navController.popBackStack() },
                onClosed = {
                    navController.popBackStack(Routes.TRIPS, inclusive = false)
                },
            )
        }
    }
}

/** Prelazak na drugu karticu ne gomila stek — nikad dvadeset putovanja iza dugmeta nazad. */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(Routes.TRIPS) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
