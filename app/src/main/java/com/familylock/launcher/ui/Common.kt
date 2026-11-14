package com.familylock.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylock.launcher.AppEntry
import com.familylock.launcher.Config

/** The monochrome palette used everywhere in the app. */
object Bw {
    val Black = Color.Black
    val White = Color.White
    /** Hairlines and outlines. */
    val Line = Color(0xFF2B2B2B)
    /** Secondary text. */
    val Muted = Color(0xFF8E8E8E)
    /** Card fill: almost opaque so text stays readable over the pattern. */
    val Card = Color(0xF00B0B0B)
    /** The only non-grey colour, used for error text. */
    val Error = Color(0xFFFF7A7A)
}

private val Colors = darkColorScheme(
    primary = Bw.White,
    onPrimary = Bw.Black,
    secondary = Bw.White,
    background = Bw.Black,
    surface = Bw.Black,
    surfaceVariant = Color(0xFF151515),
    onBackground = Bw.White,
    onSurface = Bw.White,
    onSurfaceVariant = Bw.Muted,
    outline = Bw.Line,
    error = Bw.Error,
)

@Composable
fun FamilyLockTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}

/** Turns any app icon black-and-white so the launcher looks like one set. */
val GrayscaleFilter: ColorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * App-wide backdrop: black, with a fine dot grid and thin concentric rings
 * radiating from two opposite corners. Drawn once and cached.
 */
@Composable
fun PatternBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bw.Black)
            .drawWithCache {
                val step = 22.dp.toPx()
                val w = size.width
                val h = size.height
                val dots = buildList {
                    var y = step / 2
                    while (y < h) {
                        var x = step / 2
                        while (x < w) {
                            add(Offset(x, y))
                            x += step
                        }
                        y += step
                    }
                }
                val ringGap = 46.dp.toPx()
                val hairline = Stroke(width = 1.dp.toPx())
                val dotSize = 1.5.dp.toPx()
                onDrawBehind {
                    drawPoints(dots, PointMode.Points, Color.White.copy(alpha = 0.10f), dotSize, StrokeCap.Round)
                    // Rings from the top-right corner, fading as they grow.
                    for (i in 1..10) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.16f - i * 0.013f),
                            radius = ringGap * i,
                            center = Offset(size.width, 0f),
                            style = hairline,
                        )
                    }
                    // A smaller echo from the bottom-left corner.
                    for (i in 1..6) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.12f - i * 0.016f),
                            radius = ringGap * i,
                            center = Offset(0f, size.height),
                            style = hairline,
                        )
                    }
                }
            },
        content = content,
    )
}

/** Small letter-spaced caption used above headings ("PARENT ACCESS", "STEP"). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Bw.Muted) {
    Text(text.uppercase(), color = color, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Medium, modifier = modifier)
}

/** Pill button: white-on-black when [filled], thin outline otherwise. */
@Composable
fun BwButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(50)
    val alpha = if (enabled) 1f else 0.3f
    Box(
        modifier = modifier
            .heightIn(min = 50.dp)
            .clip(shape)
            .then(
                if (filled) Modifier.background(Bw.White.copy(alpha = alpha))
                else Modifier.border(1.dp, Bw.White.copy(alpha = 0.45f * alpha), shape)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (filled) Bw.Black else Bw.White.copy(alpha = alpha),
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            letterSpacing = 0.3.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** Plain text link-style button. */
@Composable
fun BwTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Bw.Muted,
        fontSize = 14.sp,
        letterSpacing = 1.sp,
        modifier = modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
    )
}

