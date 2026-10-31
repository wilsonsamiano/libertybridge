package com.wilson.libertybridge.airplay

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarPlayMediaButtonTest {
    fun theVoiceKeyOpensSiri() {
        // Recorded on DiLink 5.0: short press 304 (scan 290), long press 312 (scan 312).
        assertTrue(CarPlayMediaButton.opensSiri(304))
        assertTrue(CarPlayMediaButton.opensSiri(312))
        assertTrue(CarPlayMediaButton.opensSiri(KeyEvent.KEYCODE_VOICE_ASSIST))
        assertFalse(CarPlayMediaButton.opensSiri(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertNull(CarPlayMediaButton.forKeyCode(304))
    }

    @Test
    fun otherKeysAreLeftToTheSystem() {
        assertNull(CarPlayMediaButton.forKeyCode(KeyEvent.KEYCODE_VOLUME_UP))
        assertNull(CarPlayMediaButton.forKeyCode(KeyEvent.KEYCODE_MEDIA_STOP))
    }

    @Test
    fun indicesMatchTheAdvertisedMediaHidReport() {
        // Media report usages: 0 none, 1 play, 2 pause, 3 play/pause, 4 next, 5 previous.
        assertEquals(3, CarPlayMediaButton.PLAY_PAUSE)
        assertEquals(4, CarPlayMediaButton.NEXT)
        assertEquals(5, CarPlayMediaButton.PREVIOUS)
    }

    @Test
    fun theWindowClaimsTrackKeysOnlyWhileCarPlayIsRunning() {
        fun claim(keyCode: Int, sessionActive: Boolean = true, inCall: Boolean = false) =
            CarPlayMediaButton.forCarPlayWindow(keyCode, sessionActive, inCall)

        assertEquals(CarPlayMediaButton.NEXT, claim(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(CarPlayMediaButton.PREVIOUS, claim(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        // Without a session, or during a call, the wheel keeps the car's own action.
        assertNull(claim(KeyEvent.KEYCODE_MEDIA_NEXT, sessionActive = false))
        assertNull(claim(KeyEvent.KEYCODE_MEDIA_PREVIOUS, sessionActive = false))
        assertNull(claim(KeyEvent.KEYCODE_MEDIA_NEXT, inCall = true))
        assertNull(claim(KeyEvent.KEYCODE_MEDIA_PREVIOUS, inCall = true))
    }

    @Test
    fun theWindowLeavesPlayPauseAndUnrelatedKeysAlone() {
        // BYD acts on play/pause itself, and the wheel key service owns it for DiLink 3. Claiming it
        // in the window too would take it away from the car for every driver.
        for (keyCode in listOf(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK, 353, 331,
            KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_VOLUME_UP)) {
            assertNull("keyCode=$keyCode", CarPlayMediaButton.forCarPlayWindow(keyCode, true, false))
        }
    }
}
