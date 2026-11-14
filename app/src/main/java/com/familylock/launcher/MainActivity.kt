package com.familylock.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylock.launcher.data.LockManager
import com.familylock.launcher.data.PinResult
import com.familylock.launcher.lock.LockAccessibilityService
import com.familylock.launcher.ui.AdminActions
import com.familylock.launcher.ui.AdminPanel
import com.familylock.launcher.ui.AppPicker
import com.familylock.launcher.ui.FamilyLockTheme
import com.familylock.launcher.ui.HomeScreen
import com.familylock.launcher.ui.OnboardingStep
import com.familylock.launcher.ui.BwButton
import com.familylock.launcher.ui.Eyebrow
import com.familylock.launcher.ui.PatternBackground
import com.familylock.launcher.ui.PinPad
import com.familylock.launcher.ui.ProtectionStatus
import com.familylock.launcher.ui.SetPinFlow
import com.familylock.launcher.ui.StepAction
import com.familylock.launcher.ui.TempAccessScreen
import kotlinx.coroutines.delay

/** Every screen the launcher can show. Navigation is just a state variable. */
enum class Screen {
    // First-launch setup, in order
    OB_PIN, OB_APPS, OB_ACCESSIBILITY, OB_ADMIN, OB_HOME, OB_FINISH,

    // Normal use
    HOME, PIN_GATE,

    // Behind the PIN
    ADMIN, ADMIN_APPS, ADMIN_CHANGE_PIN, ADMIN_TEMP;

    val isAdmin get() = this == ADMIN || this == ADMIN_APPS || this == ADMIN_CHANGE_PIN || this == ADMIN_TEMP
    val isOnboarding get() = name.startsWith("OB_")
}

/**
 * The one activity of the app. It is the HOME launcher, and also hosts the
 * onboarding flow, the PIN screen and the parent panel.
 */
class MainActivity : ComponentActivity() {

    private var screenState by mutableStateOf(Screen.HOME)

    /**
     * Current screen. Onboarding steps are also saved to disk, because Android
     * restarts the launcher when it becomes the default home app (and may kill
     * it while the parent is in Settings) - setup then resumes where it was.
     */
    private var screen: Screen
        get() = screenState
        set(value) {
            screenState = value
            if (value.isOnboarding) LockManager.update { setOnboardingStep(value.name) }
        }

    /** Bumped on every resume so the UI re-reads system state (a11y on? default home? app list?). */
    private var refresh by mutableIntStateOf(0)

    /** True once the PIN was entered on the "accessibility is off" warning (until we leave the launcher). */
    private var a11yWarningCleared by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        LockManager.init(this)

        val s = LockManager.settings.value
        screen = when {
            !s.hasPin -> Screen.OB_PIN
            !s.setupDone -> Screen.entries.firstOrNull { it.isOnboarding && it.name == s.onboardingStep }
                ?: Screen.OB_APPS
            else -> Screen.HOME
        }

