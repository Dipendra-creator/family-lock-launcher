package com.familylock.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylock.launcher.AppEntry
import com.familylock.launcher.data.LockManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The minimalist home screen: clock, date, app search, the visible apps and
 * the "Main screen" switch.
 *
 * - Long-pressing the clock is the hidden entry to the parent panel.
 * - The switch at the bottom asks for the PIN and then offers timed or
 *   permanent access to the phone's normal home screen.
 *
 * @param resetKey changes every time the launcher comes back to the front; the search box is cleared then.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    apps: List<AppEntry>?,
    resetKey: Int,
    onLaunch: (String) -> Unit,
    onAdminRequested: () -> Unit,
    onMainScreenSwitch: () -> Unit,
) {
    // Ticks once a second: drives both the clock and the full-access countdown.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val remainingMs = remember(now) { LockManager.tempRemainingMs() }
    val fullAccess = remainingMs > 0

    var query by remember(resetKey) { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    // Coming back to the home screen: drop focus so the keyboard does not pop up again.
    LaunchedEffect(resetKey) { focusManager.clearFocus(force = true) }
    val shown = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps?.filter { it.label.contains(q, ignoreCase = true) }
    }
    fun launch(pkg: String) {
        focusManager.clearFocus()
        query = ""
        onLaunch(pkg)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // While searching, the clock makes way so results stay visible above the keyboard.
        if (query.isEmpty()) {
            Spacer(Modifier.height(44.dp))
            Text(
                text = remember(now / 60000) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)) },
                fontSize = 88.sp,
                fontWeight = FontWeight.Thin,
                letterSpacing = (-2).sp,
                modifier = Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, // no ripple: the entry stays hidden
                    onClick = {},
                    onLongClick = onAdminRequested,
                ),
            )
            // Date between two hairlines.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(28.dp).height(1.dp).background(Bw.White.copy(alpha = 0.35f)))
                Text(
                    text = remember(now / 60000) {
                        SimpleDateFormat("EEE  d MMM", Locale.getDefault()).format(Date(now)).uppercase()
                    },
                    color = Bw.Muted,
                    fontSize = 12.sp,
                    letterSpacing = 4.sp,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                Box(Modifier.width(28.dp).height(1.dp).background(Bw.White.copy(alpha = 0.35f)))
            }
            Spacer(Modifier.height(26.dp))
        } else {
            Spacer(Modifier.height(18.dp))
        }

        // ---- Search box --------------------------------------------------------
        val searchShape = RoundedCornerShape(50)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
                .height(48.dp)
                .clip(searchShape)
                .background(Bw.Card)
                .border(1.dp, Bw.White.copy(alpha = 0.22f), searchShape)
                .padding(horizontal = 18.dp),
        ) {
            // Magnifier drawn by hand so it matches the line style.
            Canvas(Modifier.size(16.dp)) {
                val stroke = 1.5.dp.toPx()
                val r = size.minDimension * 0.34f
                val c = Offset(r + stroke, r + stroke)
                drawCircle(Bw.Muted, r, c, style = Stroke(stroke))
                drawLine(Bw.Muted, Offset(c.x + r * 0.75f, c.y + r * 0.75f), Offset(size.width, size.height), stroke, StrokeCap.Round)
            }
            Box(Modifier.weight(1f).padding(start = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text("Search apps", color = Bw.Muted, fontSize = 15.sp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Bw.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Bw.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    // The keyboard's search key opens the first match.
                    keyboardActions = KeyboardActions(onSearch = { shown?.firstOrNull()?.let { launch(it.packageName) } }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Text(
                    "✕", color = Bw.Muted, fontSize = 15.sp,
                    modifier = Modifier.clip(CircleShape).clickable {
                        query = ""
                        focusManager.clearFocus()
                    }.padding(8.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        // ---- Apps ----------------------------------------------------------------
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            when {
                shown == null -> Unit // still loading
                shown.isEmpty() -> Text(
                    if (query.isNotEmpty()) "No app matches \"${query.trim()}\""
                    else "No apps allowed yet.\nLong-press the clock to open the parent panel.",
                    color = Bw.Muted,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 40.dp),
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(shown, key = { it.packageName }) { app ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { launch(app.packageName) }
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                        ) {
                            // Greyscale icon inside a thin ring.
                            Box(
                                modifier = Modifier.size(62.dp).border(1.dp, Bw.White.copy(alpha = 0.22f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    bitmap = app.icon,
                                    contentDescription = null,
                                    colorFilter = GrayscaleFilter,
                                    modifier = Modifier.size(50.dp).clip(CircleShape),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                app.label,
                                fontSize = 11.sp,
                                letterSpacing = 0.4.sp,
                                color = Bw.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        // ---- Main screen switch ------------------------------------------------
        // Off (knob left): tap -> PIN -> choose timed or permanent access to the
        // normal home screen. On (knob right, countdown): tap to lock again now.
        val switchShape = RoundedCornerShape(50)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(bottom = 14.dp, top = 6.dp)
                .clip(switchShape)
                .background(if (fullAccess) Bw.White else Bw.Card)
                .border(1.dp, Bw.White.copy(alpha = 0.3f), switchShape)
                .clickable(onClick = onMainScreenSwitch)
                .padding(start = 10.dp, end = 18.dp, top = 9.dp, bottom = 9.dp),
        ) {
            // The switch itself.
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 22.dp)
                    .clip(switchShape)
                    .then(if (fullAccess) Modifier.background(Bw.Black) else Modifier.border(1.dp, Bw.White.copy(alpha = 0.5f), switchShape))
                    .padding(3.dp),
                contentAlignment = if (fullAccess) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(Bw.White))
            }
            Text(
                text = if (fullAccess) "FULL ACCESS  ${formatDuration(remainingMs)}" else "MAIN SCREEN",
                color = if (fullAccess) Bw.Black else Bw.White,
                fontSize = 11.sp,
                fontWeight = if (fullAccess) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = 2.5.sp,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** 3725000 -> "1:02:05", 125000 -> "2:05". */
fun formatDuration(ms: Long): String {
    val total = (ms + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
