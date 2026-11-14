package dev.wolsey.windowtimer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.wolsey.windowtimer.timer.IntervalPlan
import dev.wolsey.windowtimer.timer.IntervalSettings
import kotlin.math.roundToInt

/** How many evenly spaced alerts the timer gives. Saved as you change it. */
@Composable
fun IntervalsSection() {
    val context = LocalContext.current
    var plan by remember { mutableStateOf(IntervalSettings.load(context)) }

    fun update(next: IntervalPlan) {
        plan = next
        IntervalSettings.save(context, next)
    }

    fun everyText(count: Int): String {
        val percent = 100.0 / count
        return "One alert about every " +
                (if (percent % 1.0 == 0.0) "%.0f".format(percent) else "%.1f".format(percent)) +
                "% of the time"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Intervals", style = MaterialTheme.typography.titleMedium)
        Text(
            "The timer alerts at evenly spaced points through the estimate. The last interval " +
                    "plays the final sound; the ones before it use the early, middle and late sounds below. " +
                    "When interior and exterior run separately, the end of the first part plays the " +
                    "part complete sound.",
            style = MaterialTheme.typography.bodySmall
        )

        if (!plan.separate) {
            SettingSlider(
                label = "Intervals: ${plan.count}",
                value = plan.count.toFloat(),
                range = IntervalPlan.MIN.toFloat()..IntervalPlan.MAX.toFloat(),
                steps = IntervalPlan.MAX - IntervalPlan.MIN - 1,
                onChange = { update(plan.copy(count = it.roundToInt())) }
            )
            Text(everyText(plan.count), style = MaterialTheme.typography.bodySmall)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Separate interior and exterior")
                Text(
                    "When a job has both, run the interior and exterior parts one after the other, " +
                            "each with its own intervals.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = plan.separate,
                onCheckedChange = { update(plan.copy(separate = it)) }
            )
        }

        if (plan.separate) {
            SettingSlider(
                label = "Interior intervals: ${plan.interiorCount}",
                value = plan.interiorCount.toFloat(),
                range = IntervalPlan.MIN.toFloat()..IntervalPlan.MAX.toFloat(),
                steps = IntervalPlan.MAX - IntervalPlan.MIN - 1,
                onChange = { update(plan.copy(interiorCount = it.roundToInt())) }
            )
            SettingSlider(
                label = "Exterior intervals: ${plan.exteriorCount}",
                value = plan.exteriorCount.toFloat(),
                range = IntervalPlan.MIN.toFloat()..IntervalPlan.MAX.toFloat(),
                steps = IntervalPlan.MAX - IntervalPlan.MIN - 1,
                onChange = { update(plan.copy(exteriorCount = it.roundToInt())) }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Interior first")
                Switch(
                    checked = plan.interiorFirst,
                    onCheckedChange = { update(plan.copy(interiorFirst = it)) }
                )
            }
            Text(
                "A job with only one kind of window uses that kind's interval count and has no " +
                        "part complete alert.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}