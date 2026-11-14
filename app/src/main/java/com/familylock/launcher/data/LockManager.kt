package com.familylock.launcher.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.familylock.launcher.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

enum class PinResult { OK, MASTER, WRONG }

/**
 * Single source of truth for lock state, shared by the launcher UI, the
 * accessibility service and the boot receiver (they all live in one process).
 */
object LockManager {
    private lateinit var appContext: Context
    private lateinit var store: SettingsStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Always-current snapshot of the saved settings. Safe to read from any thread. */
    lateinit var settings: StateFlow<LockSettings>
        private set

    /**
     * Until this elapsedRealtime, protected settings screens are NOT PIN-locked.
     * Set when the parent (already authenticated) opens a settings page from the app.
     * Kept in memory only, so it is lost if the process dies.
     */
    @Volatile
    var adminGraceUntil: Long = 0L

    /** Set when the master code was typed on the overlay; the launcher then shows "set new PIN". */
    @Volatile
    var masterResetPending: Boolean = false

    fun init(context: Context) {
        if (::store.isInitialized) return
        appContext = context.applicationContext
        store = SettingsStore(appContext)
        // The settings file is tiny; loading it synchronously once means nobody
        // ever sees a "not loaded yet" state (which would mean "not locked yet").
        val initial = runBlocking { store.flow.first() }
        settings = store.flow.stateIn(scope, SharingStarted.Eagerly, initial)
    }

    /** Run a write against the store in the background. */
    fun update(block: suspend SettingsStore.() -> Unit) {
        scope.launch { store.block() }
    }

    // ---- PIN ----------------------------------------------------------------

    fun checkPin(input: String): PinResult {
        val s = settings.value
        return when {
            input == Config.MASTER_CODE -> PinResult.MASTER
            s.hasPin && SettingsStore.hashPin(input, s.pinSalt) == s.pinHash -> PinResult.OK
            else -> PinResult.WRONG
        }
    }

    // ---- Protection state ---------------------------------------------------

    /** True when the lock is armed at all (setup finished and not switched off by the parent). */
    fun isProtectionActive(): Boolean {
        val s = settings.value
        return s.setupDone && s.protectionEnabled
    }

    fun startAdminGrace() {
        adminGraceUntil = SystemClock.elapsedRealtime() + Config.ADMIN_GRACE_MS
    }

    fun isAdminGraceActive(): Boolean = SystemClock.elapsedRealtime() < adminGraceUntil

    // ---- Temporary full access ---------------------------------------------
    //
    // The countdown uses SystemClock.elapsedRealtime(), which the user cannot
    // change (unlike the wall clock). elapsedRealtime restarts from zero on
    // reboot, so we also store the system boot counter: if it differs, the
    // phone was restarted and the remaining time cannot be verified -> locked.

    private fun bootCount(): Int =
        Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT, 0)

    fun startTempAccess(durationMs: Long) {
        val end = SystemClock.elapsedRealtime() + durationMs
        val boot = bootCount()
        update { setTempAccess(end, boot) }
    }

    fun stopTempAccess() = update { clearTempAccess() }

    /** Milliseconds of temporary access left, or 0 if none / expired / rebooted. */
    fun tempRemainingMs(): Long {
        val s = settings.value
        if (s.tempEndElapsed <= 0L || s.tempBootCount != bootCount()) return 0L
        return (s.tempEndElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    fun isTempAccessActive(): Boolean = tempRemainingMs() > 0L
}
