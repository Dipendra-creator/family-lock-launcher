package com.familylock.launcher

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import com.familylock.launcher.lock.AdminReceiver
import com.familylock.launcher.lock.LockAccessibilityService

/** Small helpers that report whether each protection piece is currently switched on. */
object Status {

    fun adminComponent(context: Context) = ComponentName(context, AdminReceiver::class.java)

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, LockAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    fun isDeviceAdminActive(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent(context))

    fun isDefaultHome(context: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, 0)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
}
