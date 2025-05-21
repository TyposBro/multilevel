package com.typosbro.multilevel.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.typosbro.multilevel.data.local.TokenManager
import com.typosbro.multilevel.ui.screens.auth.LoginScreen
import com.typosbro.multilevel.ui.screens.auth.RegisterScreen
import com.typosbro.multilevel.ui.screens.chat.ChatDetailScreen
import com.typosbro.multilevel.ui.screens.chat.ChatListScreen
import com.typosbro.multilevel.ui.viewmodels.AppViewModelProvider // Use the factory
import com.typosbro.multilevel.ui.viewmodels.AuthViewModel

// Define navigation routes
object AppDestinations {
    const val LOGIN_ROUTE = "login"
    const val REGISTER_ROUTE = "register"
    const val CHAT_LIST_ROUTE = "chat_list"
    const val CHAT_DETAIL_ROUTE = "chat_detail" // Base route
    const val CHAT_ID_ARG = "chatId" // Argument name
    const val CHAT_DETAIL_ROUTE_WITH_ARGS = "$CHAT_DETAIL_ROUTE/{$CHAT_ID_ARG}" // Route with arg placeholder
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    // Check initial auth state using TokenManager directly or via AuthViewModel state if preferred
    val tokenManager = remember { TokenManager(context) }
    var startDestination by remember {
        mutableStateOf(
            if (tokenManager.hasToken()) AppDestinations.CHAT_LIST_ROUTE else AppDestinations.LOGIN_ROUTE
        )
    }

    // Observe logout state from AuthViewModel to reset navigation
    val authViewModel: AuthViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val isAuthenticated by authViewModel.authenticationSuccessful.collectAsState() // Observe login/register success too

    // React to authentication changes
    LaunchedEffect(key1 = tokenManager.getToken()) { // React if token changes (login/logout)
        startDestination = if (tokenManager.hasToken()) AppDestinations.CHAT_LIST_ROUTE else AppDestinations.LOGIN_ROUTE
        // Force navigation if already past the auth screens
        if (!tokenManager.hasToken() && navController.currentDestination?.route != AppDestinations.LOGIN_ROUTE && navController.currentDestination?.route != AppDestinations.REGISTER_ROUTE) {
            navController.navigate(AppDestinations.LOGIN_ROUTE) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true } // Clear back stack
                launchSingleTop = true
            }
        } else if (tokenManager.hasToken() && (navController.currentDestination?.route == AppDestinations.LOGIN_ROUTE || navController.currentDestination?.route == AppDestinations.REGISTER_ROUTE)) {
            navController.navigate(AppDestinations.CHAT_LIST_ROUTE) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        }
    }


    NavHost(
        navController = navController,
        startDestination = startDestination // Dynamically set start destination
    ) {
        composable(AppDestinations.LOGIN_ROUTE) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(AppDestinations.CHAT_LIST_ROUTE) {
                        popUpTo(AppDestinations.LOGIN_ROUTE) { inclusive = true } // Remove login from back stack
                        launchSingleTop = true
                    }
                },
                onNavigateToRegister = { navController.navigate(AppDestinations.REGISTER_ROUTE) }
            )
        }
        composable(AppDestinations.REGISTER_ROUTE) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(AppDestinations.CHAT_LIST_ROUTE) {
                        popUpTo(AppDestinations.REGISTER_ROUTE) { inclusive = true } // Remove register from back stack
                        launchSingleTop = true
                    }
                },
                onNavigateToLogin = { navController.navigate(AppDestinations.LOGIN_ROUTE) }
            )
        }
        composable(AppDestinations.CHAT_LIST_ROUTE) {
            ChatListScreen(
                onNavigateToChat = { chatId ->
                    navController.navigate("${AppDestinations.CHAT_DETAIL_ROUTE}/$chatId")
                },
                onLogout = {
                    // Navigation is handled by LaunchedEffect observing tokenManager.getToken()
                }
            )
        }
        composable(
            route = AppDestinations.CHAT_DETAIL_ROUTE_WITH_ARGS,
            arguments = listOf(navArgument(AppDestinations.CHAT_ID_ARG) { type = NavType.StringType })
        ) { // No need to pass chatId explicitly, ViewModel handles it via SavedStateHandle
            ChatDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}