package com.wilson.libertybridge.media

import android.content.Context
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AmbientMusicTickingTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun noLampUpdateRunsWhileTheFeatureIsOff() {
        context.getSharedPreferences("ambient_music", Context.MODE_PRIVATE).edit().clear().commit()
        val sink = AmbientMusicController.openSink(context)
        try {
            settle()
            assertNull(field("ticking"))
            AmbientMusicController.settingsChanged(context)
            settle()
            assertNull(field("ticking"))
        } finally {
            AmbientMusicController.closeSink(sink)
            settle()
        }
    }

