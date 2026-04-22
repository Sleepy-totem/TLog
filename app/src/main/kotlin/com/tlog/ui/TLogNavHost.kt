package com.tlog.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tlog.ui.clock.ClockScreen
import com.tlog.ui.daydetail.DayDetailScreen
import com.tlog.ui.home.HomeScreen
import com.tlog.ui.pastweeks.PastWeeksScreen
import com.tlog.ui.settings.SettingsScreen
import com.tlog.ui.timecard.TimeCardScreen
import com.tlog.viewmodel.TLogViewModel

object Routes {
    const val HOME = "home"
    const val CLOCK = "clock"
    const val TIMECARD = "timecard"
    const val SETTINGS = "settings"
    const val PAST_WEEKS = "past_weeks"
    const val DAY = "day/{dateIso}"
    fun day(dateIso: String) = "day/$dateIso"
}

@Composable
fun TLogNavHost(nav: NavHostController, viewModel: TLogViewModel) {
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onOpenClock = { nav.navigate(Routes.CLOCK) },
                onOpenTimeCard = { nav.navigate(Routes.TIMECARD) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.CLOCK) {
            ClockScreen(viewModel = viewModel, onBack = { nav.popBackStack() })
        }
        composable(Routes.TIMECARD) {
            TimeCardScreen(
                viewModel = viewModel,
                onBack = { nav.popBackStack() },
                onOpenDay = { date -> nav.navigate(Routes.day(date.toString())) },
                onOpenPastWeeks = { nav.navigate(Routes.PAST_WEEKS) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { nav.popBackStack() },
                onOpenPastWeeks = { nav.navigate(Routes.PAST_WEEKS) }
            )
        }
        composable(Routes.PAST_WEEKS) {
            PastWeeksScreen(viewModel = viewModel, onBack = { nav.popBackStack() })
        }
        composable(
            route = Routes.DAY,
            arguments = listOf(navArgument("dateIso") { type = NavType.StringType })
        ) { backStackEntry ->
            val dateIso = backStackEntry.arguments?.getString("dateIso") ?: return@composable
            DayDetailScreen(
                viewModel = viewModel,
                dateIso = dateIso,
                onBack = { nav.popBackStack() }
            )
        }
    }
}
