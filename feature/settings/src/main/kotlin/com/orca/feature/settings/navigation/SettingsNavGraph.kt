package com.orca.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.orca.feature.settings.SettingsListScreen
import com.orca.feature.settings.about.AboutScreen
import com.orca.feature.settings.appearance.AppearanceScreen
import com.orca.feature.settings.chatprefs.ChatPreferencesScreen
import com.orca.feature.settings.modelprefs.ModelPreferencesScreen
import com.orca.feature.settings.privacy.DataPrivacyScreen
import com.orca.feature.settings.providers.AiProvidersScreen
import com.orca.feature.settings.providers.ProviderDetailScreen

fun NavGraphBuilder.settingsGraph(navController: NavController) {
    composable<SettingsRoute> {
        SettingsListScreen(
            onBack = { navController.popBackStack() },
            onNavigateAppearance = { navController.navigate(AppearanceRoute) },
            onNavigateChatPreferences = { navController.navigate(ChatPreferencesRoute) },
            onNavigateAiProviders = { navController.navigate(AiProvidersRoute) },
            onNavigateModelPreferences = { navController.navigate(ModelPreferencesRoute) },
            onNavigateDataPrivacy = { navController.navigate(DataPrivacyRoute) },
            onNavigateAbout = { navController.navigate(AboutRoute) },
        )
    }
    composable<AppearanceRoute> {
        AppearanceScreen(onBack = { navController.popBackStack() })
    }
    composable<ChatPreferencesRoute> {
        ChatPreferencesScreen(onBack = { navController.popBackStack() })
    }
    composable<AiProvidersRoute> {
        AiProvidersScreen(
            onBack = { navController.popBackStack() },
            onOpenProvider = { slug -> navController.navigate(ProviderDetailRoute(slug)) },
        )
    }
    composable<ProviderDetailRoute> {
        ProviderDetailScreen(onBack = { navController.popBackStack() })
    }
    composable<ModelPreferencesRoute> {
        ModelPreferencesScreen(onBack = { navController.popBackStack() })
    }
    composable<DataPrivacyRoute> {
        DataPrivacyScreen(onBack = { navController.popBackStack() })
    }
    composable<AboutRoute> {
        AboutScreen(onBack = { navController.popBackStack() })
    }
}
