// This is the corrected version of your file
// In: ui/screens/MainScreen.kt

package com.typosbro.multilevel.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.typosbro.multilevel.ui.screens.practice.PracticeHubScreen
import com.typosbro.multilevel.ui.screens.profile.ProfileScreen
import com.typosbro.multilevel.ui.screens.progress.ProgressScreen
import com.typosbro.multilevel.ui.screens.wordbank.WordBankScreen
import com.typosbro.multilevel.ui.screens.wordbank.WordReviewScreen

// Define routes for the main tabs
object MainDestinations {
    const val PRACTICE_ROUTE = "practice"
    const val WORDBANK_ROUTE = "wordbank"
    const val PROGRESS_ROUTE = "progress"
    const val PROFILE_ROUTE = "profile"
}

object WordBankDestinations {
    const val HUB_ROUTE = "wordbank_hub"
    const val REVIEW_ROUTE = "wordbank_review"
}

@Composable
fun MainScreen(
    // Accept navigation lambdas from the parent navigator
    onNavigateToExam: () -> Unit,
    onNavigateToExamResult: (resultId: String) -> Unit,
    onNavigateToChat: (chatId: String) -> Unit
) {
    val mainNavController = rememberNavController()
    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController = mainNavController)
        }
    ) { innerPadding ->
        NavHost(
            navController = mainNavController,
            startDestination = MainDestinations.PRACTICE_ROUTE,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(MainDestinations.PRACTICE_ROUTE) {
                // Pass the navigation action down to the PracticeHubScreen
                PracticeHubScreen(
                    onNavigateToExam = onNavigateToExam,
                    onNavigateToChat = onNavigateToChat
                )
            }
            // This defines a nested navigation graph for the Word Bank tab
            navigation(startDestination = WordBankDestinations.HUB_ROUTE, route = MainDestinations.WORDBANK_ROUTE) {
                composable(WordBankDestinations.HUB_ROUTE) {
                    WordBankScreen(
                        onNavigateToReview = {
                            mainNavController.navigate(WordBankDestinations.REVIEW_ROUTE)
                        }
                    )
                }
                composable(WordBankDestinations.REVIEW_ROUTE) {
                    WordReviewScreen(
                        onNavigateBack = {
                            mainNavController.popBackStack()
                        }
                    )
                }
            }
            composable(MainDestinations.PROGRESS_ROUTE) {
                // Pass the navigation action down to the ProgressScreen
                // THIS FIXES THE ERROR
                ProgressScreen(onNavigateToResult = onNavigateToExamResult)
            }
            composable(MainDestinations.PROFILE_ROUTE) { ProfileScreen() }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(
        Triple("Practice", Icons.Default.Home, MainDestinations.PRACTICE_ROUTE),
        Triple("Word Bank", Icons.Default.Style, MainDestinations.WORDBANK_ROUTE),
        Triple("Progress", Icons.Default.History, MainDestinations.PROGRESS_ROUTE),
        Triple("Profile", Icons.Default.Person, MainDestinations.PROFILE_ROUTE)
    )

    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        items.forEach { item ->
            val route = item.third
            NavigationBarItem(
                icon = { Icon(item.second, contentDescription = item.first) },
                label = { Text(item.first) },
                selected = currentDestination?.hierarchy?.any { it.route == route } == true,
                onClick = {
                    navController.navigate(route) {
                        // Pop up to the start destination of the graph to
                        // avoid building up a large stack of destinations
                        // on the back stack as users select items
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination when
                        // re-selecting the same item
                        launchSingleTop = true
                        // Restore state when re-selecting a previously selected item
                        restoreState = true
                    }
                }
            )
        }
    }
}