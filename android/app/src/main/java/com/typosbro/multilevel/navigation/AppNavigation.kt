// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/navigation/AppNavigation.kt
package com.typosbro.multilevel.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.typosbro.multilevel.data.local.SessionManager
import com.typosbro.multilevel.ui.screens.MainScreen
import com.typosbro.multilevel.ui.screens.auth.LoginScreen
import com.typosbro.multilevel.ui.screens.practice.MultilevelExamScreen
import com.typosbro.multilevel.ui.screens.practice.MultilevelResultScreen
import com.typosbro.multilevel.ui.viewmodels.AuthViewModel

// Define navigation routes
object AppDestinations {
    const val LOGIN_ROUTE = "login"
    const val REGISTER_ROUTE = "register"
    const val MAIN_HUB_ROUTE = "main_hub"
    const val MULTILEVEL_EXAM_ROUTE = "multilevel_exam/{practicePart}"
    const val MULTILEVEL_RESULT_ROUTE = "multilevel_result/{resultId}"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val sessionManager: SessionManager = authViewModel.getSessionManager()

    val startDestination by remember {
        mutableStateOf(
            if (sessionManager.tokenFlow.value != null) AppDestinations.MAIN_HUB_ROUTE else AppDestinations.LOGIN_ROUTE
        )
    }

    // This effect is now observing the rock-solid StateFlow from SessionManager.
    val token by sessionManager.tokenFlow.collectAsState()

    LaunchedEffect(key1 = token) {
        val currentRoute = navController.currentDestination?.route
        val onAuthScreen =
            currentRoute == AppDestinations.LOGIN_ROUTE || currentRoute == AppDestinations.REGISTER_ROUTE

        if (token == null && !onAuthScreen) {
            navController.navigate(AppDestinations.LOGIN_ROUTE) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        } else if (token != null && onAuthScreen) {
            navController.navigate(AppDestinations.MAIN_HUB_ROUTE) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(AppDestinations.LOGIN_ROUTE) {
            LoginScreen()
        }


        composable(AppDestinations.MAIN_HUB_ROUTE) {
            MainScreen(
                onNavigateToMultilevel = { practicePart ->
                    navController.navigate("multilevel_exam/$practicePart")
                },

                onNavigateToMultilevelResult = { resultId ->
                    navController.navigate("multilevel_result/$resultId")
                }
            )
        }


        composable(
            route = AppDestinations.MULTILEVEL_EXAM_ROUTE,
            arguments = listOf(navArgument("practicePart") { type = NavType.StringType })
        ) {
            MultilevelExamScreen(
                onNavigateToResults = { resultId ->
                    navController.navigate("multilevel_result/$resultId") {
                        // Pop back to the multilevel practice hub, not just the exam screen
                        popUpTo(navController.graph.id) {
                            inclusive = false // Keep MainScreen in the backstack
                        }
                        // A more specific popUp can be used if you know the route
                        // popUpTo(PracticeDestinations.MULTILEVEL_HUB) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = AppDestinations.MULTILEVEL_RESULT_ROUTE,
            arguments = listOf(navArgument("resultId") { type = NavType.StringType })
        ) {
            MultilevelResultScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}