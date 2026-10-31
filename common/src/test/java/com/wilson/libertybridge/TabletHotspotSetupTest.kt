package com.wilson.libertybridge

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import com.wilson.libertybridge.adb.LocalAdb
import com.wilson.libertybridge.network.TabletHotspotSettings
import com.wilson.libertybridge.orchestration.WirelessHotspotMode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class TabletHotspotSetupTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun reset() {
        context.getSharedPreferences("diplay_tablet_hotspot", 0).edit().clear().commit()
        context.getSharedPreferences("xcertplay_airplay", 0).edit().clear().commit()
    }

    @Test fun freshInstallAndExistingHotspotConfigurationRemainOptedOut() {
        assertFalse(TabletHotspotSettings.enabled(context))
        configureHotspot()
        assertFalse(eligible())
    }

    @Test fun losingAdbHidesTheSettingWithoutClearingOrDisablingTheSavedChoice() {
        configureHotspot()
        TabletHotspotSettings.setEnabled(context, true)
        assertFalse(TabletHotspotSettings.visible(true, LocalAdb.Access.UNREACHABLE))
        assertTrue(TabletHotspotSettings.enabled(context))
        assertTrue(eligible())
    }

    @Test fun startupNeedsValidCredentialsAndNoExistingSession() {
        TabletHotspotSettings.setEnabled(context, true)
        assertFalse(eligible())
        configureHotspot()
        assertTrue(eligible())
        assertFalse(TabletHotspotSetup.shouldStartOnLaunch(context, hasSession = true))
        AirPlayPersistence.saveManualHotspotPassphrase(context, "short")
        assertFalse(eligible())
    }

    @Test fun wiredModeNeverPreparesAHotspotEvenWhenTheChoiceIsSaved() {
        configureHotspot()
        TabletHotspotSettings.setEnabled(context, true)
        AirPlayPersistence.saveWirelessEnabled(context, false)
        assertFalse(eligible())
        assertFalse(TabletHotspotSettings.shouldEnable(context, false, WirelessHotspotMode.MANUAL))
        assertTrue(TabletHotspotSettings.enabled(context))
    }

    @Test fun wifiDirectAndLocalOnlyHotspotAreNotAutomaticallyManaged() {
        configureHotspot()
        TabletHotspotSettings.setEnabled(context, true)
        AirPlayPersistence.saveWirelessHotspotMode(context, WirelessHotspotMode.WIFI_P2P)
        assertFalse(eligible())
        assertFalse(TabletHotspotSettings.shouldEnable(context, true, WirelessHotspotMode.LOCAL_ONLY_HOTSPOT))
    }

    @Test fun disablingTheChoiceCancelsFutureStartupWithoutChangingConnectionSettings() {
        configureHotspot()
        TabletHotspotSettings.setEnabled(context, true)
        assertTrue(eligible())
        TabletHotspotSettings.setEnabled(context, false)
        assertFalse(eligible())
        assertEquals("Tablet hotspot", AirPlayPersistence.loadManualHotspotSsid(context))
        assertEquals("12345678", AirPlayPersistence.loadManualHotspotPassphrase(context))
        assertEquals(WirelessHotspotMode.MANUAL, AirPlayPersistence.loadWirelessHotspotMode(context))
    }

    private fun eligible() = TabletHotspotSetup.shouldStartOnLaunch(context, hasSession = false)

    private fun installPackage(name: String, system: Boolean) {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = name
            applicationInfo = ApplicationInfo().apply {
                packageName = name
                flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0
            }
        })
    }

    private fun configureHotspot() {
        AirPlayPersistence.saveWirelessEnabled(context, true)
        AirPlayPersistence.saveWirelessHotspotMode(context, WirelessHotspotMode.MANUAL)
        AirPlayPersistence.saveManualHotspotSsid(context, "Tablet hotspot")
        AirPlayPersistence.saveManualHotspotPassphrase(context, "12345678")
    }
}
