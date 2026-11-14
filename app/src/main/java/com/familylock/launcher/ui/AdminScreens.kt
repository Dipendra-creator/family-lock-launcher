package com.familylock.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylock.launcher.data.LockManager
import com.familylock.launcher.data.LockSettings
import kotlinx.coroutines.delay

/** Current on/off state of each protection piece, read fresh every time the launcher resumes. */
data class ProtectionStatus(
    val accessibility: Boolean,
    val deviceAdmin: Boolean,
    val defaultHome: Boolean,
    val batteryExempt: Boolean,
)

/** Callbacks from the admin panel into the activity. */
class AdminActions(
    val close: () -> Unit,
    val chooseApps: () -> Unit,
    val changePin: () -> Unit,
    val tempAccess: () -> Unit,
    val setProtection: (Boolean) -> Unit,
    val fixAccessibility: () -> Unit,
    val activateAdmin: () -> Unit,
    val deactivateAdmin: () -> Unit,
    val setDefaultHome: () -> Unit,
    val fixBattery: () -> Unit,
    val openDeveloperOptions: () -> Unit,
    val openAppInfo: () -> Unit,
)

@Composable
fun AdminPanel(settings: LockSettings, status: ProtectionStatus, actions: AdminActions) {
    val remainingMs by produceState(LockManager.tempRemainingMs(), settings) {
        while (true) {
            value = LockManager.tempRemainingMs()
            delay(1000)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Family Lock")
                Spacer(Modifier.height(6.dp))
                Text("Parent panel", fontSize = 32.sp, fontWeight = FontWeight.Light)
            }
            BwButton("Close", actions.close, filled = false)
        }

        SectionTitle("Apps and access")
        Card {
            PanelRow("Allowed apps", "${settings.allowedApps.size} selected", onClick = actions.chooseApps)
            Hairline()
            if (remainingMs > 0) {
                PanelRow(
                    "Stop full access", "${formatDuration(remainingMs)} left - tap to lock now",
                    onClick = { LockManager.stopTempAccess() },
                )
            } else {
                PanelRow("Main screen / full access", "Unlock everything for a limited time, or permanently", onClick = actions.tempAccess)
            }
            Hairline()
            PanelRow("Change PIN", null, onClick = actions.changePin)
        }

        SectionTitle("Protection")
        Card {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Protection enabled", fontSize = 16.sp)
                    Text(
                        if (settings.protectionEnabled) "Locked apps and settings need the PIN"
                        else "OFF - all apps are visible and nothing is blocked",
                        color = if (settings.protectionEnabled) Bw.Muted else Bw.Error,
                        fontSize = 13.sp,
                    )
                }
                Switch(
                    checked = settings.protectionEnabled,
                    onCheckedChange = actions.setProtection,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Bw.Black,
                        checkedTrackColor = Bw.White,
                        uncheckedThumbColor = Bw.Muted,
                        uncheckedTrackColor = Bw.Black,
                        uncheckedBorderColor = Bw.Muted,
                    ),
                )
            }
            Hairline()
            StatusRow("Accessibility service", status.accessibility, "Turn on", actions.fixAccessibility)
            Hairline()
            StatusRow("Device admin (blocks uninstall)", status.deviceAdmin, "Activate", actions.activateAdmin)
            Hairline()
            StatusRow("Default home app", status.defaultHome, "Set", actions.setDefaultHome)
            Hairline()
            StatusRow("Battery optimisation off", status.batteryExempt, "Fix", actions.fixBattery)
        }

        SectionTitle("Hardening tips")
        Card {
            PanelRow(
                "Turn off USB debugging",
                "Open Developer options and switch off USB debugging (or Developer options entirely) once setup is finished.",
                onClick = actions.openDeveloperOptions,
            )
            Hairline()
            PanelRow(
                "Allow autostart (Xiaomi, Vivo, Oppo, Realme...)",
                "On these brands open App info for Family Lock and enable Autostart / Auto-launch, and set Battery to 'No restrictions'.",
                onClick = actions.openAppInfo,
            )
        }

        SectionTitle("Removing the app")
        Card {
            PanelRow(
                if (status.deviceAdmin) "Deactivate device admin" else "Device admin is not active",
                if (status.deviceAdmin) "After this, Family Lock can be uninstalled from App info."
                else "Family Lock can now be uninstalled from App info.",
                onClick = if (status.deviceAdmin) actions.deactivateAdmin else actions.openAppInfo,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Eyebrow(text, Modifier.padding(start = 6.dp, top = 28.dp, bottom = 10.dp))
}

/** Rounded, outlined container for a group of rows. */
@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Bw.Card).border(1.dp, Bw.Line, shape), content = content)
}

@Composable
private fun Hairline() {
    Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(1.dp).background(Bw.Line))
}

