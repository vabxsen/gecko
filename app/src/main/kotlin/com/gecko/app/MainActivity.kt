package com.gecko.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gecko.core.designsystem.theme.GeckoMotion
import com.gecko.core.designsystem.theme.GeckoTheme
import com.gecko.core.model.preferences.ThemeMode
import com.gecko.feature.chat.ChatScreen
import com.gecko.feature.chat.navigation.ChatRoute
import com.gecko.feature.settings.navigation.SettingsRoute
import com.gecko.feature.settings.navigation.settingsGraph
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GeckoApp()
        }
    }
}

@Composable
private fun GeckoApp(appViewModel: GeckoAppViewModel = hiltViewModel()) {
    val preferences by appViewModel.userPreferences.collectAsStateWithLifecycle()
    val darkTheme = when (preferences.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    GeckoTheme(darkTheme = darkTheme, dynamicColor = preferences.dynamicColorEnabled) {
        GeckoNavHost()
    }
}

@Composable
private fun GeckoNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = ChatRoute,
        modifier = Modifier,
        enterTransition = {
            slideInHorizontally(tween(GeckoMotion.DURATION_STANDARD, easing = GeckoMotion.EasingIncoming)) { it / 4 } +
                fadeIn(tween(GeckoMotion.DURATION_STANDARD, easing = GeckoMotion.EasingIncoming))
        },
        exitTransition = {
            slideOutHorizontally(tween(GeckoMotion.DURATION_STANDARD, easing = GeckoMotion.EasingOutgoing)) { -it / 4 } +
                fadeOut(tween(GeckoMotion.DURATION_QUICK, easing = GeckoMotion.EasingOutgoing))
        },
        popEnterTransition = {
            slideInHorizontally(tween(GeckoMotion.DURATION_STANDARD, easing = GeckoMotion.EasingIncoming)) { -it / 4 } +
                fadeIn(tween(GeckoMotion.DURATION_STANDARD, easing = GeckoMotion.EasingIncoming))
        },
        popExitTransition = {
            slideOutHorizontally(tween(GeckoMotion.DURATION_STANDARD, easing = GeckoMotion.EasingOutgoing)) { it / 4 } +
                fadeOut(tween(GeckoMotion.DURATION_QUICK, easing = GeckoMotion.EasingOutgoing))
        },
    ) {
        composable<ChatRoute> {
            ChatScreen(onOpenSettings = { navController.navigate(SettingsRoute) })
        }
        settingsGraph(navController)
    }
}
