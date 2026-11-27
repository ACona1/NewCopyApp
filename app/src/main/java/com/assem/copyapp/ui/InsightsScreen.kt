package com.assem.copyapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.assem.copyapp.MainViewModel
import com.assem.copyapp.R

@Composable
fun InsightsScreen(vm: MainViewModel) {
    val sessions by vm.sessions.collectAsState()
    val events by vm.events.collectAsState()
    val usage by vm.usageSummary.collectAsState()
    val device by vm.deviceSummary.collectAsState()
    val score = remember(sessions, events) { vm.brainRotScore() }
    val levelLabel = when (com.assem.copyapp.domain.ScoreCalculator.level(score)) {
        "mashroo3" -> stringResource(R.string.score_safe)
        "motawasit" -> stringResource(R.string.score_medium)
        "khatr" -> stringResource(R.string.score_high)
        else -> stringResource(R.string.score_unknown)
    }
    val streak = remember(sessions) { calculateStreak(sessions.map { it.startWall }) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(stringResource(R.string.score_card)) {
            Text("$score / 100", style = MaterialTheme.typography.headlineLarge)
            Text(levelLabel, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = score / 100f, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }

        SectionCard(stringResource(R.string.streak_card)) {
            Text(stringResource(R.string.streak_days, streak), style = MaterialTheme.typography.titleMedium)
        }

        SectionCard(stringResource(R.string.focus_stats_card)) {
            val completed = sessions.count { it.completed }
            val minutes = sessions.sumOf { it.actualMinutes }
            Text(stringResource(R.string.sessions_today, completed))
            Text(stringResource(R.string.focus_minutes_today, minutes))
            if (sessions.isEmpty()) {
                InfoLine(stringResource(R.string.no_history))
            } else {
                sessions.take(5).forEach {
                    InfoLine("${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(it.startWall))} · ${it.actualMinutes} ${stringResource(R.string.minute_short)}")
                }
            }
        }

        SectionCard(stringResource(R.string.usage_card)) {
            Text(usage, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { vm.refreshInsights() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.usage_refreshed))
            }
        }

        SectionCard(stringResource(R.string.counts_card)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.addCount("reels") }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.count_reels)) }
                Button(onClick = { vm.addCount("shorts") }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.count_shorts)) }
                Button(onClick = { vm.addCount("feed_scroll") }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.count_feed)) }
            }
        }

        SectionCard(stringResource(R.string.device_card)) {
            Text(device, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { vm.cleanupBackground() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.cleanup_button))
            }
        }

        SectionCard(stringResource(R.string.digest_card)) {
            InfoLine(stringResource(R.string.digest_hint))
        }
    }
}

private fun calculateStreak(startWalls: List<Long>): Int {
    if (startWalls.isEmpty()) return 0
    val days = startWalls.map {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(it))
    }.toSet()
    var streak = 0
    var cursor = java.util.Calendar.getInstance()
    while (true) {
        val key = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(cursor.time)
        if (!days.contains(key)) break
        streak++
        cursor.add(java.util.Calendar.DAY_OF_YEAR, -1)
    }
    return streak
}
