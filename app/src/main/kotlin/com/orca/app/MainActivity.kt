package com.orca.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.orca.core.designsystem.theme.OrcaTheme
import com.orca.core.model.preferences.ThemeMode
import com.orca.feature.chat.ChatScreen
import com.orca.feature.chat.navigation.ChatRoute
import com.orca.feature.settings.navigation.SettingsRoute
import com.orca.feature.settings.navigation.settingsGraph
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OrcaApp()
        }
    }
}

@Composable
private fun OrcaApp(appViewModel: OrcaAppViewModel = hiltViewModel()) {
    val preferences by appViewModel.userPreferences.collectAsStateWithLifecycle()
    val darkTheme = when (preferences.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    OrcaTheme(darkTheme = darkTheme, dynamicColor = preferences.dynamicColorEnabled) {
        OrcaNavHost()
    }
}

@Composable
private fun OrcaNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ChatRoute, modifier = Modifier) {
        composable<ChatRoute> {
            ChatScreen(onOpenSettings = { navController.navigate(SettingsRoute) })
        }
        settingsGraph(navController)
    }
}