        setContent {
            FamilyLockTheme {
                // The pattern fills the whole display; content stays inside the system bars.
                PatternBackground {
                    Surface(color = Color.Transparent, contentColor = Color.White) {
                        Box(Modifier.fillMaxSize().safeDrawingPadding()) { Root() }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Coming back to the launcher: the parent panel stays open only during the short
        // grace window (e.g. returning from a settings page the parent opened from it).
        if (screen.isAdmin && !LockManager.isAdminGraceActive()) screen = Screen.HOME
    }

    override fun onResume() {
        super.onResume()
        refresh++
        consumeMasterReset()
        // The home screen is in front: make sure no lock screen is left on top of it.
        LockAccessibilityService.instance?.onLauncherResumed()
    }

    override fun onStop() {
        super.onStop()
        a11yWarningCleared = false
    }

    /** Home button pressed while the launcher is already open: leave the parent panel. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!screen.isOnboarding) screen = Screen.HOME
        consumeMasterReset()
    }

    /** The master code was typed on the lock overlay: go straight to "set a new PIN". */
    private fun consumeMasterReset() {
        if (LockManager.masterResetPending) {
            LockManager.masterResetPending = false
            screen = Screen.ADMIN_CHANGE_PIN
        }
    }

    // ---- UI ------------------------------------------------------------------

    @Composable
    private fun Root() {
        val settings by LockManager.settings.collectAsState()
        val tick = refresh

        val status = remember(tick, screen) {
            ProtectionStatus(
                accessibility = Status.isAccessibilityEnabled(this),
                deviceAdmin = Status.isDeviceAdminActive(this),
                defaultHome = Status.isDefaultHome(this),
                batteryExempt = Status.isIgnoringBatteryOptimizations(this),
            )
        }

        // All launchable apps; reloaded whenever the launcher resumes (apps may have been installed).
        val allApps by produceState<List<AppEntry>?>(null, tick) { value = Apps.loadLaunchable(this@MainActivity) }

        // Temporary-access state for the home screen (re-checked every second so it re-locks on time).
        val tempActive by produceState(LockManager.isTempAccessActive(), settings) {
            while (true) {
                value = LockManager.isTempAccessActive()
                delay(1000)
            }
        }

        fun toggleApp(pkg: String, allowed: Boolean) {
            val current = LockManager.settings.value.allowedApps
            val next = if (allowed) current + pkg else current - pkg
            LockManager.update { setAllowedApps(next) }
        }

        val totalSteps = 6

        when (screen) {
            // ---------------- Onboarding ----------------
            Screen.OB_PIN -> SetPinFlow(title = "Set a parent PIN", eyebrow = "Step 01 / 06") { pin ->
                LockManager.update { setPin(pin) }
                screen = Screen.OB_APPS
            }

            Screen.OB_APPS -> OnboardingStep(
                step = 2, totalSteps = totalSteps,
                title = "Choose allowed apps",
                body = "Only ticked apps appear on the home screen. Everything else is hidden and asks for the PIN. " +
                    "Settings is hidden unless you tick it.",
                onNext = { screen = Screen.OB_ACCESSIBILITY },
            ) {
                AppPicker(allApps, settings.allowedApps, ::toggleApp)
            }

            Screen.OB_ACCESSIBILITY -> {
                BackHandler { screen = Screen.OB_APPS }
                OnboardingStep(
                    step = 3, totalSteps = totalSteps,
                    title = "Turn on the Accessibility service",
                    body = "This is what blocks locked apps and protected settings pages.\n\n" +
                        "1. Tap the button below.\n" +
                        "2. Find \"Family Lock\" (under Installed / Downloaded apps).\n" +
                        "3. Switch it on and confirm.\n\n" +
                        "If Android says \"Restricted setting\": open App info below, tap the ⋮ menu in the top " +
                        "right, choose \"Allow restricted settings\", then try again.",
                    actions = listOf(
                        StepAction("Open Accessibility settings") { SystemActions.openAccessibilitySettings(this) },
                        StepAction("Open App info") { SystemActions.openAppInfo(this) },
                    ),
                    done = status.accessibility,
                    doneText = "Accessibility service is on",
                    pendingText = "Accessibility service is off",
                    onBack = { screen = Screen.OB_APPS },
                    onNext = { screen = Screen.OB_ADMIN },
                )
            }

            Screen.OB_ADMIN -> {
                BackHandler { screen = Screen.OB_ACCESSIBILITY }
                OnboardingStep(
                    step = 4, totalSteps = totalSteps,
                    title = "Activate Device Admin",
                    body = "While Family Lock is a device admin it cannot be uninstalled. " +
                        "You can deactivate it later from the parent panel.",
                    actions = listOf(StepAction("Activate Device Admin") { SystemActions.requestDeviceAdmin(this) }),
                    done = status.deviceAdmin,
                    doneText = "Device admin is active",
                    pendingText = "Device admin is not active",
                    onBack = { screen = Screen.OB_ACCESSIBILITY },
                    onNext = { screen = Screen.OB_HOME },
                )
            }

            Screen.OB_HOME -> {
                BackHandler { screen = Screen.OB_ADMIN }
                OnboardingStep(
                    step = 5, totalSteps = totalSteps,
                    title = "Make Family Lock the home app",
                    body = "Choose \"Family Lock\" as the default home app so the Home button always comes here.",
                    actions = listOf(StepAction("Set as default home") { SystemActions.requestDefaultHome(this) }),
                    done = status.defaultHome,
                    doneText = "Family Lock is the default home app",
                    pendingText = "Another home app is still the default",
                    onBack = { screen = Screen.OB_ADMIN },
                    onNext = { screen = Screen.OB_FINISH },
                )
            }

            Screen.OB_FINISH -> {
                BackHandler { screen = Screen.OB_HOME }
                OnboardingStep(
                    step = 6, totalSteps = totalSteps,
                    title = "Keep it running",
                    body = "• Battery: exclude Family Lock from battery optimisation so the phone does not stop it.\n\n" +
                        "• Xiaomi / Vivo / Oppo / Realme: also open App info and enable Autostart (Auto-launch) " +
                        "and set battery use to \"No restrictions\".\n\n" +
                        "• \"Display over other apps\" is NOT needed: the lock screen is drawn by the " +
                        "accessibility service.\n\n" +
                        "• When you are finished, turn off USB debugging in Developer options.\n\n" +
                        "To open the parent panel later, long-press the clock on the home screen.",
                    actions = listOf(
                        StepAction("Disable battery optimisation") { SystemActions.requestBatteryExemption(this) },
                        StepAction("Open App info (autostart)") { SystemActions.openAppInfo(this) },
                        StepAction("Open Developer options") { SystemActions.openDeveloperOptions(this) },
                    ),
                    done = status.batteryExempt,
                    doneText = "Battery optimisation is off for Family Lock",
                    pendingText = "Battery optimisation is still on",
                    nextLabel = "Finish",
                    canSkip = true,
                    onBack = { screen = Screen.OB_HOME },
                    onNext = {
                        LockManager.update { setSetupDone(true) }
                        screen = Screen.HOME
                    },
                )
            }

            // ---------------- Normal use ----------------
            Screen.HOME -> {
                BackHandler { /* Home is the bottom of the stack: Back does nothing. */ }
                val armed = settings.setupDone && settings.protectionEnabled
                if (armed && !status.accessibility && !a11yWarningCleared) {
                    AccessibilityOffWarning()
                } else {
                    val showAll = !armed || tempActive
                    val visible = allApps?.filter { showAll || it.packageName in settings.allowedApps }
                    HomeScreen(
                        apps = visible,
                        onLaunch = { Apps.launch(this, it) },
                        onAdminRequested = { screen = Screen.PIN_GATE },
                    )
                }
            }

            Screen.PIN_GATE -> {
                BackHandler { screen = Screen.HOME }
                var error by remember { mutableStateOf<String?>(null) }
                PinPad(
                    title = "Parent PIN",
                    eyebrow = "Parent access",
                    error = error,
                    onCancel = { screen = Screen.HOME },
                ) { pin ->
                    when (LockManager.checkPin(pin)) {
                        PinResult.OK -> screen = Screen.ADMIN
                        PinResult.MASTER -> screen = Screen.ADMIN_CHANGE_PIN
                        PinResult.WRONG -> error = "Wrong PIN"
                    }
                }
            }

            // ---------------- Parent panel ----------------
            Screen.ADMIN -> {
                BackHandler { screen = Screen.HOME }
                AdminPanel(
                    settings = settings,
                    status = status,
                    actions = AdminActions(
                        close = { screen = Screen.HOME },
                        chooseApps = { screen = Screen.ADMIN_APPS },
                        changePin = { screen = Screen.ADMIN_CHANGE_PIN },
                        tempAccess = { screen = Screen.ADMIN_TEMP },
                        setProtection = { on -> LockManager.update { setProtectionEnabled(on) } },
                        fixAccessibility = { SystemActions.openAccessibilitySettings(this) },
                        activateAdmin = { SystemActions.requestDeviceAdmin(this) },
                        deactivateAdmin = {
                            SystemActions.deactivateDeviceAdmin(this)
                            refresh++
                        },
                        setDefaultHome = { SystemActions.requestDefaultHome(this) },
                        fixBattery = { SystemActions.requestBatteryExemption(this) },
                        openDeveloperOptions = { SystemActions.openDeveloperOptions(this) },
                        openAppInfo = { SystemActions.openAppInfo(this) },
                    ),
                )
            }

            Screen.ADMIN_APPS -> {
                BackHandler { screen = Screen.ADMIN }
                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Eyebrow("${settings.allowedApps.size} selected")
                            Text("Allowed apps", fontSize = 32.sp, fontWeight = FontWeight.Light)
                        }
                        BwButton("Done", { screen = Screen.ADMIN })
                    }
                    AppPicker(allApps, settings.allowedApps, ::toggleApp, Modifier.weight(1f))
                }
            }

            Screen.ADMIN_CHANGE_PIN -> {
                BackHandler { screen = Screen.HOME }
                SetPinFlow(title = "Set a new PIN", onCancel = { screen = Screen.HOME }) { pin ->
                    LockManager.update { setPin(pin) }
                    screen = Screen.ADMIN
                }
            }

            Screen.ADMIN_TEMP -> {
                BackHandler { screen = Screen.ADMIN }
                TempAccessScreen(
                    onStart = { minutes ->
                        LockManager.startTempAccess(minutes * 60_000L)
                        screen = Screen.HOME
                    },
                    onCancel = { screen = Screen.ADMIN },
                )
            }
        }
    }

    /**
     * Shown instead of the home screen when the accessibility service has been
     * switched off: nothing can be used until the parent PIN is entered.
     */
    @Composable
    private fun AccessibilityOffWarning() {
        var error by remember { mutableStateOf<String?>(null) }
        PinPad(
            title = "Protection is\nswitched off",
            eyebrow = "Warning",
            subtitle = "The Family Lock accessibility service was turned off.\nEnter the parent PIN to continue.",
            error = error,
        ) { pin ->
            when (LockManager.checkPin(pin)) {
                PinResult.OK -> {
                    a11yWarningCleared = true
                    screen = Screen.ADMIN // the panel shows a "Turn on" button for the service
                }
                PinResult.MASTER -> {
                    a11yWarningCleared = true
                    screen = Screen.ADMIN_CHANGE_PIN
                }
                PinResult.WRONG -> error = "Wrong PIN"
            }
        }
    }
}
