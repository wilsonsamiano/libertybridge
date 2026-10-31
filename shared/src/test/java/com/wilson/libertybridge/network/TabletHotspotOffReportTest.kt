package com.wilson.libertybridge.network

import android.content.Context
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class TabletHotspotOffReportTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Before @After fun clear() {
        context.getSharedPreferences("diplay_tablet_hotspot", 0).edit().clear().commit()
    }

    @Test fun anOffReportCountsUntilTheDriverConnectsAnyway() {
        assertTrue(TabletHotspotSettings.reportedOff(context, false))
        TabletHotspotSettings.setOffReportIgnored(context, true)
        assertFalse(TabletHotspotSettings.reportedOff(context, false))
    }

    @Test fun anOnReportClearsTheDriversChoice() {
        TabletHotspotSettings.setOffReportIgnored(context, true)
        assertFalse(TabletHotspotSettings.reportedOff(context, true))
        assertFalse(TabletHotspotSettings.offReportIgnored(context))
        assertTrue(TabletHotspotSettings.reportedOff(context, false))
    }

    // #809: DiLink 2.0 keeps its hotspot switch on but starts the hotspot only from its own hotspot screen.
    @Test fun aHotspotThatNeverCameUpWhileReportedOffBringsTheWarningBack() {
        TabletHotspotSettings.setOffReportIgnored(context, true)
        assertTrue(TabletHotspotSettings.hotspotNeverCameUp(context, false))
        assertFalse(TabletHotspotSettings.offReportIgnored(context))
        assertTrue(TabletHotspotSettings.reportedOff(context, false))
    }

    // Geely Galaxy E8 (#604) reports off while its hotspot is on; only a report of off with no hotspot counts.
    @Test fun aFailureWithoutAnOffReportKeepsTheChoice() {
        TabletHotspotSettings.setOffReportIgnored(context, true)
        assertFalse(TabletHotspotSettings.hotspotNeverCameUp(context, null))
        assertFalse(TabletHotspotSettings.hotspotNeverCameUp(context, true))
        assertTrue(TabletHotspotSettings.offReportIgnored(context))
        TabletHotspotSettings.setOffReportIgnored(context, false)
        assertFalse(TabletHotspotSettings.hotspotNeverCameUp(context, false))
    }

    @Test fun anUnknownStateNeverBlocksAndKeepsTheChoice() {
        assertFalse(TabletHotspotSettings.reportedOff(context, null))
        TabletHotspotSettings.setOffReportIgnored(context, true)
        assertFalse(TabletHotspotSettings.reportedOff(context, null))
        assertTrue(TabletHotspotSettings.offReportIgnored(context))
    }
}
