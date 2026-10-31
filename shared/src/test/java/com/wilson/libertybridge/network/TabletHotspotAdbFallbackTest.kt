package com.wilson.libertybridge.network

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class TabletHotspotAdbFallbackTest {
    private fun deadline(ms: Long = 1_000) = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ms)

    @Test fun genericAospHelpNeverTriggersTheGuessedCommands() {
        assertNull(TabletHotspotAdbFallback.advertisedCommand("connectivity", "Connectivity service commands:\n  help\n  airplane-mode [enable|disable]"))
        assertNull(TabletHotspotAdbFallback.advertisedCommand("tethering", "Unknown command: help"))
        assertNull(TabletHotspotAdbFallback.advertisedCommand("wifi", "  start-softap <ssid> open"))
    }

    @Test fun advertisedFirmwareFormsAreSelectedWithoutProbingMutatingCommands() {
        assertEquals("cmd connectivity start-tethering wifi", TabletHotspotAdbFallback.advertisedCommand("connectivity", "  start-tethering wifi"))
        assertEquals("cmd tethering start-tethering wifi", TabletHotspotAdbFallback.advertisedCommand("tethering", "  start-tethering <wifi|usb|bluetooth>"))
        assertEquals("cmd tethering start wifi", TabletHotspotAdbFallback.advertisedCommand("tethering", "  start wifi [options]"))
        assertNull(TabletHotspotAdbFallback.advertisedCommand("tethering", "Example: start wifi\n  start <type>"))
    }

    @Test fun aZeroExitCodeCannotHidePermissionOrUnknownCommandOutput() {
        assertTrue(TabletHotspotAdbFallback.accepted("Started\nDIPLAY_HOTSPOT_EXIT:0"))
        for (output in listOf(null, "", "DIPLAY_HOTSPOT_EXIT:1", "Unknown command\nDIPLAY_HOTSPOT_EXIT:0", "SecurityException\nDIPLAY_HOTSPOT_EXIT:0")) {
            assertFalse(TabletHotspotAdbFallback.accepted(output))
        }
    }

    @Test fun shellAcceptanceStillWaitsForAnActualApState() {
        var on = false
        val commands = mutableListOf<String>()
        assertTrue(TabletHotspotAdbFallback.start(deadline(), { false }, { on }, { command, _ ->
            commands += command
            if (command.endsWith("help")) "  start-tethering wifi" else {
                on = true
                "DIPLAY_HOTSPOT_EXIT:0"
            }
        }, {}))
        assertEquals(2, commands.size)
        assertFalse(commands.last().contains("||"))
        assertTrue(commands.last().contains("\$?"))
        assertFalse(TabletHotspotAdbFallback.start(deadline(20), { false }, { false }, { command, _ ->
            if (command.endsWith("help")) "  start-tethering wifi" else "DIPLAY_HOTSPOT_EXIT:0"
        }, {}))
    }

    @Test fun noStatusOrCapabilityMeansNoMutation() {
        val commands = mutableListOf<String>()
        assertFalse(TabletHotspotAdbFallback.start(deadline(), { false }, { null }, { command, _ -> commands += command; null }, {}))
        assertEquals(listOf("dumpsys wifi"), commands)
        commands.clear()
        assertFalse(TabletHotspotAdbFallback.start(deadline(), { false }, { false }, { command, _ -> commands += command; "  help" }, {}))
        assertTrue(commands.all { it.endsWith("help") })
    }

    @Test fun theReportSaysWhyTheAdbPathStopped() {
        // #616: an Android 10 DiLink 3.0 failed within 90 ms and the report did not say which step stopped.
        val logs = mutableListOf<String>()
        TabletHotspotAdbFallback.start(deadline(), { false }, { null }, { _, _ -> null }, { logs += it })
        assertEquals(listOf("tablet hotspot: ADB cannot read the AP state; not starting it"), logs)

        logs.clear()
        TabletHotspotAdbFallback.start(deadline(), { false }, { false }, { _, _ -> "Connectivity service commands:\n  help" }, { logs += it })
        assertEquals(listOf("tablet hotspot: firmware lists no hotspot start command in cmd connectivity/tethering help"), logs)

        logs.clear()
        TabletHotspotAdbFallback.start(deadline(), { false }, { false }, { command, _ ->
            if (command.endsWith("help")) "  start-tethering wifi" else "\nSecurityException: no tether permission\nDIPLAY_HOTSPOT_EXIT:0"
        }, { logs += it })
        assertEquals(listOf("tablet hotspot: firmware rejected 'cmd connectivity start-tethering wifi': " +
            "SecurityException: no tether permission"), logs)
    }

    @Test fun unknownAppStatusCanBeConfirmedThroughCurrentTetheredApDump() {
        var on = false
        assertTrue(TabletHotspotAdbFallback.start(deadline(), { false }, { null }, { command, _ ->
            when {
                command == "dumpsys wifi" -> "mWifiApState: ${if (on) 13 else 11}"
                command.endsWith("help") -> "  start-tethering wifi"
                else -> { on = true; "DIPLAY_HOTSPOT_EXIT:0" }
            }
        }, {}))
    }

    @Test fun dumpParserIgnoresHistoricalStationAndLocalOnlyState() {
        assertNull(TabletHotspotAdbFallback.apState("log: mWifiApState=13\nWi-Fi is enabled\nwlan1 has address"))
        assertNull(TabletHotspotAdbFallback.apState("mWifiApState: 11\nmSoftApState: 13"))
        val started = "--Dump of SoftApManager--\ncurrent StateMachine mode: StartedState\nmIfaceIsUp: true\nmMode: 1"
        assertEquals(true, TabletHotspotAdbFallback.apState(started))
        assertNull(TabletHotspotAdbFallback.apState("old log: " + started))
        assertNull(TabletHotspotAdbFallback.apState(started.replace("mMode: 1", "mMode: 2")))
        assertEquals(false, TabletHotspotAdbFallback.apState(started.replace("StartedState", "IdleState").replace("true", "false")))
    }

    @Test fun dumpParserDoesNotDiscardUnknownOrConflictingCurrentStates() {
        assertNull(TabletHotspotAdbFallback.apState("mWifiApState: 13\nmSoftApState: 99"))
        val started = "Dump of SoftApManager id=1\ncurrent StateMachine mode: StartedState\nmIfaceIsUp: true\nmOriginalModeConfiguration.targetMode: 1"
        assertEquals(true, TabletHotspotAdbFallback.apState(started))
        assertNull(TabletHotspotAdbFallback.apState(started.replace("Configuration.targetMode", "ConfigurationXtargetMode")))
        assertNull(TabletHotspotAdbFallback.apState("mWifiApState: 13\n" + started.replace("targetMode: 1", "targetMode: 2")))
        assertNull(TabletHotspotAdbFallback.apState(started + "\n" + started.replace("StartedState", "StartingState")))
        assertNull(TabletHotspotAdbFallback.apState(started + "\n" + started.replace("StartedState", "IdleState").replace("true", "false")))
    }

    @Test fun cancellationAfterCapabilityReadPreventsMutation() {
        var cancelled = false
        val commands = mutableListOf<String>()
        assertFalse(TabletHotspotAdbFallback.start(deadline(), { cancelled }, { false }, { command, _ ->
            commands += command; cancelled = true; "  start-tethering wifi"
        }, {}))
        assertEquals(listOf("cmd connectivity help"), commands)
    }

    @Test fun boundedDeadlineAbortsStalledIoWithoutTakingASecondBudget() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val aborted = AtomicBoolean(false)
        val started = System.nanoTime()
        assertFalse(TabletHotspotAdbFallback.bounded(deadline(50), { false }, { aborted.set(true); release.countDown() }) {
            entered.countDown(); release.await(2, TimeUnit.SECONDS); false
        })
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        assertTrue(aborted.get())
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1_000)
    }

    @Test fun boundedCancellationAbortsBeforeAReadTimeout() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancelled = AtomicBoolean(false)
        val aborted = AtomicBoolean(false)
        val cancellation = thread { entered.await(1, TimeUnit.SECONDS); cancelled.set(true) }
        assertFalse(TabletHotspotAdbFallback.bounded(deadline(2_000), cancelled::get, { aborted.set(true); release.countDown() }) {
            entered.countDown(); release.await(2, TimeUnit.SECONDS); false
        })
        cancellation.join(1_000)
        assertTrue(aborted.get())
    }

    @Test fun withoutAFirmwareCommandTheShellStartsTheSavedHotspot() {
        // #616: Android 10 trinket and Android 12 DiLink 5.0 head units listed no command, and the app-side call failed.
        var on = false
        val commands = mutableListOf<String>()
        val logs = mutableListOf<String>()
        val helper = TabletHotspotStartMain.command("/data/app/diplay/base.apk")
        assertTrue(TabletHotspotAdbFallback.start(deadline(), { false }, { on }, { command, _ ->
            commands += command
            when {
                command.endsWith("help") -> "Connectivity service commands:\n  help"
                command == helper -> { on = true; "WARNING: linker noise\n${TabletHotspotStartMain.HEADER}|result 0" }
                else -> null
            }
        }, { logs += it }, helper))
        assertEquals(helper, commands.last())
        assertTrue(helper.contains("app_process /system/bin ${TabletHotspotStartMain::class.java.name}"))
        assertEquals(listOf(
            "tablet hotspot: firmware lists no hotspot start command in cmd connectivity/tethering help",
            "tablet hotspot: shell tethering request result 0; waiting for AP enabled state",
            "tablet hotspot: AP enabled state confirmed after local ADB request",
        ), logs)
    }

    @Test fun aRefusedShellRequestIsReportedAndNothingElseIsTried() {
        val logs = mutableListOf<String>()
        val commands = mutableListOf<String>()
        assertFalse(TabletHotspotAdbFallback.start(deadline(), { false }, { false }, { command, _ ->
            commands += command
            if (command.endsWith("help")) "  help" else "${TabletHotspotStartMain.HEADER}|error SecurityException: no tether permission"
        }, { logs += it }, "helper"))
        assertEquals(listOf("cmd connectivity help", "cmd tethering help", "helper"), commands)
        assertEquals("tablet hotspot: shell tethering request failed: error SecurityException: no tether permission", logs.last())

        assertFalse(TabletHotspotStartMain.accepted(TabletHotspotStartMain.parse("Exception in thread main")))
        assertFalse(TabletHotspotStartMain.accepted("result 12"))
        assertTrue(TabletHotspotStartMain.accepted("requested"))
    }
}
