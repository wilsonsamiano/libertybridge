package com.wilson.libertybridge.hud

/**
 * A voice key can arrive as two keys about half a second apart: the CarPlay
 * pair and the generic pair. Each opens Siri, and a second request ends the
 * Siri session the first one began, so only the first of the two within
 * [WINDOW_MILLIS] is sent. No Android types, so it is unit-tested.
 */
class VoiceKeyPair {
    private var carPlayKeyMillis: Long? = null
    private var genericKeyMillis: Long? = null

    fun allowCarPlayKey(nowMillis: Long): Boolean = take(nowMillis, genericKeyMillis) { carPlayKeyMillis = it }
    fun allowGenericKey(nowMillis: Long): Boolean = take(nowMillis, carPlayKeyMillis) { genericKeyMillis = it }

    private fun take(now: Long, other: Long?, record: (Long) -> Unit): Boolean {
        if (other != null && now - other in 0 until WINDOW_MILLIS) return false
        record(now)
        return true
    }

    companion object {
        const val WINDOW_MILLIS = 1_500L

        /** Shared by the key service and the CarPlay window, which run in the same process. */
        val shared = VoiceKeyPair()
    }
}
