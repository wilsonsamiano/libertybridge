package com.wilson.libertybridge.network

import android.net.wifi.WifiConfiguration
import com.wilson.libertybridge.orchestration.ManualHotspotBand
import com.wilson.libertybridge.orchestration.ManualHotspotSecurity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.wilson.libertybridge.network.TabletHotspotConfigWrite.Result

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class TabletHotspotConfigWriteTest {
    private val logs = mutableListOf<String>()
    private fun log(message: String) { logs += message }
    private val credentials = HotspotCredentials("DiPlay", "password1", ManualHotspotSecurity.WPA2)

    @Test fun onlyAndroidSevenPointOneAndElevenPlusAreSupported() {
        listOf(23, 24, 26, 27, 28, 29).forEach { assertFalse(TabletHotspotConfigWrite.supported(it)) }
        listOf(25, 30, 31, 33, 35).forEach { assertTrue(TabletHotspotConfigWrite.supported(it)) }
    }

    @Test fun unsupportedSdkIsReportedBeforeAnythingElse() {
        assertEquals(TabletHotspotConfigWrite.WriteOutcome(Result.UNSUPPORTED_SDK),
            TabletHotspotConfigWrite.write(RuntimeEnvironment.getApplication(), credentials, sdk = 29, log = ::log))
        assertTrue(logs.single().contains("unsupported Android 29"))
    }

    @Test fun wpa3IsNeverDowngraded() {
        assertEquals(TabletHotspotConfigWrite.WriteOutcome(Result.SKIPPED), TabletHotspotConfigWrite.write(RuntimeEnvironment.getApplication(),
            credentials.copy(security = ManualHotspotSecurity.WPA3), sdk = 25, log = ::log))
    }

    @Test fun credentialsWithLineBreaksAreSkipped() {
        assertEquals(TabletHotspotConfigWrite.WriteOutcome(Result.SKIPPED), TabletHotspotConfigWrite.write(RuntimeEnvironment.getApplication(),
            credentials.copy(passphrase = "pass\nword"), sdk = 25, log = ::log))
        assertEquals(TabletHotspotConfigWrite.WriteOutcome(Result.SKIPPED), TabletHotspotConfigWrite.write(RuntimeEnvironment.getApplication(),
            credentials.copy(ssid = "car\rhotspot"), sdk = 30, log = ::log))
    }

    @Test fun writeAppliesRawSsidAndWpa2Credentials() {
        val config = WifiConfiguration()
        TabletHotspotConfigWrite.applyTo(config, credentials)
        assertEquals("DiPlay", config.SSID)
        assertEquals("password1", config.preSharedKey)
        assertTrue(config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA2_PSK))
    }

    @Test fun openHotspotsStayKeyless() {
        val config = WifiConfiguration()
        TabletHotspotConfigWrite.applyTo(config, credentials.copy(security = ManualHotspotSecurity.OPEN))
        assertNull(config.preSharedKey)
        assertTrue(config.allowedKeyManagement.isEmpty)
        assertTrue(TabletHotspotConfigWrite.matches(config, credentials.copy(security = ManualHotspotSecurity.OPEN)))
        assertFalse(TabletHotspotConfigWrite.matches(config, credentials))
    }

    @Test fun fiveGhzIsWrittenThroughReflectionAndAutoKeepsTheDefault() {
        val auto = WifiConfiguration()
        TabletHotspotConfigWrite.applyTo(auto, credentials)
        assertEquals(0, auto.javaClass.getField("apBand").get(auto))
        val five = WifiConfiguration()
        TabletHotspotConfigWrite.applyTo(five, credentials.copy(band = ManualHotspotBand.GHZ_5))
        assertEquals(WifiConfiguration::class.java.getField("AP_BAND_5GHZ").getInt(null),
            five.javaClass.getField("apBand").get(five))
    }

    @Test fun appWriteVerifiesTheReadback() {
        var written: WifiConfiguration? = null
        assertEquals(TabletHotspotConfigWrite.WriteOutcome(Result.WRITTEN, "DiPlay"),
            TabletHotspotConfigWrite.writeApp(credentials,
            newConfig = { WifiConfiguration() },
            write = { written = it; true },
            readBack = { written },
            log = ::log))
    }

    @Test fun appWriteKeepsTheCarSsidWhenOneIsReadable() {
        val existing = WifiConfiguration().apply { SSID = "Car-Original" }
        var written: WifiConfiguration? = null
        var reads = 0
        val outcome = TabletHotspotConfigWrite.writeApp(credentials,
            newConfig = { WifiConfiguration() },
            write = { written = it; true },
            readBack = { reads++; if (reads == 1) existing else written },
            log = ::log)
        assertEquals(TabletHotspotConfigWrite.WriteOutcome(Result.WRITTEN, "Car-Original"), outcome)
        assertEquals("Car-Original", written?.SSID)
        assertEquals("password1", written?.preSharedKey)
    }

    @Test fun appWriteUsesTheGeneratedSsidWithoutAReadableConfiguration() {
        var written: WifiConfiguration? = null
        val outcome = TabletHotspotConfigWrite.writeApp(credentials,
            newConfig = { WifiConfiguration() },
            write = { written = it; true },
            readBack = { written },
            log = ::log)
        assertEquals(TabletHotspotConfigWrite.WriteOutcome(Result.WRITTEN, "DiPlay"), outcome)
        assertEquals("DiPlay", written?.SSID)
    }

    @Test fun aRejectedWriteFails() {
        assertEquals(TabletHotspotConfigWrite.WriteOutcome(Result.FAILED), TabletHotspotConfigWrite.writeApp(credentials,
            newConfig = { WifiConfiguration() },
            write = { false },
            readBack = { null },
            log = ::log))
    }

    @Test fun aMissingReadbackFails() {
        assertEquals(TabletHotspotConfigWrite.WriteOutcome(Result.FAILED), TabletHotspotConfigWrite.writeApp(credentials,
            newConfig = { WifiConfiguration() },
            write = { true },
            readBack = { null },
            log = ::log))
    }

    @Test fun aReadbackWithoutTheWrittenCredentialsFails() {
        val stale = WifiConfiguration().apply { SSID = "Other" }
        assertEquals(TabletHotspotConfigWrite.WriteOutcome(Result.FAILED), TabletHotspotConfigWrite.writeApp(credentials,
            newConfig = { WifiConfiguration() },
            write = { true },
            readBack = { stale },
            log = ::log))
        assertTrue(logs.last().contains("readback does not match"))
    }

    @Test fun reflectionFailuresAreReportedAsFailed() {
        assertEquals(TabletHotspotConfigWrite.WriteOutcome(Result.FAILED), TabletHotspotConfigWrite.writeApp(credentials,
            newConfig = { throw NoSuchMethodException("setWifiApConfiguration") },
            write = { true },
            readBack = { null },
            log = ::log))
        assertTrue(logs.last().contains("app write failed"))
    }

    @Test fun shellWriteAcceptsTheHelperOkLine() {
        var command: String? = null
        var stdin: ByteArray? = null
        assertEquals(TabletHotspotConfigWrite.WriteOutcome(Result.WRITTEN, "Car-Original"), TabletHotspotConfigWrite.writeAdb(
            { sent, _, pushed -> command = sent; stdin = pushed;
                "noise\nDIPLAY_HOTSPOT_APCONFIG_V1|ok|Q2FyLU9yaWdpbmFs\n" },
            "/data/app/diplay.apk", credentials, 4_000, ::log))
        command!!.let {
            assertTrue(it.contains("app_process /system/bin com.wilson.libertybridge.network.TabletHotspotStartMain"))
            assertTrue(it.contains(" apconfig "))
            assertTrue(it.contains(" 'DiPlay' AUTO"))
            assertFalse(it.contains("password1"))
        }
        assertEquals("9\npassword1", String(stdin!!, Charsets.UTF_8))
    }

    @Test fun anOpenWriteIsRejectedWhenTheCarHasAPassword() {
        val secured = WifiConfiguration().apply { SSID = "Car-Original"; preSharedKey = "carpass" }
        assertEquals(TabletHotspotConfigWrite.WriteOutcome(Result.OPEN_REJECTED),
            TabletHotspotConfigWrite.writeApp(credentials.copy(security = ManualHotspotSecurity.OPEN),
            newConfig = { WifiConfiguration() },
            write = { throw AssertionError("no write should happen") },
            readBack = { secured },
            log = ::log))
    }

    @Test fun anOpenWriteOverAnOpenTabletHotspotStillWrites() {
        val open = WifiConfiguration().apply {
            SSID = "Car-Original"
            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
        }
        var written: WifiConfiguration? = null
        var reads = 0
        val outcome = TabletHotspotConfigWrite.writeApp(credentials.copy(security = ManualHotspotSecurity.OPEN),
            newConfig = { WifiConfiguration() },
            write = { written = it; true },
            readBack = { reads++; if (reads == 1) open else written },
            log = ::log)
        assertEquals(TabletHotspotConfigWrite.WriteOutcome(Result.WRITTEN, "Car-Original"), outcome)
        assertTrue(written?.preSharedKey.isNullOrEmpty())
        assertTrue(written?.allowedKeyManagement?.isEmpty == true)
    }

    @Test fun isSecuredDistinguishesOpenAndSecuredConfigurations() {
        assertFalse(TabletHotspotConfigWrite.isSecured(null))
        assertFalse(TabletHotspotConfigWrite.isSecured(WifiConfiguration()))
        val none = WifiConfiguration().apply { allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE) }
        assertFalse(TabletHotspotConfigWrite.isSecured(none))
        assertTrue(TabletHotspotConfigWrite.isSecured(WifiConfiguration().apply { preSharedKey = "carpass" }))
        val wpa2 = WifiConfiguration().apply { allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA2_PSK) }
        assertTrue(TabletHotspotConfigWrite.isSecured(wpa2))
    }

    @Test fun shellWriteRefusesToStripTheCarPassword() {
        assertEquals(TabletHotspotConfigWrite.WriteOutcome(Result.OPEN_REJECTED), TabletHotspotConfigWrite.writeAdb(
            { _, _, _ -> "DIPLAY_HOTSPOT_APCONFIG_V1|error open" }, "", credentials, 4_000, ::log))
    }

    @Test fun shellWriteRejectsFailuresAndSilence() {
        assertEquals(TabletHotspotConfigWrite.WriteOutcome(Result.FAILED), TabletHotspotConfigWrite.writeAdb(
            { _, _, _ -> "DIPLAY_HOTSPOT_APCONFIG_V1|error rejected" }, "", credentials, 4_000, ::log))
        assertEquals(TabletHotspotConfigWrite.WriteOutcome(Result.FAILED), TabletHotspotConfigWrite.writeAdb(
            { _, _, _ -> null }, "", credentials, 4_000, ::log))
        assertTrue(logs.last().contains("no reply"))
    }

    @Test fun shellWriteAcceptsAnOkLineWithoutASsid() {
        assertEquals(TabletHotspotConfigWrite.WriteOutcome(Result.WRITTEN, null), TabletHotspotConfigWrite.writeAdb(
            { _, _, _ -> "DIPLAY_HOTSPOT_APCONFIG_V1|ok" }, "", credentials, 4_000, ::log))
    }

    @Test fun parsesTheApconfigProtocolLine() {
        assertEquals("ok|Car-Original",
            TabletHotspotStartMain.parseApConfig("x\nDIPLAY_HOTSPOT_APCONFIG_V1|ok|Car-Original\nmore"))
        assertEquals("error rejected", TabletHotspotStartMain.parseApConfig("DIPLAY_HOTSPOT_APCONFIG_V1|error rejected"))
        assertNull(TabletHotspotStartMain.parseApConfig("nothing"))
        assertNull(TabletHotspotStartMain.parseApConfig(null))
    }

    @Test fun readsTheSavedSsidFromTheHelperReply() {
        assertEquals("Car-Original", TabletHotspotStartMain.apConfigSsid("ok|Q2FyLU9yaWdpbmFs"))
        assertNull(TabletHotspotStartMain.apConfigSsid("ok"))
        assertNull(TabletHotspotStartMain.apConfigSsid("ok|"))
        assertNull(TabletHotspotStartMain.apConfigSsid("ok|Car-Original"))
        assertNull(TabletHotspotStartMain.apConfigSsid("error rejected"))
        assertNull(TabletHotspotStartMain.apConfigSsid(null))
        assertEquals("pass|word", TabletHotspotStartMain.apConfigPassphrase("ok|Q2Fy|cGFzc3x3b3Jk|1"))
        assertEquals("", TabletHotspotStartMain.apConfigPassphrase("ok|Q2Fy"))
        assertEquals("", TabletHotspotStartMain.apConfigPassphrase("ok|Q2Fy||1"))
        assertEquals(1, TabletHotspotStartMain.apConfigSecurity("ok|Q2Fy|cGFzc3x3b3Jk|1"))
        assertNull(TabletHotspotStartMain.apConfigSecurity("ok|Q2Fy|cGFzc3x3b3Jk"))
        assertNull(TabletHotspotStartMain.apConfigSecurity("ok|Q2Fy|cGFzc3x3b3Jk|x"))
        assertTrue(TabletHotspotStartMain.apConfigAccepted("ok|Car-Original"))
        assertTrue(TabletHotspotStartMain.apConfigAccepted("ok"))
        assertFalse(TabletHotspotStartMain.apConfigAccepted("error rejected"))
        assertFalse(TabletHotspotStartMain.apConfigAccepted(null))
    }

    @Test fun ssidQuotesAreShellEscaped() {
        val command = TabletHotspotStartMain.apConfigCommand("/a/b.apk",
            HotspotCredentials("it's", "pass'word", ManualHotspotSecurity.WPA2))
        assertTrue(command.contains(" 'it'\"'\"'s' AUTO"))
        assertFalse(command.contains("pass'word"))
    }

    @Test fun appWriteKeepsTheSavedHiddenAndBandFields() {
        val written = mutableListOf<WifiConfiguration>()
        val current = WifiConfiguration().apply {
            SSID = "Car-Original"
            hiddenSSID = true
            javaClass.getField("apBand").setInt(this, WifiConfiguration::class.java.getField("AP_BAND_2GHZ").getInt(null))
            javaClass.getField("apChannel").setInt(this, 6)
        }
        val outcome = TabletHotspotConfigWrite.writeApp(credentials,
            newConfig = { WifiConfiguration() },
            write = { written += it; true },
            readBack = { written.lastOrNull() ?: current },
            log = ::log)
        assertEquals(TabletHotspotConfigWrite.WriteOutcome(Result.WRITTEN, "Car-Original"), outcome)
        val saved = written.single()
        assertTrue(saved.hiddenSSID)
        assertEquals(6, saved.javaClass.getField("apChannel").getInt(saved))
        assertEquals(WifiConfiguration::class.java.getField("AP_BAND_2GHZ").getInt(null),
            saved.javaClass.getField("apBand").getInt(saved))
    }

    @Test fun appWriteMovesToFiveGhzOnlyWhenAskedAndTheCarIsNotThere() {
        val written = mutableListOf<WifiConfiguration>()
        val current = WifiConfiguration().apply {
            SSID = "Car-Original"
            javaClass.getField("apBand").setInt(this, WifiConfiguration::class.java.getField("AP_BAND_2GHZ").getInt(null))
            javaClass.getField("apChannel").setInt(this, 6)
        }
        val fiveGhzCredentials = credentials.copy(band = ManualHotspotBand.GHZ_5)
        TabletHotspotConfigWrite.writeApp(fiveGhzCredentials,
            newConfig = { WifiConfiguration() },
            write = { written += it; true },
            readBack = { current },
            log = ::log)
        val saved = written.single()
        assertEquals(WifiConfiguration::class.java.getField("AP_BAND_5GHZ").getInt(null),
            saved.javaClass.getField("apBand").getInt(saved))
    }
}
