package com.wilson.libertybridge.media

import android.content.Context
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** The sole scheduler owns lamp I/O; audio writes only append small envelope metadata. */
object AmbientMusicController {
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "carplay-ambient-music").apply { isDaemon = true }
    }
    private val sequence = AtomicLong()
    private val activeSink = AtomicLong()
    private val activeRenderer = AtomicLong()
    private val phonePlayback = AmbientPhonePlaybackState()
    @Volatile private var artworkOwner: Any? = null
    private var albumColor: Int? = null
    @Volatile private var settings = AmbientMusicSettings.Values()
    private var context: Context? = null
    private var envelope: AmbientMusicEnvelope? = null
    private var playback: (() -> Pair<Int, Boolean>)? = null
    private var allowed = false
    private var colors = AmbientMusicColorDetector()
    private var brightnessEnvelope = AmbientMusicBrightness(settings.speed)
    private var appliedCount = 0L
    private var lastLogMillis = 0L
    private var idleSinceMillis: Long? = null

    // No ambient lamp on a tablet: the lamp session is a no-op, so the audio
    // envelope/token machinery above stays intact without any BYD lamp I/O.
    private object NoOpLampSession {
        fun isReady(): Boolean = false
        fun setEnabled(value: Boolean) {}
        fun update(desired: Triple<Int, Int, Int>) {}
        fun recover() {}
    }
    private val lampSession = NoOpLampSession

    // The 20 Hz lamp update, scheduled only while the feature (or the car's own rhythm) is on; turning it off
    // restores the lamps by itself.
    private var ticking: ScheduledFuture<*>? = null
    private const val BAND_INTERVAL_MILLIS = 90L

    /** On the executor, after [settings] changed. */
    private fun updateTicking() {
        // No lamp to drive on a tablet: never schedule lamp ticking.
        ticking?.cancel(false)
        ticking = null
    }

    @Synchronized fun openSink(context: Context?): Long {
        if (context == null) return 0L
        val token = sequence.incrementAndGet()
        activeSink.set(token)
        activeRenderer.set(0L)
        executor.execute {
            if (activeSink.get() != token) return@execute
            this.context = context.applicationContext
            settings = AmbientMusicSettings.load(context)
            resetColors()
            brightnessEnvelope = AmbientMusicBrightness(settings.speed)
            allowed = settings.enabled; envelope = null; playback = null
            idleSinceMillis = longIdle()
            if (!settings.enabled) restoreOriginal()
            updateTicking()
        }
        return token
    }

    @Synchronized internal fun openRenderer(sink: Long, envelope: AmbientMusicEnvelope,
        playback: () -> Pair<Int, Boolean>): Long {
        if (sink == 0L || sink != activeSink.get()) return 0L
        val token = sequence.incrementAndGet()
        activeRenderer.set(token)
        executor.execute {
            if (activeSink.get() != sink || activeRenderer.get() != token) return@execute
            allowed = true
            this.envelope = envelope
            this.playback = playback
        }
        return token
    }

    private fun feeding() = settings.carRhythm && settings.carRhythmFeed

    internal fun wantsPcm(token: Long): Boolean = token != 0L && token == activeRenderer.get() &&
        ((settings.enabled && settings.music) || feeding())

    /** Whether the played PCM should also be reduced to the 16 rhythm bands for the car. */
    internal fun wantsBands(token: Long): Boolean = token != 0L && token == activeRenderer.get() && feeding()

    /** Binds phone playback callbacks to the current CarPlay media owner. */
    @Synchronized fun claimPlaybackOwner(owner: Any) {
        phonePlayback.claim(owner)
        if (artworkOwner === owner) return
        artworkOwner = owner
        albumColorChanged(owner, null)
    }

    /** Already decoded artwork is sampled on the existing artwork worker, never on PCM writes. */
    fun albumColorChanged(owner: Any, color: Int?) {
        executor.execute {
            if (artworkOwner !== owner) return@execute
            val valid = color?.takeIf { it in 1..31 }
            if (albumColor == valid) return@execute
            albumColor = valid
            if (settings.colorSource == AmbientColorSource.ALBUM) resetColors(preserveTiming = true)
        }
    }

    /** Stale callbacks from a replaced media owner are ignored. */
    fun phonePlaybackChanged(owner: Any, playing: Boolean): Boolean = phonePlayback.update(owner, playing)

    @Synchronized fun releasePlaybackOwner(owner: Any): Boolean {
        if (artworkOwner === owner) {
            artworkOwner = null
            executor.execute {
                if (artworkOwner != null) return@execute
                albumColor = null
                if (settings.colorSource == AmbientColorSource.ALBUM) resetColors(preserveTiming = true)
            }
        }
        return phonePlayback.release(owner)
    }

    private fun resetColors(preserveTiming: Boolean = false) {
        val palette = ambientColorPalette(settings.colorSource, settings.selectedColors, albumColor)
        val initial = if (settings.colorSource == AmbientColorSource.ALBUM) albumColor ?: palette.first()
            else settings.color
        val random = if (settings.colorSource == AmbientColorSource.ALBUM && albumColor == null)
            ({ size: Int -> kotlin.random.Random.nextInt(size) }) else null
        if (preserveTiming) colors.updatePalette(initial, palette, random)
        else colors = AmbientMusicColorDetector(initial, palette, AmbientColorSpeed.STANDARD, random)
    }

    /** No ambient lamp exists on a tablet, so ambient support is always unavailable. */
    fun checkSupport(context: Context): java.util.concurrent.CompletableFuture<Boolean> =
        java.util.concurrent.CompletableFuture.completedFuture(false)

    fun settingsChanged(context: Context) {
        executor.execute {
            this.context = context.applicationContext
            settings = AmbientMusicSettings.load(context)
            resetColors()
            brightnessEnvelope = AmbientMusicBrightness(settings.speed)
            allowed = settings.enabled
            if (!settings.enabled) restoreOriginal()
            updateTicking()
        }
    }

    /** Silence that began before this session: the car's own lighting returns without a first lamp session. */
    private fun longIdle() = android.os.SystemClock.uptimeMillis() - AmbientMusicActivityPolicy.RESTORE_AFTER_MILLIS

    @Synchronized internal fun closeRenderer(token: Long) {
        if (token == 0L || !activeRenderer.compareAndSet(token, 0L)) return
        executor.execute {
            if (activeRenderer.get() != 0L) return@execute
            allowed = settings.enabled
            envelope = null; playback = null
            if (!settings.enabled) restoreOriginal()
        }
    }

    @Synchronized fun closeSink(token: Long) {
        if (token == 0L || !activeSink.compareAndSet(token, 0L)) return
        activeRenderer.set(0L)
        executor.execute {
            if (activeSink.get() != 0L) return@execute
            allowed = settings.enabled
            envelope = null; playback = null
            if (!settings.enabled) restoreOriginal()
        }
    }

    private fun tick() {
        val values = settings
        if (values.carRhythm) { carRhythm(); return }
        if (!allowed) return
        if (!values.enabled) { restoreOriginal(); return }
        if (context == null) return
        val nowMillis = android.os.SystemClock.uptimeMillis()
        val rendererActive = activeRenderer.get() != 0L && playback != null
        val playbackState = if (rendererActive) playback?.invoke() else null
        val playing = playbackState?.second == true && phonePlayback.current() != false
        if (playing) idleSinceMillis = null
        else if (idleSinceMillis == null) idleSinceMillis = nowMillis
        val target = AmbientMusicActivityPolicy.target(
            enabled = values.enabled,
            music = values.music,
            brightness = values.brightness,
            rendererActive = rendererActive,
            playing = playing,
            idle = values.idle,
            idleMillis = idleSinceMillis?.let { nowMillis - it } ?: 0,
        )
        if (target == AmbientMusicTarget.RESTORE_OEM) { restoreOriginal(); return }
        lampSession.setEnabled(true)
        val energy = if (target == AmbientMusicTarget.FOLLOW_PLAYBACK) {
            val (head, headPlaying) = playbackState ?: return
            envelope?.playedWindow(head, headPlaying) ?: AmbientMusicEnvelope.Energy(0.0, 0.0)
        } else AmbientMusicEnvelope.Energy(0.0, 0.0)
        val rms = energy.rms
        val brightness = when (target) {
            AmbientMusicTarget.LOWEST -> 1
            AmbientMusicTarget.FOLLOW_PLAYBACK -> brightnessEnvelope.level(rms, nowMillis, values.brightness)
            else -> values.brightness
        }
        val area = if (target == AmbientMusicTarget.LOWEST) 3 else values.area
        val color = if (target == AmbientMusicTarget.FOLLOW_PLAYBACK && values.colorCycle)
            colors.color(values.colorMode, nowMillis, rms, energy.bassRms) else if (values.colorSource == AmbientColorSource.ALBUM) albumColor ?: 1
            else values.selectedColors.firstOrNull() ?: values.color
        lampSession.update(Triple(area, color, brightness))
    }

    private fun restoreOriginal() { lampSession.setEnabled(false) }

    // The car's own rhythm follows CarPlay music while it plays; DiPlay does not set the lamps meanwhile. On head
    // units whose audio service makes no spectrum of its own, the feed option sends the played music's 16 bands.
    private var lastBandsMillis = 0L
    private fun carRhythm() {}
}
