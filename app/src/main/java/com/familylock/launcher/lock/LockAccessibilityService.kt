package com.familylock.launcher.lock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import com.familylock.launcher.MainActivity
import com.familylock.launcher.R
import com.familylock.launcher.data.LockManager
import com.familylock.launcher.data.PinResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The enforcement engine.
 *
 * Every time a new window comes to the front we decide whether it may be used:
 *  - protected settings screens (see [BlockRules])  -> PIN overlay, always
 *  - an app that is not on the allowed list          -> PIN overlay
 *  - everything else                                 -> left alone
 *
 * A correct PIN unlocks that one app until the user leaves it. A wrong PIN,
 * Cancel or Back sends the user to the launcher home.
 *
 * Once enabled in Settings, Android itself restarts this service after a
 * reboot or crash - no foreground service is needed.
 */
class LockAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var overlay: PinOverlay
    private lateinit var appLabel: String

    /** Package the overlay is currently covering. */
    private var overlayPkg: String? = null

    /** Package unlocked with the PIN; stays unlocked until another app comes to the front. */
    private var unlockedPkg: String? = null

    /** Last real foreground window we saw (used to re-check when settings change or the timer ends). */
    private var lastPkg = ""
    private var lastClass = ""
    private var lastTitle = ""

    private var lastContentCheck = 0L
    private var imePackages: Set<String> = emptySet()

    /** package -> "has a launcher or home icon" cache. */
    private val launchableCache = HashMap<String, Boolean>()

    /**
     * Runs when timed full access runs out. If the phone's own launcher is in
     * front (that is where full access leaves the user), simply bring Family Lock
     * back instead of showing a PIN screen over it; otherwise re-check the app.
     */
    private val recheck = Runnable {
        if (LockManager.isProtectionActive() && lastPkg != packageName && isHomeApp(lastPkg)) goHome()
        else reevaluate()
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                // Locking the screen ends any PIN unlock.
                Intent.ACTION_SCREEN_OFF -> unlockedPkg = null
                // Apps were installed/removed: forget what we cached.
                else -> launchableCache.clear()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        LockManager.init(this)
        appLabel = getString(R.string.app_name)
        overlay = PinOverlay(this, ::onPinEntered, ::goHome)
        instance = this

        registerReceiver(systemReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        registerReceiver(systemReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })

        // Whenever settings change (allowed list, protection switch, temporary
        // access started/stopped) re-check the app that is currently in front.
        scope.launch {
            LockManager.settings.collect {
                scheduleTempExpiryCheck()
                reevaluate()
            }
        }
    }

    override fun onDestroy() {
        instance = null
        runCatching { unregisterReceiver(systemReceiver) }
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        if (::overlay.isInitialized) overlay.hide()
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    /**
     * Called by the launcher every time it comes to the front. Android does not
     * always send a window event for that (e.g. when a floating dialog such as the
     * uninstall prompt was shown on top of the launcher), so the launcher reports
     * it directly: nothing needs locking while the home screen is showing.
     */
    fun onLauncherResumed() {
        if (!::overlay.isInitialized) return
        hideOverlay()
        unlockedPkg = null
        lastPkg = packageName
        lastClass = ""
        lastTitle = ""
    }

    // ---- Event handling ------------------------------------------------------

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            // Some system screens (e.g. the "Default home app" picker) never send a
            // window-state event, so also look at the active window when windows change.
            scheduleVerify()
            return
        }
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val cls = event.className?.toString().orEmpty()
                val title = event.text.joinToString(" ")
                if (DEBUG) Log.d(TAG, "raw event pkg=$pkg cls=$cls text='$title'")
                evaluate(pkg, cls, title)
                scheduleVerify()
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Settings apps often swap pages inside one window. Re-check them,
                // at most a few times per second, using the fresh window title.
                if (pkg != lastPkg || !BlockRules.isGuardedPackage(pkg)) return
                if (overlay.isShowing || unlockedPkg == pkg) return
                val now = SystemClock.elapsedRealtime()
                if (now - lastContentCheck < 300) return
                lastContentCheck = now
                evaluate(pkg, lastClass, lastTitle)
            }
        }
    }

    private val verify = Runnable { verifyActiveWindow() }

    private fun scheduleVerify() {
        handler.removeCallbacks(verify)
        handler.postDelayed(verify, 300)
    }

    /**
     * Safety net for screens that do not announce themselves: read the window that
     * is really in front and check it against the protected-screen rules. The
     * activity class is unknown here, so only those rules are applied (not the
     * generic "app is locked" rule, which needs the class to spare e.g. Recents).
     */
    private fun verifyActiveWindow() {
        val pkg = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        if (pkg == null) {
            // Some system windows (the permission controller's "Default home app" /
            // "Default apps" pages) hide their content from accessibility services.
            // Only the window title is visible, so match on that alone.
            val hiddenTitle = runCatching {
                windows.firstOrNull { it.isActive && it.type == AccessibilityWindowInfo.TYPE_APPLICATION }?.title?.toString()
            }.getOrNull()
            if (!hiddenTitle.isNullOrBlank()) evaluate(BlockRules.HIDDEN_WINDOW_PACKAGE, "", hiddenTitle, sensitiveOnly = true)
            return
        }
        if (pkg == packageName) return
        if (pkg == lastPkg) evaluate(pkg, lastClass, lastTitle)
        else evaluate(pkg, "", "", sensitiveOnly = true)
    }

    /** Re-run the decision for the window we last saw in front. */
    private fun reevaluate() {
        if (lastPkg.isNotEmpty() && lastPkg != packageName) evaluate(lastPkg, lastClass, lastTitle)
        else if (!LockManager.isProtectionActive()) hideOverlay()
    }

    private fun evaluate(pkg: String, cls: String, eventTitle: String, sensitiveOnly: Boolean = false) {
        if (!LockManager.isProtectionActive()) {
            hideOverlay()
            return
        }

        if (pkg == packageName) {
            // Our own launcher came to the front: nothing to block. (Events from the
            // overlay window itself also carry our package name - ignore those.)
            if (cls == MainActivity::class.java.name) {
                hideOverlay()
                unlockedPkg = null
                lastPkg = pkg
            }
            return
        }

        if (BlockRules.isIgnored(pkg, cls) || isKeyboard(pkg)) return

        // Combine the event text with the real window title (brands differ in which one is set).
        val root = runCatching { rootInActiveWindow }.getOrNull()?.takeIf { it.packageName?.toString() == pkg }
        val windowTitle = runCatching { root?.window?.title?.toString() }.getOrNull().orEmpty()
        var title = "$eventTitle $windowTitle".trim()
        // No title at all: fall back to the page heading (the first text on screen).
        if (title.isEmpty() && root != null) title = firstTexts(root, 2)

        lastPkg = pkg
        lastClass = cls
        lastTitle = eventTitle

        // Leaving the unlocked app ends the unlock.
        if (unlockedPkg != null && unlockedPkg != pkg) unlockedPkg = null

        val reason = blockReason(pkg, cls, title, sensitiveOnly)
        if (DEBUG) Log.d(TAG, "window pkg=$pkg cls=$cls title='$title' -> ${reason ?: "allowed"}")

        if (reason == null || unlockedPkg == pkg) hideOverlay() else showOverlay(pkg, reason)
    }

    /** @return why this window must be locked, or null if it may be used. */
    private fun blockReason(pkg: String, cls: String, title: String, sensitiveOnly: Boolean): String? {
        // 1. Protected settings screens: locked even when Settings is allowed and
        //    even during temporary full access (otherwise a child could use that
        //    hour to remove the app). Only the parent's grace window skips it.
        val sensitive = BlockRules.sensitiveReason(pkg, cls, title, appLabel) { screenText() }
        if (sensitive != null) return if (LockManager.isAdminGraceActive()) null else sensitive
        if (sensitiveOnly) return null

        // 2. Temporary full access: every app is open.
        if (LockManager.isTempAccessActive()) return null

        // 3. Allowed apps.
        if (pkg in LockManager.settings.value.allowedApps) return null

        // 4. Packages without an app icon are system helpers (sign-in sheets, file
        //    pickers, permission dialogs...) that allowed apps rely on - let them be.
        if (!isLaunchable(pkg)) return null

        return "This app is locked"
    }

    // ---- Overlay -------------------------------------------------------------

    private fun showOverlay(pkg: String, reason: String) {
        overlayPkg = pkg
        overlay.show(reason)
    }

    private fun hideOverlay() {
        overlayPkg = null
        overlay.hide()
    }

    private fun onPinEntered(pin: String) {
        when (LockManager.checkPin(pin)) {
            PinResult.OK -> {
                unlockedPkg = overlayPkg
                hideOverlay()
            }

            PinResult.MASTER -> {
                // Master code: go to the launcher, which asks for a new PIN.
                LockManager.masterResetPending = true
                goHome()
            }

            PinResult.WRONG -> goHome()
        }
    }

    /** Send the user to the launcher. The overlay stays up until the launcher is in front (no peeking). */
    private fun goHome() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        // Safety net in case the "launcher in front" event is missed.
        handler.postDelayed({ if (lastPkg == packageName) hideOverlay() }, 1500)
    }

    // ---- Helpers -------------------------------------------------------------

    /** When temporary access runs out, re-check the foreground app even if nothing else happens. */
    private fun scheduleTempExpiryCheck() {
        handler.removeCallbacks(recheck)
        val remaining = LockManager.tempRemainingMs()
        if (remaining > 0) handler.postDelayed(recheck, remaining + 250)
    }

    private fun isKeyboard(pkg: String): Boolean {
        if (pkg in imePackages) return true
        // Refresh the list lazily (keyboards can be installed later).
        imePackages = runCatching {
            getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }.toSet()
        }.getOrDefault(emptySet())
        return pkg in imePackages
    }

    private fun isHomeApp(pkg: String): Boolean {
        if (pkg.isEmpty()) return false
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg)
        return packageManager.queryIntentActivities(home, 0).isNotEmpty()
    }

    /** True if the package shows up as an app (launcher icon) or is another home app. */
    private fun isLaunchable(pkg: String): Boolean = launchableCache.getOrPut(pkg) {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg)
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg)
        packageManager.queryIntentActivities(launcher, 0).isNotEmpty() ||
            packageManager.queryIntentActivities(home, 0).isNotEmpty()
    }

    /** The first [count] pieces of text in a window, top-down (normally the page heading). */
    private fun firstTexts(root: AccessibilityNodeInfo, count: Int): String {
        val found = ArrayList<String>()
        var visited = 0
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null || found.size >= count || visited++ > 200) return
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { found.add(it) }
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        runCatching { walk(root) }
        return found.joinToString(" ")
    }

    /** All visible text of the active window, capped so huge screens stay cheap. */
    private fun screenText(): String {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return ""
        val sb = StringBuilder()
        var visited = 0
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null || visited++ > 400) return
            node.text?.let { sb.append(it).append('\n') }
            node.contentDescription?.let { sb.append(it).append('\n') }
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        runCatching { walk(root) }
        return sb.toString()
    }

    companion object {
        private const val TAG = "FamilyLock"

        /** The running service, if enabled (same process as the launcher). */
        @Volatile
        var instance: LockAccessibilityService? = null
            private set

        /** Set to true to log every window decision (adb logcat -s FamilyLock) when adding brand rules. */
        const val DEBUG = false
    }
}