/** Monochrome tick box. */
@Composable
fun BwCheck(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .then(if (checked) Modifier.background(Bw.White) else Modifier.border(1.dp, Bw.White.copy(alpha = 0.35f), CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", color = Bw.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** One round key of the PIN pad. */
@Composable
private fun PinKey(label: String, onClick: () -> Unit) {
    val isOk = label == "OK"
    Box(
        modifier = Modifier
            .padding(9.dp)
            .size(74.dp)
            .clip(CircleShape)
            .then(if (isOk) Modifier.background(Bw.White) else Modifier.border(1.dp, Bw.White.copy(alpha = 0.26f), CircleShape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (isOk) Bw.Black else Bw.White,
            fontSize = if (isOk) 16.sp else 27.sp,
            fontWeight = if (isOk) FontWeight.Bold else FontWeight.Light,
            letterSpacing = if (isOk) 1.sp else 0.sp,
        )
    }
}

/**
 * Numeric keypad used by every PIN screen inside the launcher.
 * Calls [onSubmit] with the typed digits when OK is pressed.
 */
@Composable
fun PinPad(
    title: String,
    subtitle: String? = null,
    eyebrow: String = "Family Lock",
    error: String? = null,
    onCancel: (() -> Unit)? = null,
    onSubmit: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Eyebrow(eyebrow)
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = Bw.Muted, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(26.dp))

        // Entered digits: filled dots; empty placeholders: hollow rings.
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(maxOf(Config.PIN_MIN_LENGTH, pin.length)) { i ->
                Box(
                    Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .then(if (i < pin.length) Modifier.background(Bw.White) else Modifier.border(1.dp, Bw.White.copy(alpha = 0.4f), CircleShape))
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(error ?: " ", color = Bw.Error, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))

        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK").chunked(3).forEach { row ->
            Row {
                row.forEach { key ->
                    PinKey(key) {
                        when (key) {
                            "⌫" -> pin = pin.dropLast(1)
                            "OK" -> if (pin.isNotEmpty()) {
                                val entered = pin
                                pin = ""
                                onSubmit(entered)
                            }
                            else -> if (pin.length < Config.PIN_MAX_LENGTH) pin += key
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (onCancel != null) BwTextButton("CANCEL", onCancel) else Spacer(Modifier.height(44.dp))
    }
}

/** Two-step "enter new PIN, then confirm it" flow. */
@Composable
fun SetPinFlow(title: String, eyebrow: String = "Family Lock", onCancel: (() -> Unit)? = null, onPinChosen: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    if (first == null) {
        PinPad(
            title = title,
            subtitle = "Choose ${Config.PIN_MIN_LENGTH}-${Config.PIN_MAX_LENGTH} digits, then press OK",
            eyebrow = eyebrow,
            error = error,
            onCancel = onCancel,
        ) { pin ->
            when {
                pin.length < Config.PIN_MIN_LENGTH -> error = "PIN must be at least ${Config.PIN_MIN_LENGTH} digits"
                pin == Config.MASTER_CODE -> error = "That is the master code. Choose a different PIN"
                else -> {
                    error = null
                    first = pin
                }
            }
        }
    } else {
        PinPad(
            title = "Confirm PIN",
            subtitle = "Enter the same PIN again",
            eyebrow = eyebrow,
            error = error,
            onCancel = { first = null; error = null },
        ) { pin ->
            if (pin == first) onPinChosen(pin)
            else {
                first = null
                error = "PINs did not match. Try again"
            }
        }
    }
}

/** Tick list of all launchable apps. [apps] is null while loading. */
@Composable
fun AppPicker(
    apps: List<AppEntry>?,
    allowed: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (apps == null) {
        Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Bw.White, strokeWidth = 1.5.dp)
        }
        return
    }
    val shape = RoundedCornerShape(20.dp)
    LazyColumn(modifier.clip(shape).background(Bw.Card).border(1.dp, Bw.Line, shape)) {
        items(apps, key = { it.packageName }) { app ->
            val checked = app.packageName in allowed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(app.packageName, !checked) }
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    bitmap = app.icon,
                    contentDescription = null,
                    colorFilter = GrayscaleFilter,
                    alpha = if (checked) 1f else 0.55f,
                    modifier = Modifier.size(38.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(16.dp))
                Text(app.label, modifier = Modifier.weight(1f), color = if (checked) Bw.White else Bw.Muted, fontSize = 15.sp)
                BwCheck(checked)
            }
        }
    }
}
