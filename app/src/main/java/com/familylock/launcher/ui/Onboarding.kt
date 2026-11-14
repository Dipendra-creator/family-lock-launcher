package com.familylock.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One extra button on an onboarding page. */
data class StepAction(val label: String, val onClick: () -> Unit)

/**
 * Generic onboarding page: progress bar, big step number, heading, explanation,
 * action buttons that open a system screen, a status line, and Back / Next.
 */
@Composable
fun OnboardingStep(
    step: Int,
    totalSteps: Int,
    title: String,
    body: String,
    actions: List<StepAction> = emptyList(),
    /** null = no status line; true/false = done / not done yet. */
    done: Boolean? = null,
    doneText: String = "Done",
    pendingText: String = "Not done yet",
    nextLabel: String = "Next",
    /** If false, Next is disabled until [done] is true. */
    canSkip: Boolean = false,
    onBack: (() -> Unit)? = null,
    onNext: () -> Unit,
    content: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
        // Segmented progress bar.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(totalSteps) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(if (i < step) Bw.White else Bw.White.copy(alpha = 0.18f))
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%02d".format(step), fontSize = 54.sp, lineHeight = 54.sp, fontWeight = FontWeight.Thin)
            Text(
                " / %02d".format(totalSteps),
                color = Bw.Muted, fontSize = 14.sp, letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(title, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))

        Column(Modifier.weight(1f).then(if (content == null) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
            Text(body, color = Bw.Muted, fontSize = 14.5.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(18.dp))
            actions.forEachIndexed { i, action ->
                BwButton(action.label, action.onClick, Modifier.fillMaxWidth(), filled = i == 0)
                Spacer(Modifier.height(10.dp))
            }
            if (done != null) {
                Spacer(Modifier.height(8.dp))
                val shape = RoundedCornerShape(14.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Bw.Card)
                        .border(1.dp, if (done) Bw.White.copy(alpha = 0.6f) else Bw.Line, shape)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    // Filled dot = done, hollow ring = still to do.
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .then(if (done) Modifier.background(Bw.White) else Modifier.border(1.dp, Bw.Muted, CircleShape))
                    )
                    Text(
                        if (done) doneText else pendingText,
                        color = if (done) Bw.White else Bw.Muted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            if (content != null) content()
        }

        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) BwTextButton("BACK", onBack)
            Spacer(Modifier.weight(1f))
            if (canSkip && done == false) {
                BwButton("Skip", onNext, filled = false)
            } else {
                BwButton(nextLabel, onNext, enabled = done != false)
            }
        }
    }
}
