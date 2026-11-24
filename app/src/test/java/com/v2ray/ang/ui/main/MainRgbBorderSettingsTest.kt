package com.v2ray.ang.ui.main

import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.SettingsChangeManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MainRgbBorderSettingsTest {

    @BeforeEach
    fun clearPendingFlags() {
        SettingsChangeManager.consumeRestartService()
        SettingsChangeManager.consumeSetupGroupTab()
    }

    @Test
    fun mainUiStateDefaultsRgbBorderToEnabled() {
        val state = MainUiState()
        assertTrue(state.rgbBorderEnabled)
        assertFalse(state.isRunning)
    }

    @Test
    fun togglingRgbBorderPrefDoesNotRequestServiceRestart() {
        SettingsChangeManager.notifySettingChanged(AppConfig.PREF_RGB_BORDER_ENABLED)

        assertFalse(SettingsChangeManager.consumeRestartService())
        assertTrue(SettingsChangeManager.consumeSetupGroupTab())
    }

    @Test
    fun nonUiPreferenceStillRequestsServiceRestart() {
        SettingsChangeManager.notifySettingChanged(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY)

        assertTrue(SettingsChangeManager.consumeRestartService())
        assertEquals(true, SettingsChangeManager.consumeSetupGroupTab())
    }
}
