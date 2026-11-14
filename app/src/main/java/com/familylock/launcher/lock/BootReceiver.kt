package com.familylock.launcher.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familylock.launcher.data.LockManager

/**
 * Runs after every reboot. Temporary full access never survives a restart:
 * the countdown is based on elapsedRealtime, which resets on boot, so the
 * remaining time can no longer be verified -> re-lock immediately.
 * (LockManager also compares the boot counter, so the phone is already locked
 * before this broadcast arrives; this just cleans up the stored value.)
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        LockManager.init(context)
        LockManager.stopTempAccess()
    }
}
