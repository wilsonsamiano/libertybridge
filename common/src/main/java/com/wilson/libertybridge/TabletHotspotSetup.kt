package com.wilson.libertybridge

import android.content.Context
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.util.Log
import com.wilson.libertybridge.adb.AdbKeys
import com.wilson.libertybridge.adb.LocalAdb
import com.wilson.libertybridge.network.TabletHotspotSettings
import com.wilson.libertybridge.orchestration.ManualHotspotValidation

/** Each grant is requested explicitly from settings; startup never calls this authorization path. */
internal object TabletHotspotSetup {
    enum class Permission(val appOp: String) {
        HOTSPOT("WRITE_SETTINGS"), BOOT_LAUNCH("SYSTEM_ALERT_WINDOW");

        fun granted(context: Context): Boolean = when (this) {
            HOTSPOT -> Settings.System.canWrite(context)
            BOOT_LAUNCH -> Settings.canDrawOverlays(context)
        }
    }

    fun check(context: Context, adb: LocalAdb = LocalAdb(AdbKeys.load(context))): LocalAdb.Access = adb.use {
        it.connect(mayAsk = false)
    }

    fun grant(context: Context, permissions: List<Permission>, adb: LocalAdb = LocalAdb(AdbKeys.load(context))): LocalAdb.Access =
        adb.use {
            val access = it.connect(mayAsk = true)
            Log.i("DiPlay-ADB", "switch connection: $access")
            if (access == LocalAdb.Access.READY) {
                for (permission in permissions) {
                    if (!permission.granted(context)) {
                        Log.i("DiPlay-ADB", "request permission: ${permission.appOp}")
                        it.shell("appops set ${context.packageName} ${permission.appOp} allow")
                    }
                    val granted = permission.granted(context)
                    Log.i("DiPlay-ADB", "permission ${permission.appOp}: granted=$granted")
                    if (!granted) break
                }
            }
            access
        }

    fun shouldStartOnLaunch(context: Context, hasSession: Boolean): Boolean =
        !hasSession && TabletHotspotSettings.shouldEnable(context,
            AirPlayPersistence.loadWirelessEnabled(context), AirPlayPersistence.loadWirelessHotspotMode(context)) &&
            ManualHotspotValidation.error(AirPlayPersistence.loadManualHotspotSsid(context),
                AirPlayPersistence.loadManualHotspotPassphrase(context)) == null
}
