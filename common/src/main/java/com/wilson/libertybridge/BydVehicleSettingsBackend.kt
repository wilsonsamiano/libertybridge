package com.wilson.libertybridge

import android.content.Context
import com.wilson.libertybridge.hud.BydAdbAccess
import com.wilson.libertybridge.hud.BydVehicleCapabilityProbe
import com.wilson.libertybridge.hud.BydVehicleProbeOutcome

/** Test seam for the blocking ADB work launched by the host settings page. */
internal interface BydVehicleSettingsBackend {
    fun check(context: Context, mayAsk: Boolean): BydAdbAccess.Status
    fun checkState(context: Context, mayAsk: Boolean): BydAdbAccess.State
    fun probe(context: Context, persist: Boolean): BydVehicleProbeOutcome
    fun execute(name: String, block: () -> Unit)
}

internal object AndroidBydVehicleSettingsBackend : BydVehicleSettingsBackend {
    override fun check(context: Context, mayAsk: Boolean) = BydAdbAccess.check(context, mayAsk)

    override fun checkState(context: Context, mayAsk: Boolean) = BydAdbAccess.checkState(context, mayAsk)

    override fun probe(context: Context, persist: Boolean) =
        BydVehicleCapabilityProbe.probe(context, persist)

    override fun execute(name: String, block: () -> Unit) {
        Thread(block, name).start()
    }
}

/** Replaced only by Robolectric tests; production always uses [AndroidBydVehicleSettingsBackend]. */
internal object BydVehicleSettingsBackendProvider {
    @Volatile var current: BydVehicleSettingsBackend = AndroidBydVehicleSettingsBackend

    fun reset() {
        current = AndroidBydVehicleSettingsBackend
    }
}
