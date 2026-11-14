package com.familylock.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * Switching between Family Lock and the phone's normal ("main") home screen.
 *
 * Family Lock is offered to Android as a home app through the `.HomeAlias`
 * activity-alias in the manifest. Disabling that alias removes Family Lock from
 * the list of home apps, so Android falls back to the phone's own launcher -
 * that is how "permanent return to main screen" works. Re-enabling it lets the
 * parent choose Family Lock as the home app again.
 */
object HomeMode {

    private fun alias(context: Context) = ComponentName(context.packageName, "com.familylock.launcher.HomeAlias")

    /** False after "permanent return to main screen": Family Lock is no longer a home app. */
    fun isHomeEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(alias(context)) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    fun setHomeEnabled(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            alias(context),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    /** The phone's own launcher (any home app that is not Family Lock), or null if there is none. */
    fun mainLauncher(context: Context): ComponentName? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .asSequence()
            .map { it.activityInfo }
            // Settings registers a placeholder "FallbackHome" that is not a real launcher.
            .filter { it.packageName != context.packageName && it.packageName != "com.android.settings" }
            // Prefer the launcher that came with the phone.
            .sortedByDescending { (it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
            .map { ComponentName(it.packageName, it.name) }
            .firstOrNull()
    }

    /** Shows the phone's normal home screen. @return false if the phone has no other launcher. */
    fun openMainScreen(context: Context): Boolean {
        val launcher = mainLauncher(context) ?: return false
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setComponent(launcher)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
