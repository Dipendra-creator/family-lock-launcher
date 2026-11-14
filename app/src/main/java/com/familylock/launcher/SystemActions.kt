package com.familylock.launcher

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.familylock.launcher.data.LockManager

/**
 * Opens the system screens the parent needs during setup / in the admin panel.
 * Every call starts the short "admin grace" window so the accessibility
 * service does not immediately PIN-lock the page the parent just asked for.
 */
object SystemActions {

    private fun Activity.open(intent: Intent, fallback: Intent? = null) {
        LockManager.startAdminGrace()
        runCatching { startActivity(intent) }.onFailure {
            if (fallback != null) runCatching { startActivity(fallback) }
        }
    }

    fun openAccessibilitySettings(activity: Activity) =
        activity.open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    /** Our own App Info page (needed for "Allow restricted settings" on Android 13+). */
    fun openAppInfo(activity: Activity) = activity.open(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))
    )

    fun requestDeviceAdmin(activity: Activity) = activity.open(
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Status.adminComponent(activity))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Stops Family Lock from being uninstalled without the parent PIN."
            )
    )

    fun deactivateDeviceAdmin(activity: Activity) {
        activity.getSystemService(DevicePolicyManager::class.java)
            .removeActiveAdmin(Status.adminComponent(activity))
    }

    /** Ask to become the default home app (system dialog on Android 10+, settings page before). */
    fun requestDefaultHome(activity: Activity) {
        LockManager.startAdminGrace()
        val homeSettings = Intent(Settings.ACTION_HOME_SETTINGS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = activity.getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                @Suppress("DEPRECATION")
                runCatching {
                    activity.startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 1001)
                }.onFailure { activity.open(homeSettings) }
                return
            }
        }
        activity.open(homeSettings)
    }

    fun requestBatteryExemption(activity: Activity) = activity.open(
        @Suppress("BatteryLife")
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${activity.packageName}")),
        fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )

    fun openDeveloperOptions(activity: Activity) = activity.open(
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        fallback = Intent(Settings.ACTION_SETTINGS),
    )
}
