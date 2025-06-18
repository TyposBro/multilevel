// {PATH_TO_PROJECT}/app/src/main/java/com/typosbro/multilevel/navigation/AppNavigation.kt
package com.typosbro.multilevel.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.typosbro.multilevel.data.local.SessionManager
import com.typosbro.multilevel.data.local.TokenManager
import com.typosbro.multilevel.ui.screens.MainScreen
import com.typosbro.multilevel.ui.screens.auth.LoginScreen
import com.typosbro.multilevel.ui.screens.auth.RegisterScreen
import com.typosbro.multilevel.ui.screens.chat.ChatDetailScreen
import com.typosbro.multilevel.ui.screens.chat.ChatListScreen
import com.typosbro.multilevel.ui.screens.practice.ExamResultScreen
import com.typosbro.multilevel.ui.screens.practice.ExamScreen
import com.typosbro.multilevel.ui.viewmodels.AuthViewModel
import kotlinx.coroutines.flow.collectLatest

// Define navigation routes
object AppDestinations {
    const val LOGIN_ROUTE = "login"
    const val REGISTER_ROUTE = "register"
    const val MAIN_HUB_ROUTE = "main_hub"
    const val EXAM_SCREEN_ROUTE = "exam_screen"
    const val EXAM_RESULT_ROUTE = "exam_result/{resultId}"
    const val CHAT_LIST_ROUTE = "chat_list"
    const val CHAT_DETAIL_ROUTE = "chat_detail"
    const val CHAT_ID_ARG = "chatId"
    const val CHAT_DETAIL_ROUTE_WITH_ARGS = "$CHAT_DETAIL_ROUTE/{$CHAT_ID_ARG}"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val authViewModel: AuthViewModel = hiltViewModel()

    // Get the singleton SessionManager instance via the AuthViewModel
    val sessionManager: SessionManager = authViewModel.getSessionManager()

    // Determine the initial screen based on whether a token exists on startup.
    val startDestination by remember {
        mutableStateOf(
            if (tokenManager.hasToken()) AppDestinations.MAIN_HUB_ROUTE else AppDestinations.LOGIN_ROUTE
        )
    }

    // EFFECT 1: Handles automatic logout when token expires.
    // This LaunchedEffect listens for events from the SessionManager.
    // The network interceptor will trigger this event if it gets a 401 error.
    LaunchedEffect(key1 = sessionManager) {
        sessionManager.logoutEvents.collectLatest {
            // When a logout event is received, navigate to the login screen
            // and clear the entire back stack to prevent the user from going back.
            navController.navigate(AppDestinations.LOGIN_ROUTE) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // EFFECT 2: Handles manual login/logout and app startup state.
    // This observes the token directly. It's useful for redirecting the user
    // immediately after they manually log in or log out.
    val token by tokenManager.tokenFlow.collectAsState(initial = tokenManager.getToken())
    LaunchedEffect(key1 = token) {
        val currentRoute = navController.currentDestination?.route
        val onAuthScreen = currentRoute == AppDestinations.LOGIN_ROUTE || currentRoute == AppDestinations.REGISTER_ROUTE

        if (token == null && !onAuthScreen) {
            // If token is cleared (manual logout) and we are NOT on an auth screen,
            // navigate to login.
            navController.navigate(AppDestinations.LOGIN_ROUTE) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        } else if (token != null && onAuthScreen) {
            // If a token is present (successful login) and we ARE on an auth screen,
            // navigate to the main hub.
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
            LoginScreen(
                onNavigateToRegister = { navController.navigate(AppDestinations.REGISTER_ROUTE) }
            )
        }

        composable(AppDestinations.REGISTER_ROUTE) {
            RegisterScreen(
                onNavigateToLogin = { navController.navigate(AppDestinations.LOGIN_ROUTE) }
            )
        }

        composable(AppDestinations.MAIN_HUB_ROUTE) {
            MainScreen(
                onNavigateToExam = {
                    navController.navigate(AppDestinations.EXAM_SCREEN_ROUTE)
                },
                onNavigateToExamResult = { resultId ->
                    navController.navigate("exam_result/$resultId")
                },
                onNavigateToChat = { chatId ->
                    navController.navigate("${AppDestinations.CHAT_DETAIL_ROUTE}/$chatId")
                }
            )
        }

        composable(AppDestinations.EXAM_SCREEN_ROUTE) {
            ExamScreen(
                onNavigateToResults = { resultId ->
                    navController.navigate("exam_result/$resultId") {
                        popUpTo(AppDestinations.EXAM_SCREEN_ROUTE) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = AppDestinations.EXAM_RESULT_ROUTE,
            arguments = listOf(navArgument("resultId") { type = NavType.StringType })
        ) {
            ExamResultScreen(onNavigateBack = { navController.popBackStack() })
        }

        // OLD MVP ROUTES
        composable(AppDestinations.CHAT_LIST_ROUTE) {
            ChatListScreen(
                onNavigateToChat = { chatId ->
                    navController.navigate("${AppDestinations.CHAT_DETAIL_ROUTE}/$chatId")
                },
                onLogout = {
                    // The logout button in ProfileViewModel now calls sessionManager.logout(),
                    // which is handled by our new LaunchedEffect.
                }
            )
        }
        composable(
            route = AppDestinations.CHAT_DETAIL_ROUTE_WITH_ARGS,
            arguments = listOf(navArgument(AppDestinations.CHAT_ID_ARG) { type = NavType.StringType })
        ) {
            ChatDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}