package com.wilson.libertybridge.setup

import android.content.Context

/** Which features the setup guide offers on a tablet, and how well each is proven. */
object SetupGuide {
    const val STEP_TABLET = 0
    const val STEP_CONNECTION = 1
    const val STEP_IPHONE = 2
    const val STEP_FEATURES = 3
    const val STEP_DONE = 4
    const val STEP_COUNT = 5

    enum class Feature { AUTO_CONNECT, LOCATION }

    /** TESTED: confirmed on at least one tablet. HIDDEN: not offered in the guide. */
    enum class Status { TESTED, EXPERIMENTAL, HIDDEN }

    data class Entry(val feature: Feature, val status: Status, val needsAdb: Boolean)

    fun features(): List<Entry> {
        fun entry(feature: Feature, status: Status, needsAdb: Boolean = false) = Entry(feature, status, needsAdb)
        return listOf(
            entry(Feature.AUTO_CONNECT, Status.TESTED),
            entry(Feature.LOCATION, Status.TESTED),
        ).filter { it.status != Status.HIDDEN }
    }

    /**
     * Applies the first-setup defaults when the user confirms the tablet on a first setup only.
     * Someone who re-runs the guide, or set LibertyBridge up before the guide existed, keeps
     * the routing left on automatic.
     */
    fun applyFirstSetupDefaults(context: Context, phoneChosen: Boolean): Boolean =
        shouldOpenOnLaunch(seen(context), phoneChosen)

    private fun prefs(context: Context) = context.getSharedPreferences("diplay", Context.MODE_PRIVATE)

    fun seen(context: Context): Boolean = prefs(context).getBoolean("setup_guide_seen", false)

    fun markSeen(context: Context) {
        prefs(context).edit().putBoolean("setup_guide_seen", true).apply()
    }

    /** Only a first launch opens the guide by itself; anyone with a saved iPhone has already set up. */
    fun shouldOpenOnLaunch(seen: Boolean, phoneChosen: Boolean): Boolean = !seen && !phoneChosen
}
