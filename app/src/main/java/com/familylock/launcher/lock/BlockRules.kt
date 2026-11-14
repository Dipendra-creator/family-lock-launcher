package com.familylock.launcher.lock

/**
 * All "what counts as a protected screen" knowledge lives here so it is easy
 * to extend for a new phone brand: add a package, a class-name fragment or a
 * window-title keyword to the lists below. All matching is case-insensitive
 * "contains". Titles are matched in the phone's UI language, so add translated
 * keywords if the phone is not set to English.
 */
object BlockRules {

    /** Packages that are never blocked (system chrome that must keep working). */
    val IGNORED_PACKAGES = setOf(
        "android",                      // system dialogs, app chooser
        "com.android.systemui",         // status bar, notification shade, power menu
        "com.android.intentresolver",   // share sheet (the chosen target is still checked)
    )

    /** Windows that are never blocked, by class-name fragment. */
    val IGNORED_CLASS_KEYWORDS = listOf(
        "RecentsActivity",   // recent-apps screen provided by the stock launcher
        "InCall",            // incoming / ongoing phone call screen
    )

    /**
     * Placeholder package name used for system windows that hide their content
     * (and therefore their package) from accessibility services; only their
     * window title can be matched.
     */
    const val HIDDEN_WINDOW_PACKAGE = "hidden.system.window"

    /** Exact packages treated as "Settings-like" (sensitive screens are searched for inside these). */
    val GUARDED_PACKAGES = setOf(
        HIDDEN_WINDOW_PACKAGE,
        "com.android.settings",
        "com.android.vending",                 // Play Store (uninstall button on the app page)
        "com.samsung.accessibility",
        "com.miui.securitycenter",
        "com.coloros.safecenter",
        "com.oplus.safecenter",
        "com.vivo.permissionmanager",
        "com.iqoo.secure",
        "com.huawei.systemmanager",
    )

    /** Any package whose name contains one of these is also treated as Settings-like. */
    val GUARDED_PACKAGE_KEYWORDS = listOf(
        "settings", "permissioncontroller", "packageinstaller", "securitycenter", "safecenter",
    )

    /** One protected screen: matched by activity class-name fragment OR window-title keyword. */
    data class ScreenRule(
        val name: String,
        val classKeywords: List<String> = emptyList(),
        val titleKeywords: List<String> = emptyList(),
    )

    val SENSITIVE_SCREENS = listOf(
        ScreenRule(
            name = "Accessibility settings",
            classKeywords = listOf("Accessibility"),
            titleKeywords = listOf("accessibility"),
        ),
        ScreenRule(
            name = "Device admin settings",
            classKeywords = listOf("DeviceAdmin"),
            titleKeywords = listOf("device admin", "device administrator", "phone administrator"),
        ),
        ScreenRule(
            name = "Developer options",
            classKeywords = listOf("DevelopmentSettings", "DevelopmentOptions"),
            titleKeywords = listOf("developer options", "developer option"),
        ),
        ScreenRule(
            name = "Default apps / home app",
            classKeywords = listOf("HomeSettings", "DefaultApp", "ManageDefaultApps", "DefaultHome", "RequestRole"),
            titleKeywords = listOf("default apps", "default app", "default home", "home app", "default launcher", "home screen app"),
        ),
        ScreenRule(
            name = "Date & time settings",
            classKeywords = listOf("DateTimeSettings", "DateAndTime"),
            titleKeywords = listOf("date & time", "date and time", "date &amp; time"),
        ),
    )

    /**
     * Brand-independent safety net: inside a Settings-like app, any screen that
     * mentions OUR app name together with one of these words is protected
     * (App Info page, uninstall dialog, deactivate-admin page, a11y toggle page).
     */
    val DANGER_WORDS = listOf(
        "force stop", "uninstall", "deactivate", "clear storage", "clear data",
        "clear cache", "storage & cache", "storage and cache", "remove app",
    )

    fun isIgnored(pkg: String, cls: String): Boolean =
        pkg in IGNORED_PACKAGES || IGNORED_CLASS_KEYWORDS.any { cls.contains(it, ignoreCase = true) }

    fun isGuardedPackage(pkg: String): Boolean =
        pkg in GUARDED_PACKAGES || GUARDED_PACKAGE_KEYWORDS.any { pkg.contains(it, ignoreCase = true) }

    /**
     * @param title    window title / event text of the screen
     * @param appLabel our own app name ("Family Lock")
     * @param content  lazily returns all visible text on screen (only read if needed)
     * @return a human-readable reason if this screen must be PIN-protected, else null
     */
    fun sensitiveReason(
        pkg: String,
        cls: String,
        title: String,
        appLabel: String,
        content: () -> String,
    ): String? {
        if (!isGuardedPackage(pkg)) return null

        for (rule in SENSITIVE_SCREENS) {
            if (rule.classKeywords.any { cls.contains(it, ignoreCase = true) } ||
                rule.titleKeywords.any { title.contains(it, ignoreCase = true) }
            ) return rule.name
        }

        // A Settings page titled with our own name (e.g. the accessibility toggle page).
        if (title.contains(appLabel, ignoreCase = true)) return "$appLabel settings"

        val text = content()
        if (text.contains(appLabel, ignoreCase = true) &&
            DANGER_WORDS.any { text.contains(it, ignoreCase = true) }
        ) return "$appLabel app info"

        return null
    }
}
