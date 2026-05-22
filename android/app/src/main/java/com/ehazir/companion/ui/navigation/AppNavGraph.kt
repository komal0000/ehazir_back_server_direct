package com.ehazir.companion.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ehazir.companion.data.preferences.AppPreferences
import com.ehazir.companion.ui.attendance.AttendanceScreen
import com.ehazir.companion.ui.dashboard.DashboardScreen
import com.ehazir.companion.ui.launcher.AppLauncherScreen
import com.ehazir.companion.ui.manageattendance.ManageStudentAttendanceScreen
import com.ehazir.companion.ui.register.RegisterScreen
import com.ehazir.companion.ui.settings.SettingsScreen

@Composable
fun AppNavGraph(
    prefs: AppPreferences = hiltViewModel<AppPreferencesViewModel>().prefs
) {
    val navController = rememberNavController()
    val isRegistered by prefs.isRegistered.collectAsState(initial = false)

    val startDestination = if (isRegistered) "dashboard" else "register"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("register") {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate("dashboard") {
                        popUpTo("register") { inclusive = true }
                    }
                }
            )
        }
        composable("dashboard") {
            DashboardScreen(
                onManageConnection = { navController.navigate("settings") },
                onOpenAttendance = { navController.navigate("attendance") },
                onOpenAppLauncher = { navController.navigate("appLauncher") },
                onOpenManageStudentAttendance = { navController.navigate("manageStudentAttendance") }
            )
        }
        composable("attendance") {
            AttendanceScreen(
                onNavigateSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onUnregister = {
                    navController.navigate("register") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }
        composable("appLauncher") {
            AppLauncherScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("manageStudentAttendance") {
            ManageStudentAttendanceScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
