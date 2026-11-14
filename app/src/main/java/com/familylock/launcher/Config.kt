package com.familylock.launcher

/**
 * App-wide constants. Change values here and rebuild.
 */
object Config {
    /**
     * Master recovery code. Typing this on ANY PIN screen unlocks the app and
     * lets the parent set a new PIN. Change it here (digits only, 4-8 long).
     */
    const val MASTER_CODE = "993311"

    /** Allowed PIN length. */
    const val PIN_MIN_LENGTH = 4
    const val PIN_MAX_LENGTH = 8

    /**
     * After the parent opens a system settings page from the admin panel or
     * onboarding, protected screens stay unlocked for this long so they are
     * not asked for the PIN again straight away.
     */
    const val ADMIN_GRACE_MS = 2 * 60 * 1000L
}
