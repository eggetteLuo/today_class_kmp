package com.eggetteluo.todayclasskmp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.eggetteluo.todayclasskmp.core.preferences.RolePreferences
import com.eggetteluo.todayclasskmp.feature.desktop.ui.DesktopScreen
import com.eggetteluo.todayclasskmp.feature.role.model.UserRole
import com.eggetteluo.todayclasskmp.feature.role.ui.RoleSelectScreen

@Composable
fun TodayClassNavHost() {
    val navController = rememberNavController()
    val rememberedRole = remember { UserRole.fromCode(RolePreferences.getSelectedRoleCode()) }
    val startDestination = remember(rememberedRole) {
        rememberedRole?.let { AppRoutes.desktop(it.code) } ?: AppRoutes.RoleSelect
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(route = AppRoutes.RoleSelect) {
            RoleSelectScreen(
                onRoleSelected = { role ->
                    RolePreferences.setSelectedRoleCode(role.code)
                    navController.navigate(AppRoutes.desktop(role.code)) {
                        popUpTo(AppRoutes.RoleSelect) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = AppRoutes.DesktopPattern,
            arguments = listOf(navArgument("role") { type = NavType.StringType }),
        ) { backStackEntry ->
            val roleCode = backStackEntry.arguments?.read { getStringOrNull("role") }.orEmpty()
            val role = UserRole.fromCode(roleCode) ?: UserRole.Student
            DesktopScreen(role = role)
        }
    }
}
