package com.typosbro.multilevel.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.typosbro.multilevel.R
import com.typosbro.multilevel.ui.screens.practice.PracticeHubScreen
import com.typosbro.multilevel.ui.screens.profile.ProfileScreen
import com.typosbro.multilevel.ui.screens.progress.ProgressScreen
import com.typosbro.multilevel.ui.screens.wordbank.WordBankScreen
import com.typosbro.multilevel.ui.screens.wordbank.WordLevelScreen
import com.typosbro.multilevel.ui.screens.wordbank.WordListScreen
import com.typosbro.multilevel.ui.screens.wordbank.WordReviewScreen
import com.typosbro.multilevel.ui.screens.wordbank.WordTopicScreen


// Define routes for the main tabs
object MainDestinations {
    const val PRACTICE_ROUTE = "practice"
    const val WORDBANK_ROUTE = "wordbank"
    const val PROGRESS_ROUTE = "progress"
    const val PROFILE_ROUTE = "profile"
}

// --- Start of new code ---
object WordBankDestinations {
    const val HUB_ROUTE = "wordbank_hub"
    const val REVIEW_ROUTE = "wordbank_review"
    const val LEVEL_SELECT_ROUTE = "wordbank_level_select"
    const val TOPIC_SELECT_ROUTE = "wordbank_topic_select/{level}"
    const val WORD_LIST_ROUTE = "wordbank_word_list/{level}/{topic}"
}
// --- End of new code ---

@Composable
fun MainScreen(
    onNavigateToIELTS: () -> Unit,
    onNavigateToMultilevel: () -> Unit,
    onNavigateToIeltsResult: (resultId: String) -> Unit, // New
    onNavigateToMultilevelResult: (resultId: String) -> Unit, // New
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
                    onNavigateToIELTS = onNavigateToIELTS,
                    onNavigateToMultilevel = onNavigateToMultilevel
                )
            }
            // --- Start of modified code ---
            // This defines a nested navigation graph for the Word Bank tab
            navigation(
                startDestination = WordBankDestinations.HUB_ROUTE,
                route = MainDestinations.WORDBANK_ROUTE
            ) {
                composable(WordBankDestinations.HUB_ROUTE) {
                    WordBankScreen(
                        onNavigateToReview = {
                            mainNavController.navigate(WordBankDestinations.REVIEW_ROUTE)
                        },
                        onNavigateToDiscovery = {
                            mainNavController.navigate(WordBankDestinations.LEVEL_SELECT_ROUTE)
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
                composable(WordBankDestinations.LEVEL_SELECT_ROUTE) {
                    WordLevelScreen(
                        onLevelSelected = { level ->
                            mainNavController.navigate("wordbank_topic_select/$level")
                        },
                        onNavigateBack = { mainNavController.popBackStack() }
                    )
                }
                composable(
                    route = WordBankDestinations.TOPIC_SELECT_ROUTE,
                    arguments = listOf(navArgument("level") { type = NavType.StringType })
                ) { backStackEntry ->
                    val level = backStackEntry.arguments?.getString("level") ?: ""
                    WordTopicScreen(
                        level = level,
                        onTopicSelected = { topic ->
                            mainNavController.navigate("wordbank_word_list/$level/$topic")
                        },
                        onNavigateBack = { mainNavController.popBackStack() }
                    )
                }
                composable(
                    route = WordBankDestinations.WORD_LIST_ROUTE,
                    arguments = listOf(
                        navArgument("level") { type = NavType.StringType },
                        navArgument("topic") { type = NavType.StringType }
                    )
                ) {
                    WordListScreen(
                        onNavigateBack = { mainNavController.popBackStack() },
                        level = it.arguments?.getString("level") ?: "",
                        topic = it.arguments?.getString("topic") ?: ""
                    )
                }
            }
            // --- End of modified code ---
            composable(MainDestinations.PROGRESS_ROUTE) {
                // Pass the two specific navigation actions down to the ProgressScreen
                ProgressScreen(
                    onNavigateToIeltsResult = onNavigateToIeltsResult,
                    onNavigateToMultilevelResult = onNavigateToMultilevelResult
                )
            }
            composable(MainDestinations.PROFILE_ROUTE) {
                ProfileScreen()
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {

    val practiceString = stringResource(id = R.string.navbar_practice)
    val vocabularyString = stringResource(id = R.string.navbar_vocabulary)
    val progressString = stringResource(id = R.string.navbar_progress)
    val profileString = stringResource(id = R.string.navbar_profile)

    val items = listOf(
        Triple(practiceString, Icons.Default.Home, MainDestinations.PRACTICE_ROUTE),
        Triple(vocabularyString, Icons.Default.MenuBook, MainDestinations.WORDBANK_ROUTE),
        Triple(progressString, Icons.Default.History, MainDestinations.PROGRESS_ROUTE),
        Triple(profileString, Icons.Default.Person, MainDestinations.PROFILE_ROUTE)
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