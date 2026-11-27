package com.assem.copyapp.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.assem.copyapp.MainViewModel
import com.assem.copyapp.R
import com.assem.copyapp.domain.SessionMode

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val settings by vm.settings.collectAsState()
    val locked by vm.session.collectAsState()
    var friendKey by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(stringResource(R.string.general_card)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to R.string.language_system, "ar" to R.string.language_ar, "en" to R.string.language_en).forEach { (tag, label) ->
                    FilterChip(
                        selected = settings.language == tag,
                        onClick = {
                            val locales = if (tag == "system") LocaleListCompat.getEmptyLocaleList()
                            else LocaleListCompat.forLanguageTag(tag)
                            AppCompatDelegate.setApplicationLocales(locales)
                            vm.updateSettings { copy(language = tag) }
                        },
                        label = { Text(stringResource(label)) }
                    )
                }
            }
            OutlinedButton(
                onClick = { vm.updateSettings { copy(onboardingDone = false) } },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text(stringResource(R.string.replay_onboarding)) }
        }

        SectionCard(stringResource(R.string.session_defaults_card)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.defaultMode == SessionMode.SOFT,
                    onClick = { vm.updateSettings { copy(defaultMode = SessionMode.SOFT) } },
                    enabled = !locked.active,
                    label = { Text(stringResource(R.string.mode_soft)) }
                )
                FilterChip(
                    selected = settings.defaultMode == SessionMode.HARD,
                    onClick = { vm.updateSettings { copy(defaultMode = SessionMode.HARD) } },
                    enabled = !locked.active,
                    label = { Text(stringResource(R.string.mode_hard)) }
                )
            }
            NumberField(
                settings.defaultDurationMinutes,
                { value -> vm.updateSettings { copy(defaultDurationMinutes = value.coerceIn(1, 600)) } },
                stringResource(R.string.minutes_hint),
                enabled = !locked.active
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Checkbox(
                    checked = settings.strictByDefault,
                    onCheckedChange = { checked -> vm.updateSettings { copy(strictByDefault = checked) } },
                    enabled = !locked.active
                )
                Text(stringResource(R.string.strict_mode_label))
            }
            Text(stringResource(R.string.settings_locked_hint), style = MaterialTheme.typography.bodySmall)
        }

        SectionCard(stringResource(R.string.friend_key_card)) {
            OutlinedTextField(
                friendKey,
                { friendKey = it },
                label = { Text(stringResource(R.string.friend_key_hint)) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { vm.setFriendKey(friendKey); friendKey = "" }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.save_friend_key))
                }
                OutlinedButton(onClick = { vm.setFriendKey("") }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.clear_friend_key))
                }
            }
            InfoLine(stringResource(R.string.friend_key_note))
        }

        SectionCard(stringResource(R.string.wind_down_card)) {
            Row {
                Checkbox(
                    checked = settings.windDownEnabled,
                    onCheckedChange = { checked -> vm.updateSettings { copy(windDownEnabled = checked) } }
                )
                Text(stringResource(R.string.wind_down_enable))
            }
            NumberField(
                settings.windDownStartMin,
                { value -> vm.updateSettings { copy(windDownStartMin = value.coerceIn(0, 1439)) } },
                stringResource(R.string.wind_down_start)
            )
            NumberField(
                settings.bedtimeMin,
                { value -> vm.updateSettings { copy(bedtimeMin = value.coerceIn(0, 1439)) } },
                stringResource(R.string.bedtime_start),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        SectionCard(stringResource(R.string.backup_card)) {
            Row {
                Checkbox(
                    checked = settings.digestEnabled,
                    onCheckedChange = { checked -> vm.updateSettings { copy(digestEnabled = checked) } }
                )
                Text(stringResource(R.string.digest_enable))
            }
            Button(
                onClick = { vm.exportJson { json -> clipboard.setText(AnnotatedString(json)) } },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.export_json)) }
            OutlinedTextField(
                importText,
                { importText = it },
                label = { Text(stringResource(R.string.import_hint)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                maxLines = 4
            )
            OutlinedButton(onClick = { vm.importJson(importText) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.import_json))
            }
        }

        SectionCard(stringResource(R.string.about_card)) {
            Text(stringResource(R.string.version_line), style = MaterialTheme.typography.bodyMedium)
            InfoLine(stringResource(R.string.local_only_note))
            Button(
                onClick = {
                    vm.permissionIntents().forEach { runCatching { context.startActivity(it) } }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text(stringResource(R.string.review_permissions)) }
        }
    }
}
