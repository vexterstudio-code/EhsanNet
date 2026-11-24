package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConnectionStateAndSpeedTest {

    @Test
    fun formatSpeedBytesPerSecHandlesIdleAndDynamicUnits() {
        assertEquals("0 KB/s", formatSpeedBytesPerSec(0L))
        assertEquals("0 KB/s", formatSpeedBytesPerSec(-42L))
        assertEquals("512 B/s", formatSpeedBytesPerSec(512L))
        assertEquals("1.5 KB/s", formatSpeedBytesPerSec(1536L))
        assertEquals("250 KB/s", formatSpeedBytesPerSec(250L * 1024L))
        assertEquals("2.50 MB/s", formatSpeedBytesPerSec((2.5 * 1024 * 1024).toLong()))
        assertEquals("1.25 GB/s", formatSpeedBytesPerSec((1.25 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun connectionModeFromPrefDefaultsToNormal() {
        assertEquals(ConnectionMode.NORMAL, ConnectionMode.fromPref(null))
        assertEquals(ConnectionMode.NORMAL, ConnectionMode.fromPref("unknown"))
        assertEquals(ConnectionMode.NORMAL, ConnectionMode.fromPref("normal"))
        assertEquals(ConnectionMode.BOOST, ConnectionMode.fromPref("boost"))
    }

    @Test
    fun plannerGuardsDoubleTapsDuringTransitionStates() {
        listOf(
            ConnectionStatus.SCANNING_BOOST,
            ConnectionStatus.CONNECTING,
            ConnectionStatus.DISCONNECTING,
        ).forEach { busyStatus ->
            assertEquals(
                ConnectionTransitionDecision.IgnoreBusy,
                planConnectionAction(
                    currentStatus = busyStatus,
                    activeMode = ConnectionMode.NORMAL,
                    targetMode = ConnectionMode.NORMAL,
                    selectedConfigId = "cfg1",
                    lastActiveConfigId = "cfg1",
                ),
            )
            assertEquals(
                ConnectionTransitionDecision.IgnoreBusy,
                planConnectionAction(
                    currentStatus = busyStatus,
                    activeMode = ConnectionMode.BOOST,
                    targetMode = ConnectionMode.BOOST,
                    selectedConfigId = "cfg1",
                    lastActiveConfigId = "cfg1",
                ),
            )
        }
    }

    @Test
    fun plannerHandlesNormalFallbackAndMissingSelection() {
        assertEquals(
            ConnectionTransitionDecision.PromptSelectConfig,
            planConnectionAction(
                currentStatus = ConnectionStatus.DISCONNECTED,
                activeMode = ConnectionMode.NORMAL,
                targetMode = ConnectionMode.NORMAL,
                selectedConfigId = null,
                lastActiveConfigId = null,
            ),
        )
        assertEquals(
            ConnectionTransitionDecision.ConnectNormal("fallback-id"),
            planConnectionAction(
                currentStatus = ConnectionStatus.DISCONNECTED,
                activeMode = ConnectionMode.NORMAL,
                targetMode = ConnectionMode.NORMAL,
                selectedConfigId = null,
                lastActiveConfigId = "fallback-id",
            ),
        )
        assertEquals(
            ConnectionTransitionDecision.Disconnect,
            planConnectionAction(
                currentStatus = ConnectionStatus.CONNECTED,
                activeMode = ConnectionMode.NORMAL,
                targetMode = ConnectionMode.NORMAL,
                selectedConfigId = "cfg1",
                lastActiveConfigId = null,
            ),
        )
    }

    @Test
    fun plannerCleanlySwitchesBetweenNormalAndBoostWhileConnected() {
        assertEquals(
            ConnectionTransitionDecision.SwitchToBoost,
            planConnectionAction(
                currentStatus = ConnectionStatus.CONNECTED,
                activeMode = ConnectionMode.NORMAL,
                targetMode = ConnectionMode.BOOST,
                selectedConfigId = "cfg1",
                lastActiveConfigId = null,
            ),
        )
        assertEquals(
            ConnectionTransitionDecision.SwitchToNormal("cfg1"),
            planConnectionAction(
                currentStatus = ConnectionStatus.CONNECTED,
                activeMode = ConnectionMode.BOOST,
                targetMode = ConnectionMode.NORMAL,
                selectedConfigId = "cfg1",
                lastActiveConfigId = null,
            ),
        )
        assertEquals(
            ConnectionTransitionDecision.Disconnect,
            planConnectionAction(
                currentStatus = ConnectionStatus.CONNECTED,
                activeMode = ConnectionMode.BOOST,
                targetMode = ConnectionMode.BOOST,
                selectedConfigId = "cfg1",
                lastActiveConfigId = null,
            ),
        )
    }

    @Test
    fun modeTabPlannerSwitchesDisconnectedWithoutConnectingAndGuardsBusy() {
        assertEquals(
            ModeTabSelectionDecision.SwitchTabOnly(ConnectionMode.BOOST),
            planModeTabSelection(
                currentStatus = ConnectionStatus.DISCONNECTED,
                activeMode = ConnectionMode.NORMAL,
                targetMode = ConnectionMode.BOOST,
                selectedConfigId = null,
                lastActiveConfigId = null,
            ),
        )
        assertEquals(
            ModeTabSelectionDecision.SwitchTabOnly(ConnectionMode.NORMAL),
            planModeTabSelection(
                currentStatus = ConnectionStatus.ERROR,
                activeMode = ConnectionMode.BOOST,
                targetMode = ConnectionMode.NORMAL,
                selectedConfigId = null,
                lastActiveConfigId = null,
            ),
        )
        assertEquals(
            ModeTabSelectionDecision.NoChange,
            planModeTabSelection(
                currentStatus = ConnectionStatus.CONNECTED,
                activeMode = ConnectionMode.BOOST,
                targetMode = ConnectionMode.BOOST,
                selectedConfigId = "cfg1",
                lastActiveConfigId = null,
            ),
        )
        assertEquals(
            ModeTabSelectionDecision.IgnoreBusy,
            planModeTabSelection(
                currentStatus = ConnectionStatus.SCANNING_BOOST,
                activeMode = ConnectionMode.BOOST,
                targetMode = ConnectionMode.NORMAL,
                selectedConfigId = "cfg1",
                lastActiveConfigId = null,
            ),
        )
    }

    @Test
    fun modeTabPlannerSwitchesModesCleanlyWhenConnected() {
        assertEquals(
            ModeTabSelectionDecision.SwitchToBoost,
            planModeTabSelection(
                currentStatus = ConnectionStatus.CONNECTED,
                activeMode = ConnectionMode.NORMAL,
                targetMode = ConnectionMode.BOOST,
                selectedConfigId = "cfg1",
                lastActiveConfigId = null,
            ),
        )
        assertEquals(
            ModeTabSelectionDecision.SwitchToNormal("cfg1"),
            planModeTabSelection(
                currentStatus = ConnectionStatus.CONNECTED,
                activeMode = ConnectionMode.BOOST,
                targetMode = ConnectionMode.NORMAL,
                selectedConfigId = "cfg1",
                lastActiveConfigId = null,
            ),
        )
        assertEquals(
            ModeTabSelectionDecision.PromptSelectConfig,
            planModeTabSelection(
                currentStatus = ConnectionStatus.CONNECTED,
                activeMode = ConnectionMode.BOOST,
                targetMode = ConnectionMode.NORMAL,
                selectedConfigId = null,
                lastActiveConfigId = null,
            ),
        )
    }
}
