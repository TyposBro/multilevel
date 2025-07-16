package com.typosbro.multilevel.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
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
import com.typosbro.multilevel.ui.screens.subscription.SubscriptionScreen
import com.typosbro.multilevel.ui.screens.wordbank.ExploreLevelScreen
import com.typosbro.multilevel.ui.screens.wordbank.ExploreTopicScreen
import com.typosbro.multilevel.ui.screens.wordbank.WordBankScreen
import com.typosbro.multilevel.ui.screens.wordbank.WordReviewScreen

object MainDestinations {
    const val PRACTICE_ROUTE = "practice"
    const val WORDBANK_ROUTE = "wordbank"
    const val PROGRESS_ROUTE = "progress"
    const val PROFILE_ROUTE = "profile"
    const val SUBSCRIPTION_ROUTE = "subscription"
}

object WordBankDestinations {
    const val HUB_ROUTE = "wordbank_hub"
    const val REVIEW_ROUTE = "wordbank_review"
    const val EXPLORE_LEVEL_ROUTE = "wordbank_explore_level"
    const val EXPLORE_TOPIC_ROUTE = "wordbank_explore_topic/{level}"
}

@Composable
fun MainScreen(
    onNavigateToMultilevel: (String) -> Unit,
    onNavigateToMultilevelResult: (resultId: String) -> Unit,
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
                PracticeHubScreen(
                    onPartSelected = { practicePart ->
                        onNavigateToMultilevel(practicePart.name)
                    }
                )
            }

            navigation(
                startDestination = WordBankDestinations.HUB_ROUTE,
                route = MainDestinations.WORDBANK_ROUTE
            ) {
                composable(WordBankDestinations.HUB_ROUTE) {
                    WordBankScreen(
                        onNavigateToReview = {
                            mainNavController.navigate(WordBankDestinations.REVIEW_ROUTE)
                        },
                        onNavigateToExplore = {
                            mainNavController.navigate(WordBankDestinations.EXPLORE_LEVEL_ROUTE)
                        }
                    )
                }
                composable(WordBankDestinations.REVIEW_ROUTE) {
                    WordReviewScreen(
                        onNavigateBack = { mainNavController.popBackStack() }
                    )
                }
                composable(WordBankDestinations.EXPLORE_LEVEL_ROUTE) {
                    ExploreLevelScreen(
                        onLevelSelected = { level ->
                            mainNavController.navigate("wordbank_explore_topic/$level")
                        },
                        onNavigateBack = { mainNavController.popBackStack() }
                    )
                }
                composable(
                    route = WordBankDestinations.EXPLORE_TOPIC_ROUTE,
                    arguments = listOf(navArgument("level") { type = NavType.StringType })
                ) { backStackEntry ->
                    ExploreTopicScreen(
                        level = backStackEntry.arguments?.getString("level") ?: "",
                        onNavigateBack = { mainNavController.popBackStack() }
                    )
                }
            }

            composable(MainDestinations.PROGRESS_ROUTE) {
                ProgressScreen(
                    onNavigateToMultilevelResult = onNavigateToMultilevelResult,
                )
            }
            composable(MainDestinations.PROFILE_ROUTE) {
                ProfileScreen(onNavigateToSubscription = {
                    mainNavController.navigate(MainDestinations.SUBSCRIPTION_ROUTE)
                })
            }

            composable(MainDestinations.SUBSCRIPTION_ROUTE) {
                SubscriptionScreen(onNavigateBack = { mainNavController.popBackStack() })
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
        Triple(
            vocabularyString,
            Icons.AutoMirrored.Filled.MenuBook,
            MainDestinations.WORDBANK_ROUTE
        ),
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
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}