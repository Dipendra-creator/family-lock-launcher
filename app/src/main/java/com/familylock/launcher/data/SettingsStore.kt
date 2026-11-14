package com.familylock.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom

/** Everything the app persists, as one immutable snapshot. */
data class LockSettings(
    /** Salted SHA-256 of the admin PIN ("" = no PIN set yet). */
    val pinHash: String = "",
    val pinSalt: String = "",
    /** Package names the child may use. */
    val allowedApps: Set<String> = emptySet(),
    /** True once onboarding has been finished. */
    val setupDone: Boolean = false,
    /** Parent's master switch. False = launcher shows everything, nothing is blocked. */
    val protectionEnabled: Boolean = true,
    /** Temporary full access ends when SystemClock.elapsedRealtime() reaches this value. */
    val tempEndElapsed: Long = 0L,
    /** Boot count when temporary access was started; a different value means the phone rebooted. */
    val tempBootCount: Int = -1,
    /** Name of the onboarding screen the parent is on (survives the activity being restarted). */
    val onboardingStep: String = "",
) {
    val hasPin: Boolean get() = pinHash.isNotEmpty()
}

private val Context.dataStore by preferencesDataStore(name = "family_lock_settings")

/** Thin wrapper around Preferences DataStore. */
class SettingsStore(private val context: Context) {

    private object Keys {
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val ALLOWED = stringSetPreferencesKey("allowed_apps")
        val SETUP_DONE = booleanPreferencesKey("setup_done")
        val PROTECTION = booleanPreferencesKey("protection_enabled")
        val TEMP_END = longPreferencesKey("temp_end_elapsed")
        val TEMP_BOOT = intPreferencesKey("temp_boot_count")
        val OB_STEP = stringPreferencesKey("onboarding_step")
    }

    val flow: Flow<LockSettings> = context.dataStore.data.map { p ->
        LockSettings(
            pinHash = p[Keys.PIN_HASH] ?: "",
            pinSalt = p[Keys.PIN_SALT] ?: "",
            allowedApps = p[Keys.ALLOWED] ?: emptySet(),
            setupDone = p[Keys.SETUP_DONE] ?: false,
            protectionEnabled = p[Keys.PROTECTION] ?: true,
            tempEndElapsed = p[Keys.TEMP_END] ?: 0L,
            tempBootCount = p[Keys.TEMP_BOOT] ?: -1,
            onboardingStep = p[Keys.OB_STEP] ?: "",
        )
    }

    suspend fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        context.dataStore.edit {
            it[Keys.PIN_SALT] = salt
            it[Keys.PIN_HASH] = hashPin(pin, salt)
        }
    }

    suspend fun setAllowedApps(apps: Set<String>) {
        context.dataStore.edit { it[Keys.ALLOWED] = apps }
    }

    suspend fun setSetupDone(done: Boolean) {
        context.dataStore.edit { it[Keys.SETUP_DONE] = done }
    }

    suspend fun setOnboardingStep(step: String) {
        context.dataStore.edit { it[Keys.OB_STEP] = step }
    }

    suspend fun setProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PROTECTION] = enabled }
    }

    suspend fun setTempAccess(endElapsed: Long, bootCount: Int) {
        context.dataStore.edit {
            it[Keys.TEMP_END] = endElapsed
            it[Keys.TEMP_BOOT] = bootCount
        }
    }

    suspend fun clearTempAccess() {
        context.dataStore.edit {
            it[Keys.TEMP_END] = 0L
            it[Keys.TEMP_BOOT] = -1
        }
    }

    companion object {
        fun hashPin(pin: String, salt: String): String =
            MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()).toHex()

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    }
}
