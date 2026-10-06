package com.gecko.app

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.gecko.core.designsystem.theme.LocalGeckoMotionEnabled
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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
import com.gecko.feature.settings.navigation.AddProviderRoute
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

    val view = LocalView.current
    SideEffect {
        val window = (view.context as android.app.Activity).window
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.isAppearanceLightStatusBars = !darkTheme
        insetsController.isAppearanceLightNavigationBars = !darkTheme
    }

    GeckoTheme(darkTheme = darkTheme, dynamicColor = preferences.dynamicColorEnabled) {
        val backgroundColor = MaterialTheme.colorScheme.background
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.setBackgroundDrawable(ColorDrawable(backgroundColor.toArgb()))
        }
        GeckoNavHost()
    }
}

@Composable
private fun GeckoNavHost() {
    val navController = rememberNavController()
    val motion = LocalGeckoMotionEnabled.current
    val travel = with(LocalDensity.current) { 18.dp.roundToPx() }
    NavHost(
        navController = navController,
        startDestination = ChatRoute,
        modifier = Modifier,
        enterTransition = {
                if (motion) fadeIn(tween(GeckoMotion.DURATION_STANDARD)) +
                    slideInVertically(tween(GeckoMotion.DURATION_STANDARD, easing = GeckoMotion.EasingEmphasized)) { travel }
                else EnterTransition.None
        },
        exitTransition = {
                if (motion) fadeOut(tween(GeckoMotion.DURATION_QUICK)) else ExitTransition.None
        },
        popEnterTransition = {
                if (motion) fadeIn(tween(GeckoMotion.DURATION_STANDARD)) else EnterTransition.None
        },
        popExitTransition = {
                if (motion) fadeOut(tween(GeckoMotion.DURATION_QUICK)) +
                    slideOutVertically(tween(GeckoMotion.DURATION_STANDARD, easing = GeckoMotion.EasingOutgoing)) { travel }
                else ExitTransition.None
        },
    ) {
        composable<ChatRoute> {
            ChatScreen(
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onConnectProvider = { navController.navigate(AddProviderRoute) },
            )
        }
        settingsGraph(navController, onConnected = { navController.popBackStack<ChatRoute>(inclusive = false) })
    }
}
