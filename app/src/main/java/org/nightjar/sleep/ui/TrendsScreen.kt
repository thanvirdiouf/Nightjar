package org.nightjar.sleep.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nightjar.sleep.core.AppContainer
import org.nightjar.sleep.core.storage.DailyTag
import org.nightjar.sleep.core.storage.NoiseEvent
import java.time.*
import kotlin.math.*

@Composable fun TrendsScreen(app: AppContainer) {
    val all by app.repository.sessions.collectAsStateWithLifecycle(emptyList())
    var days by remember { mutableIntStateOf(7) }
    var tags by remember { mutableStateOf(emptyList<DailyTag>()) }
    var noise by remember { mutableStateOf(emptyList<NoiseEvent>()) }
    LaunchedEffect(all) { tags = app.repository.dao.allTags(); noise = app.repository.dao.allNoise() }
    val since = LocalDate.now().minusDays(days - 1L).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val sessions = all.filter { it.endTime != null && it.startTime >= since }.sortedBy { it.startTime }
    val scored = sessions.filter { it.score != null }
    fun consistency(times: List<Long>): String {
        if (times.size < 2) return "Need two nights"
        val angles = times.map {
            val time = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime()
            (time.hour * 60 + time.minute) / 1440.0 * 2 * PI
        }
        val length = hypot(angles.map(::cos).average(), angles.map(::sin).average()).coerceIn(.00001, 1.0)
        return "~${(sqrt(-2 * ln(length)) * 1440 / (2 * PI)).roundToInt()} min variation"
    }
    Page("See your patterns.", "Small changes become clearer over time.") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = days == 7, onClick = { days = 7 }, label = { Text("7 days") })
            FilterChip(selected = days == 30, onClick = { days = 30 }, label = { Text("30 days") })
        }
        if (sessions.isEmpty()) Panel("Your rhythm will emerge") {
            Text("Completed nights appear here. Track a few nights to compare sleep duration, quality, and consistency.")
        } else {
            Panel("Over ${sessions.size} nights") {
                Detail("Average quality", if (scored.isEmpty()) "—" else "%.0f / 100".format(scored.map { it.score!! }.average()))
                Detail("Average sleep", durationLabel(sessions.map { it.sleepMs ?: 0 }.average().toLong()))
                Detail("Bedtime consistency", consistency(sessions.map { it.startTime }))
                Detail("Wake-time consistency", consistency(sessions.map { it.endTime!! }))
            }
            Panel("Quality estimates") {
                TrendBars(sessions.map { it.score?.toFloat() ?: 0f }, 100f, "Quality scores: " + sessions.joinToString { "${dateLabel(it.startTime)} ${it.score ?: "unavailable"}" })
                Text("Oldest → latest • 0–100", style = MaterialTheme.typography.bodySmall)
            }
            Panel("Estimated sleep duration") {
                TrendBars(sessions.map { (it.sleepMs ?: 0) / 3_600_000f }, maxOf(8f, sessions.maxOf { (it.sleepMs ?: 0) / 3_600_000f }), "Sleep durations: " + sessions.joinToString { durationLabel(it.sleepMs) })
                Text("Oldest → latest • hours", style = MaterialTheme.typography.bodySmall)
            }
            Panel("Bedtime and wake-up rhythm") {
                fun hoursFromNoon(time: Long): Float {
                    val local = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalTime()
                    return ((local.hour * 60 + local.minute + 720) % 1440) / 60f
                }
                Text("Bedtime")
                TrendBars(sessions.map { hoursFromNoon(it.startTime) }, 24f, "Bedtimes: " + sessions.joinToString { clockTime(it.startTime) })
                Text("Wake-up")
                TrendBars(sessions.map { hoursFromNoon(it.endTime!!) }, 24f, "Wake times: " + sessions.joinToString { clockTime(it.endTime!!) })
                Text("Oldest → latest • height runs from noon through midnight to the following noon.", style = MaterialTheme.typography.bodySmall)
            }
            Panel("Candidate noise events") {
                val counts = sessions.map { session -> noise.count { it.sessionId == session.id }.toFloat() }
                TrendBars(counts, maxOf(1f, counts.maxOrNull() ?: 1f), "Noise events per night: " + counts.joinToString { it.toInt().toString() })
                Text("Oldest → latest • only nights with recording enabled can capture events.", style = MaterialTheme.typography.bodySmall)
            }
            Panel("Notes and patterns") {
                val ids = scored.map { it.id }.toSet()
                val groups = tags.filter { it.sessionId in ids }.groupBy { it.tag }
                if (groups.isEmpty()) Text("Add tags such as caffeine, stress, or exercise to completed nights to compare your scores.")
                groups.forEach { (tag, values) ->
                    val tagged = values.map { it.sessionId }.toSet()
                    val with = scored.filter { it.id in tagged }
                    val without = scored.filter { it.id !in tagged }
                    if (with.size >= 2 && without.size >= 2)
                        Detail(tag, "%+.0f points (%d nights)".format(with.map { it.score!! }.average() - without.map { it.score!! }.average(), with.size))
                    else Detail(tag, "${with.size} nights • more data needed")
                }
                Text("Differences are associations, not proof of cause. At least two scored nights with and without a tag are needed.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable private fun TrendBars(values: List<Float>, maximum: Float, description: String) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = description }) {
        if (values.isEmpty()) return@Canvas
        val width = size.width / values.size
        values.forEachIndexed { index, value ->
            val height = value.coerceIn(0f, maximum) / maximum * size.height
            drawRect(color.copy(alpha = .7f), Offset(index * width + width * .15f, size.height - height), Size(width * .7f, height))
        }
    }
}
