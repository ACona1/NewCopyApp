package com.assem.copyapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assem.copyapp.R
import com.assem.copyapp.domain.BlockReason
import com.assem.copyapp.domain.SessionMode
import kotlinx.coroutines.delay

@Composable
fun BlockingScreen(
    appLabel: String,
    reason: BlockReason,
    mode: SessionMode,
    remaining: Long,
    totalPlanned: Long,
    frictionSeconds: Int,
    onHome: () -> Unit,
    onEmergency: () -> Unit,
    onSoftEnd: () -> Unit,
    onFriendKey: (String) -> Unit,
    onIllegalExit: () -> Unit
) {
    BackHandler { onIllegalExit() }

    var showFriction by remember { mutableStateOf(false) }
    var friendKey by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔒", style = MaterialTheme.typography.displayLarge)
        Text(stringResource(R.string.blocking_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(appLabel, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
        Text(stringResource(reason.label()), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Text(formatTimer(remaining), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(
            progress = if (totalPlanned > 0) ((totalPlanned - remaining).toFloat() / totalPlanned.toFloat()).coerceIn(0f, 1f) else 0f,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.blocking_home)) }
        OutlinedButton(onClick = onEmergency, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.blocking_emergency)) }
        if (mode == SessionMode.SOFT) {
            Button(
                onClick = { showFriction = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.blocking_stop)) }
        } else {
            Text(stringResource(R.string.hard_mode_blocking_note), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            OutlinedTextField(friendKey, { friendKey = it }, label = { Text(stringResource(R.string.friend_key_hint)) }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onFriendKey(friendKey); friendKey = "" }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.friend_key_unlock))
            }
        }
    }

    if (showFriction) {
        var left by remember { mutableIntStateOf(frictionSeconds.coerceAtLeast(1)) }
        LaunchedEffect(Unit) {
            while (left > 0) { delay(1000L); left-- }
        }
        AlertDialog(
            onDismissRequest = { showFriction = false },
            title = { Text(stringResource(R.string.soft_break_title)) },
            text = { Text(stringResource(R.string.soft_break_message, left)) },
            confirmButton = {
                Button(onClick = { showFriction = false; onSoftEnd() }, enabled = left == 0) {
                    Text(stringResource(R.string.soft_break_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showFriction = false }) { Text(stringResource(R.string.cleanup_cancel)) }
            }
        )
    }
}

private fun BlockReason.label(): Int = when (this) {
    BlockReason.SESSION_APP -> R.string.reason_session
    BlockReason.STRICT -> R.string.reason_strict
    BlockReason.BUDGET -> R.string.reason_budget
    BlockReason.SCHEDULE -> R.string.reason_schedule
    BlockReason.WIND_DOWN -> R.string.reason_wind_down
    BlockReason.BEDTIME -> R.string.reason_bedtime
}

private fun formatTimer(millis: Long): String {
    val total = millis / 1000L
    return String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
}
