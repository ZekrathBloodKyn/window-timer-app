package dev.wolsey.windowtimer.ui.timer

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val MAX_COUNT = 999

/**
 * A full-screen counter. Tap or swipe right to add one, swipe left to take one off.
 * Changes are reported as they happen, so there's nothing to confirm.
 */
@Composable
fun FullScreenCounter(
    title: String,
    count: Int,
    onCountChange: (Int) -> Unit,
    onDone: () -> Unit
) {
    // The gesture handlers below are set up once, so they read the latest values this way.
    val currentCount by rememberUpdatedState(count)
    val currentOnChange by rememberUpdatedState(onCountChange)

    val haptics = LocalHapticFeedback.current
    val swipeThreshold = with(LocalDensity.current) { 48.dp.toPx() }

    fun change(delta: Int) {
        val next = (currentCount + delta).coerceIn(0, MAX_COUNT)
        if (next != currentCount) {
            currentOnChange(next)
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // A tap anywhere counts up.
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { change(+1) })
                    }
                    // A swipe right counts up; a swipe left counts down.
                    .pointerInput(Unit) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragCancel = { total = 0f },
                            onDragEnd = {
                                if (total > swipeThreshold) change(+1)
                                else if (total < -swipeThreshold) change(-1)
                                total = 0f
                            },
                            onHorizontalDrag = { _, dragAmount -> total += dragAmount }
                        )
                    }
                    .safeDrawingPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium)

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = count.toString(),
                    fontSize = 160.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Tap or swipe right: +1\nSwipe left: −1",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Text("Done", fontSize = 22.sp)
                }
            }
        }
    }
}