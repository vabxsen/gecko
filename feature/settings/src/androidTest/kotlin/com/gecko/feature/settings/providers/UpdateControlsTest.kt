package com.gecko.feature.settings.providers

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.gecko.core.model.update.AppUpdate
import com.gecko.feature.settings.update.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UpdateControlsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun updateAndInstallPermissionButtonsDispatchCorrectActions() {
        val state = mutableStateOf<UpdateCheckState>(UpdateCheckState.Available(AppUpdate("9.0", "https://example.test/app.apk", "https://example.test/release")))
        var downloads = 0
        var dismissed = 0
        var settings = 0
        compose.setContent { MaterialTheme {
            UpdateResultDialog(state.value, { downloads++ }, { settings++ }, { dismissed++ })
        } }
        compose.onNodeWithText("Not now").performClick()
        compose.onNodeWithText("Download").performClick()
        compose.runOnIdle { state.value = UpdateCheckState.NeedsInstallPermission(Uri.EMPTY) }
        compose.onNodeWithText("Open settings").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(1, downloads); assertEquals(1, settings); assertEquals(2, dismissed) }
    }
}
