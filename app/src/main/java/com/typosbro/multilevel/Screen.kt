package com.typosbro.multilevel


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object VoiceRecognition : Screen("voice_recognition", "Voice", Icons.Default.Home)
    object SecondScreen : Screen("second_screen", "Second", Icons.Default.Info)
    object ThirdScreen : Screen("third_screen", "Third", Icons.Default.Settings)
}

