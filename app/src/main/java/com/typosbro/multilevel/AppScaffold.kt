package com.typosbro.multilevel

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.typosbro.multilevel.ui.screen.SecondScreen
import com.typosbro.multilevel.ui.screen.ThirdScreen
import com.typosbro.multilevel.ui.screen.VoiceRecognitionScreen
import org.vosk.Model

@Composable
fun AppScaffold(
    navController: NavHostController,
    partialResults: String,
    onStartMicRecognition: () -> Unit,
    onStopRecognition: () -> Unit,
    completedMessages: List<String>,
    isPaused: Boolean
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.VoiceRecognition.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.VoiceRecognition.route) {
                VoiceRecognitionScreen(
                    partialResults = partialResults,
                    onStartMicRecognition = onStartMicRecognition,
                    onStopRecognition = onStopRecognition,
                    isPaused = isPaused,
                    completedMessages = completedMessages
                )
            }
            composable(Screen.SecondScreen.route) {
                SecondScreen()
            }
            composable(Screen.ThirdScreen.route) {
                ThirdScreen()
            }
        }
    }
}


private val items = listOf(
    Screen.VoiceRecognition,
    Screen.SecondScreen,
    Screen.ThirdScreen
)