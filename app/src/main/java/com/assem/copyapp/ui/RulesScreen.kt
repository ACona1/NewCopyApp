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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.assem.copyapp.AppContainer
import com.assem.copyapp.MainViewModel
import com.assem.copyapp.R
import com.assem.copyapp.domain.DetectorType
import com.assem.copyapp.domain.RuleAction

@Composable
fun RulesScreen(vm: MainViewModel, container: AppContainer) {
    val rules by vm.rules.collectAsState()
    val detectors by vm.detectors.collectAsState()
    val limits by vm.limits.collectAsState()
    val settings by vm.settings.collectAsState()
    val node by container.lastNode.collectAsState()

    var pkg by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var action by remember { mutableStateOf(RuleAction.BLOCK_APP) }
    var start by remember { mutableIntStateOf(-1) }
    var end by remember { mutableIntStateOf(-1) }
    var days by remember { mutableIntStateOf(0x7F) }

    var detectorPkg by remember { mutableStateOf("") }
    var detectorSection by remember { mutableStateOf("") }
    var detectorType by remember { mutableStateOf(DetectorType.CLASS_NAME) }
    var markers by remember { mutableStateOf("") }

    var limitPkg by remember { mutableStateOf("") }
    var limitMinutes by remember { mutableIntStateOf(60) }
    var exception by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(stringResource(R.string.rules_card)) {
            OutlinedTextField(pkg, { pkg = it }, label = { Text(stringResource(R.string.package_hint)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(section, { section = it }, label = { Text(stringResource(R.string.section_hint)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                FilterChip(selected = true, onClick = {
                    action = RuleAction.entries[(action.ordinal + 1) % RuleAction.entries.size]
                }, label = { Text(action.name) })
                NumberField(start, { start = it }, stringResource(R.string.start_time), Modifier.weight(1f))
                NumberField(end, { end = it }, stringResource(R.string.end_time), Modifier.weight(1f))
            }
            Row(modifier = Modifier.padding(top = 8.dp)) {
                for (bit in 0 until 7) {
                    Checkbox(
                        checked = days and (1 shl bit) != 0,
                        onCheckedChange = { days = if (it) days or (1 shl bit) else days and (1 shl bit).inv() }
                    )
                }
            }
            Button(
                onClick = {
                    vm.saveRule(pkg, section, action, start, end, days)
                    pkg = ""; section = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.save_rule)) }

            rules.forEach { rule ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${rule.packageName} · ${rule.sectionId} · ${rule.action}", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { vm.deleteRule(rule.id) }) { Text(stringResource(R.string.delete)) }
                }
            }
        }

        SectionCard(stringResource(R.string.detectors_card)) {
            InfoLine(stringResource(R.string.detector_note))
            node?.let {
                InfoLine("pkg: ${it.packageName ?: "—"}\nclass: ${it.className ?: "—"}\nviewId: ${it.viewIdResourceName ?: "—"}\ntext: ${it.text ?: "—"}")
            } ?: InfoLine(stringResource(R.string.detector_wait))
            OutlinedTextField(detectorPkg, { detectorPkg = it }, label = { Text(stringResource(R.string.package_hint)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(detectorSection, { detectorSection = it }, label = { Text(stringResource(R.string.section_hint)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            FilterChip(
                selected = true,
                onClick = {
                    detectorType = DetectorType.entries[(detectorType.ordinal + 1) % DetectorType.entries.size]
                },
                label = { Text(detectorType.name) },
                modifier = Modifier.padding(top = 8.dp)
            )
            OutlinedTextField(
                markers,
                { markers = it },
                label = { Text(stringResource(R.string.markers_hint)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Button(
                onClick = {
                    val chosen = markers.ifBlank {
                        when (detectorType) {
                            DetectorType.CLASS_NAME -> node?.className.orEmpty()
                            DetectorType.VIEW_ID -> node?.viewIdResourceName.orEmpty()
                            DetectorType.TEXT -> node?.text.orEmpty()
                            DetectorType.CONTENT_DESC -> node?.contentDescription.orEmpty()
                        }
                    }
                    vm.saveDetector(detectorPkg.ifBlank { node?.packageName.orEmpty() }, detectorSection, detectorType, chosen)
                    detectorPkg = ""
                    detectorSection = ""
                    markers = ""
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text(stringResource(R.string.save_detector)) }
            detectors.forEach { detector ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${detector.packageName} · ${detector.sectionId} · ${detector.type}", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { vm.deleteDetector(detector.id) }) { Text(stringResource(R.string.delete)) }
                }
            }
        }

        SectionCard(stringResource(R.string.budgets_card)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(limitPkg, { limitPkg = it }, label = { Text(stringResource(R.string.package_hint)) }, modifier = Modifier.weight(1f))
                NumberField(limitMinutes, { limitMinutes = it }, stringResource(R.string.minute_short), Modifier.weight(0.6f))
            }
            Button(onClick = { vm.setDailyLimit(limitPkg, limitMinutes); limitPkg = "" }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.save_limit))
            }
            val today = vm.today()
            limits.filter { it.date == today }.forEach {
                InfoLine("${it.packageName}: ${it.usedMinutes}/${it.limitMinutes} ${stringResource(R.string.minute_short)}")
            }
        }

        SectionCard(stringResource(R.string.exceptions_card)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(exception, { exception = it }, label = { Text(stringResource(R.string.package_hint)) }, modifier = Modifier.weight(1f))
                Button(onClick = { vm.addException(exception); exception = "" }, modifier = Modifier.weight(0.6f)) {
                    Text(stringResource(R.string.add))
                }
            }
            settings.userExceptions.forEach { pkgName ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(pkgName, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { vm.removeException(pkgName) }) { Text(stringResource(R.string.delete)) }
                }
            }
        }
    }
}
