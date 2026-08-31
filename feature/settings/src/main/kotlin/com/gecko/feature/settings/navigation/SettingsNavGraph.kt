package com.gecko.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.gecko.feature.settings.SettingsListScreen
import com.gecko.feature.settings.about.AboutScreen
import com.gecko.feature.settings.appearance.AppearanceScreen
import com.gecko.feature.settings.chatprefs.ChatPreferencesScreen
import com.gecko.feature.settings.modelprefs.DefaultModelSelectionScreen
import com.gecko.feature.settings.modelprefs.ModelPreferencesScreen
import com.gecko.feature.settings.privacy.DataPrivacyScreen
import com.gecko.feature.settings.providers.AddProviderScreen
import com.gecko.feature.settings.providers.AiProvidersScreen
import com.gecko.feature.settings.providers.ProviderDetailScreen
import com.gecko.feature.settings.providers.ProviderModelSelectionScreen

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
            onOpenProvider = { configId -> navController.navigate(ProviderDetailRoute(configId)) },
            onAddProvider = { navController.navigate(AddProviderRoute) },
        )
    }
    composable<AddProviderRoute> {
        AddProviderScreen(onBack = { navController.popBackStack() })
    }
    composable<ProviderDetailRoute> { backStackEntry ->
        val configId = backStackEntry.toRoute<ProviderDetailRoute>().configId
        ProviderDetailScreen(
            onBack = { navController.popBackStack() },
            onOpenModelSelection = { navController.navigate(ProviderModelSelectionRoute(configId)) },
        )
    }
    composable<ProviderModelSelectionRoute> {
        ProviderModelSelectionScreen(onBack = { navController.popBackStack() })
    }
    composable<ModelPreferencesRoute> {
        ModelPreferencesScreen(
            onBack = { navController.popBackStack() },
            onOpenModelSelection = { configId -> navController.navigate(DefaultModelSelectionRoute(configId)) },
        )
    }
    composable<DefaultModelSelectionRoute> {
        DefaultModelSelectionScreen(onBack = { navController.popBackStack() })
    }
    composable<DataPrivacyRoute> {
        DataPrivacyScreen(onBack = { navController.popBackStack() })
    }
    composable<AboutRoute> {
        AboutScreen(onBack = { navController.popBackStack() })
    }
}