@Composable
private fun PanelRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = Bw.Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Text("›", color = Bw.Muted, fontSize = 22.sp, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun StatusRow(title: String, ok: Boolean, fixLabel: String, onFix: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Filled dot = OK, hollow ring = needs attention.
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .then(if (ok) Modifier.background(Bw.White) else Modifier.border(1.dp, Bw.Error, CircleShape))
        )
        Text(title, fontSize = 15.sp, color = if (ok) Bw.White else Bw.Error, modifier = Modifier.weight(1f).padding(start = 14.dp))
        if (ok) {
            Text("ON", color = Bw.Muted, fontSize = 11.sp, letterSpacing = 2.sp)
        } else {
            val shape = RoundedCornerShape(50)
            Text(
                fixLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Bw.Black,
                modifier = Modifier.clip(shape).background(Bw.White).clickable(onClick = onFix).padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Asked after the PIN when the parent wants the normal phone back: pick a time
 * limit (full access, then Family Lock returns by itself) or tick "permanent"
 * to switch Family Lock off and restore the phone's own home screen.
 */
@Composable
fun MainScreenAccessScreen(onStart: (minutes: Long) -> Unit, onPermanent: () -> Unit, onCancel: () -> Unit) {
    var preset by remember { mutableStateOf<Long?>(10L) }
    var custom by remember { mutableStateOf("") }
    var permanent by remember { mutableStateOf(false) }
    // A typed custom value wins over the preset tiles.
    val minutes = custom.toLongOrNull() ?: preset ?: 0L

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Eyebrow("Main screen")
        Spacer(Modifier.height(6.dp))
        Text("Full access", fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(10.dp))
        Text(
            "Opens the phone's normal home screen with every app unlocked. When the time is up, " +
                "Family Lock comes back and locks again. Restarting the phone ends it early.",
            color = Bw.Muted, fontSize = 14.5.sp, lineHeight = 22.sp,
        )
        Spacer(Modifier.height(24.dp))

        Eyebrow("How long")
        Spacer(Modifier.height(10.dp))
        // Preset durations; the selected one is filled white. Dimmed when "permanent" is ticked.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Triple(10L, "10", "MIN"), Triple(60L, "1", "HOUR"), Triple(120L, "2", "HOURS")).forEach { (value, big, unit) ->
                val selected = !permanent && custom.isEmpty() && preset == value
                val shape = RoundedCornerShape(20.dp)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(if (selected) Bw.White else Bw.Card)
                        .border(1.dp, Bw.White.copy(alpha = if (permanent) 0.12f else 0.35f), shape)
                        .clickable(enabled = !permanent) {
                            preset = value
                            custom = ""
                        }
                        .padding(vertical = 18.dp),
                ) {
                    val ink = when {
                        selected -> Bw.Black
                        permanent -> Bw.White.copy(alpha = 0.3f)
                        else -> Bw.White
                    }
                    Text(big, color = ink, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Thin)
                    Text(unit, color = if (selected) Bw.Black else Bw.Muted.copy(alpha = if (permanent) 0.4f else 1f), fontSize = 11.sp, letterSpacing = 3.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = custom,
            onValueChange = { v -> custom = v.filter(Char::isDigit).take(4) },
            enabled = !permanent,
            label = { Text("Custom minutes") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Bw.White,
                unfocusedBorderColor = Bw.White.copy(alpha = 0.35f),
                disabledBorderColor = Bw.White.copy(alpha = 0.12f),
                focusedLabelColor = Bw.White,
                unfocusedLabelColor = Bw.Muted,
                disabledLabelColor = Bw.Muted.copy(alpha = 0.4f),
                cursorColor = Bw.White,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))
        // "Permanent" option.
        val boxShape = RoundedCornerShape(18.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(boxShape)
                .background(Bw.Card)
                .border(1.dp, if (permanent) Bw.White else Bw.Line, boxShape)
                .clickable { permanent = !permanent }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            BwCheck(permanent)
            Column(Modifier.padding(start = 14.dp)) {
                Text("Permanent return to main screen", fontSize = 15.5.sp)
                Text(
                    "Turns Family Lock off and restores the normal home screen until you turn it back on from the Family Lock app.",
                    color = Bw.Muted, fontSize = 13.sp, lineHeight = 18.sp,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        if (permanent) {
            BwButton("Return to main screen", onPermanent, Modifier.fillMaxWidth())
        } else {
            BwButton(
                if (minutes > 0) "Start ${formatMinutes(minutes)} of full access" else "Start full access",
                { onStart(minutes) },
                Modifier.fillMaxWidth(),
                enabled = minutes > 0,
            )
        }
        Spacer(Modifier.height(6.dp))
        BwTextButton("CANCEL", onCancel, Modifier.align(Alignment.CenterHorizontally))
    }
}

/** 10 -> "10 min", 60 -> "1 hr", 90 -> "1 hr 30 min". */
private fun formatMinutes(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return listOfNotNull(if (h > 0) "$h hr" else null, if (m > 0) "$m min" else null).joinToString(" ")
}
