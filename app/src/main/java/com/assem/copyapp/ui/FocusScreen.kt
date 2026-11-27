package com.assem.copyapp.ui

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.assem.copyapp.MainViewModel
import com.assem.copyapp.R
import com.assem.copyapp.domain.SessionMode
import kotlinx.coroutines.delay

@Composable
fun FocusScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val session by vm.session.collectAsState()
    val settings by vm.settings.collectAsState()
    val duration by vm.duration.collectAsState()
    val mode by vm.mode.collectAsState()
    val strict by vm.strict.collectAsState()
    val selected by vm.selected.collectAsState()
    val query by vm.search.collectAsState()
    val apps = vm.filteredApps(query)
    val locked = session.active

    var remaining by remember { mutableLongStateOf(0L) }
    var showEndDialog by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(session.active, session.startMono, session.endWall, session.startWall) {
        while (session.active) {
            val planned = (session.endWall - session.startWall).coerceAtLeast(0L)
            val elapsed = (SystemClock.elapsedRealtime() - session.startMono).coerceAtLeast(0L)
            remaining = (planned - elapsed).coerceAtLeast(0L)
            delay(1000L)
        }
        remaining = 0L
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(stringResource(R.string.status_card)) {
            Text(
                if (session.active) formatTimer(remaining) else stringResource(R.string.no_active_session),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            LinearProgressIndicator(
                progress = if (session.active && session.endWall > session.startWall) {
                    ((SystemClock.elapsedRealtime() - session.startMono).toFloat() /
                        (session.endWall - session.startWall).toFloat()).coerceIn(0f, 1f)
                } else 0f,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Text(stringResource(R.string.mode_line, stringResource(mode.label())), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { vm.startFocus() }, enabled = !locked, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.start_focus))
                }
                OutlinedButton(
                    onClick = {
                        if (mode == SessionMode.HARD) vm.endFocus("HARD_ATTEMPT")
                        else showEndDialog = true
                    },
                    enabled = locked,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.end_session))
                }
            }
        }

        SectionCard(stringResource(R.string.permissions_card)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PermissionChip(vm.isAccessibilityEnabled(), stringResource(R.string.perm_accessibility))
                PermissionChip(vm.hasUsageStatsAccess(), stringResource(R.string.perm_usage))
                PermissionChip(vm.hasNotifications(), stringResource(R.string.perm_notifications))
                PermissionChip(vm.isIgnoringBattery(), stringResource(R.string.perm_battery))
            }
            Button(
                onClick = {
                    vm.permissionIntents().forEach { runCatching { context.startActivity(it) } }
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !vm.hasNotifications()
                    ) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.review_permissions))
            }
        }

        SectionCard(stringResource(R.string.blocked_apps_card)) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setSearch,
                label = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                enabled = !locked,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = vm::selectAll, enabled = !locked, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.select_all))
                }
                OutlinedButton(onClick = vm::clearSelection, enabled = !locked, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.clear_all))
                }
            }
            LazyColumn(Modifier.height(220.dp).fillMaxWidth()) {
                items(apps, key = { it.packageName }) { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !locked) { vm.togglePackage(app.packageName) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = app.packageName in selected,
                            onCheckedChange = { vm.togglePackage(app.packageName) },
                            enabled = !locked
                        )
                        Column {
                            Text(app.label, style = MaterialTheme.typography.bodyMedium)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        SectionCard(stringResource(R.string.duration_card)) {
            NumberField(duration, vm::setDuration, stringResource(R.string.minutes_hint), enabled = !locked)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf(15, 30, 60, 120).forEach { value ->
                    FilterChip(
                        selected = duration == value,
                        onClick = { vm.setDuration(value) },
                        enabled = !locked,
                        label = { Text("$value ${stringResource(R.string.minute_short)}") }
                    )
                }
            }
            FilterChip(
                selected = strict,
                onClick = { vm.setStrict(!strict) },
                enabled = !locked,
                label = { Text(stringResource(R.string.strict_mode_label)) },
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                FilterChip(
                    selected = mode == SessionMode.SOFT,
                    onClick = { vm.setMode(SessionMode.SOFT) },
                    enabled = !locked,
                    label = { Text(stringResource(R.string.mode_soft)) }
                )
                FilterChip(
                    selected = mode == SessionMode.HARD,
                    onClick = { vm.setMode(SessionMode.HARD) },
                    enabled = !locked,
                    label = { Text(stringResource(R.string.mode_hard)) }
                )
            }
        }

        SectionCard(stringResource(R.string.schedule_card)) {
            val start by vm.scheduleStart.collectAsState()
            val end by vm.scheduleEnd.collectAsState()
            Text(if (start >= 0 && end > start) stringResource(R.string.schedule_range, vm.formatClock(start), vm.formatClock(end)) else stringResource(R.string.schedule_off))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                NumberField(start, { vm.saveSchedule(it, end) }, stringResource(R.string.start_time), Modifier.weight(1f), !locked)
                NumberField(end, { vm.saveSchedule(start, it) }, stringResource(R.string.end_time), Modifier.weight(1f), !locked)
            }
            OutlinedButton(onClick = vm::clearSchedule, enabled = !locked, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.cancel_schedule))
            }
        }
    }

    if (showEndDialog) {
        SoftEndDialog(
            seconds = settings.pauseRequiredSeconds,
            onDismiss = { showEndDialog = false },
            onConfirm = {
                showEndDialog = false
                vm.endFocus("SOFT_FRICTION")
            }
        )
    }
}

@Composable
private fun PermissionChip(ok: Boolean, label: String) {
    FilterChip(selected = ok, onClick = {}, enabled = false, label = { Text("$label ${if (ok) "✅" else "❌"}") })
}

@Composable
private fun SoftEndDialog(seconds: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var left by remember { mutableIntStateOf(seconds.coerceAtLeast(1)) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000L)
            left--
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.soft_break_title)) },
        text = { Text(stringResource(R.string.soft_break_message, left)) },
        confirmButton = {
            Button(onClick = onConfirm, enabled = left == 0) {
                Text(stringResource(R.string.soft_break_confirm))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cleanup_cancel)) }
        }
    )
}

private fun SessionMode.label(): Int =
    if (this == SessionMode.HARD) R.string.mode_hard else R.string.mode_soft

private fun formatTimer(millis: Long): String {
    val total = millis / 1000L
    return String.format(
        java.util.Locale.getDefault(),
        "%02d:%02d:%02d",
        total / 3600,
        (total % 3600) / 60,
        total % 60
    )
}
