package org.milliytechnology.spiko.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import org.milliytechnology.spiko.R
import org.milliytechnology.spiko.ui.screens.practice.PracticeHubScreen
import org.milliytechnology.spiko.ui.screens.profile.ProfileScreen
import org.milliytechnology.spiko.ui.screens.progress.ProgressScreen
import org.milliytechnology.spiko.ui.screens.subscription.SubscriptionScreen
import org.milliytechnology.spiko.ui.screens.wordbank.ExploreLevelScreen
import org.milliytechnology.spiko.ui.screens.wordbank.ExploreTopicScreen
import org.milliytechnology.spiko.ui.screens.wordbank.WordBankScreen
import org.milliytechnology.spiko.ui.screens.wordbank.WordReviewScreen
import org.milliytechnology.spiko.ui.viewmodels.AuthViewModel
import org.milliytechnology.spiko.ui.viewmodels.ProfileViewModel
import org.milliytechnology.spiko.ui.viewmodels.SettingsViewModel

// --- Navigation Destinations & Items ---

/**
 * A sealed class to define the primary navigation items in the bottom bar.
 * This provides better type safety and readability than using Triple or Pair.
 */
sealed class MainNavigationItem(
    val route: String,
    @StringRes val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Practice : MainNavigationItem(
        "practice", R.string.navbar_practice, Icons.Filled.Home, Icons.Outlined.Home
    )

    object WordBank : MainNavigationItem(
        "wordbank",
        R.string.navbar_vocabulary,
        Icons.AutoMirrored.Filled.MenuBook,
        Icons.AutoMirrored.Outlined.MenuBook
    )

    object Progress : MainNavigationItem(
        "progress", R.string.navbar_progress, Icons.Filled.History, Icons.Outlined.History
    )

    object Profile : MainNavigationItem(
        "profile", R.string.navbar_profile, Icons.Filled.Person, Icons.Outlined.Person
    )
}

object MainDestinations {
    const val SUBSCRIPTION_ROUTE = "subscription"
}

object WordBankDestinations {
    const val HUB_ROUTE = "wordbank_hub"
    const val REVIEW_ROUTE = "wordbank_review"
    const val EXPLORE_LEVEL_ROUTE = "wordbank_explore_level"
    const val EXPLORE_TOPIC_ROUTE = "wordbank_explore_topic/{level}"
}


// --- Main Screen Composable ---

@Composable
fun MainScreen(
    onNavigateToMultilevel: (String) -> Unit,
    onNavigateToMultilevelResult: (resultId: String) -> Unit,
    profileViewModel: ProfileViewModel,
    authViewModel: AuthViewModel,
    settingsViewModel: SettingsViewModel
) {
    val mainNavController = rememberNavController()
    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController = mainNavController)
        }
    ) { innerPadding ->
        NavHost(
            navController = mainNavController,
            startDestination = MainNavigationItem.Practice.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(MainNavigationItem.Practice.route) {
                PracticeHubScreen(
                    onPartSelected = { practicePart ->
                        onNavigateToMultilevel(practicePart.name)
                    }
                )
            }

            // Encapsulate WordBank navigation into its own graph builder function
            wordBankNavGraph(mainNavController)

            composable(MainNavigationItem.Progress.route) {
                ProgressScreen(
                    onNavigateToMultilevelResult = onNavigateToMultilevelResult,
                )
            }
            composable(MainNavigationItem.Profile.route) {
                ProfileScreen(
                    profileViewModel = profileViewModel,
                    authViewModel = authViewModel,
                    settingsViewModel = settingsViewModel,
                    onNavigateToSubscription = {
                        mainNavController.navigate(MainDestinations.SUBSCRIPTION_ROUTE)
                    })
            }
            composable(MainDestinations.SUBSCRIPTION_ROUTE) {
                SubscriptionScreen(onNavigateBack = { mainNavController.popBackStack() })
            }
        }
    }
}

// --- Navigation Graph Builders ---

/**
 * Encapsulates the WordBank feature's navigation logic into a modular graph.
 */
private fun NavGraphBuilder.wordBankNavGraph(navController: NavHostController) {
    navigation(
        startDestination = WordBankDestinations.HUB_ROUTE,
        route = MainNavigationItem.WordBank.route
    ) {
        composable(WordBankDestinations.HUB_ROUTE) {
            WordBankScreen(
                onNavigateToReview = { navController.navigate(WordBankDestinations.REVIEW_ROUTE) },
                onNavigateToExplore = { navController.navigate(WordBankDestinations.EXPLORE_LEVEL_ROUTE) }
            )
        }
        composable(WordBankDestinations.REVIEW_ROUTE) {
            WordReviewScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(WordBankDestinations.EXPLORE_LEVEL_ROUTE) {
            ExploreLevelScreen(
                onLevelSelected = { level -> navController.navigate("wordbank_explore_topic/$level") },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = WordBankDestinations.EXPLORE_TOPIC_ROUTE,
            arguments = listOf(navArgument("level") { type = NavType.StringType })
        ) { backStackEntry ->
            ExploreTopicScreen(
                level = backStackEntry.arguments?.getString("level") ?: "",
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

// --- UI Components ---

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(
        MainNavigationItem.Practice,
        MainNavigationItem.WordBank,
        MainNavigationItem.Progress,
        MainNavigationItem.Profile
    )

    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        items.forEach { item ->
            val isSelected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            val label = stringResource(item.labelResId)
            NavigationBarItem(
                icon = {
                    // Use filled icon when selected, outlined otherwise
                    val icon = if (isSelected) item.selectedIcon else item.unselectedIcon
                    Icon(icon, contentDescription = label)
                },
                label = { Text(label) },
                selected = isSelected,
                onClick = {
                    navController.navigate(item.route) {
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