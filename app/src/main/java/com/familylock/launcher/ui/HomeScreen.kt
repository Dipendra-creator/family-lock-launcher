package com.familylock.launcher.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
 * The minimalist home screen: clock, date and the visible apps.
 * Long-pressing the clock is the hidden entry to the admin PIN screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    apps: List<AppEntry>?,
    onLaunch: (String) -> Unit,
    onAdminRequested: () -> Unit,
) {
    // Ticks once a second: drives both the clock and the temporary-access countdown.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val remainingMs = remember(now) { LockManager.tempRemainingMs() }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(52.dp))
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

        if (remainingMs > 0) {
            Spacer(Modifier.height(18.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Bw.White)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Bw.Black))
                Text(
                    text = "FULL ACCESS  ${formatDuration(remainingMs)}",
                    color = Bw.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        when {
            apps == null -> Unit // still loading
            apps.isEmpty() -> Text(
                "No apps allowed yet.\nLong-press the clock to open the parent panel.",
                color = Bw.Muted,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 48.dp),
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(apps, key = { it.packageName }) { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onLaunch(app.packageName) }
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
}

/** 3725000 -> "1:02:05", 125000 -> "2:05". */
fun formatDuration(ms: Long): String {
    val total = (ms + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
