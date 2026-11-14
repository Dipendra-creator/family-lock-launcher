package com.familylock.launcher.lock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Device Admin component. It enforces no policies; an app that is an active
 * device admin simply cannot be uninstalled until the admin is deactivated,
 * and the deactivation screen is PIN-protected by the accessibility service.
 */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Deactivating will allow Family Lock to be uninstalled."
}
