package com.assem.copyapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assem.copyapp.MainViewModel
import com.assem.copyapp.R
import com.assem.copyapp.domain.SessionMode

@Composable
fun OnboardingScreen(vm: MainViewModel) {
    var mode by remember { mutableStateOf(SessionMode.SOFT) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(stringResource(R.string.onboarding_body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        FilterChip(
            selected = mode == SessionMode.SOFT,
            onClick = { mode = SessionMode.SOFT },
            label = { Text(stringResource(R.string.mode_soft)) }
        )
        FilterChip(
            selected = mode == SessionMode.HARD,
            onClick = { mode = SessionMode.HARD },
            label = { Text(stringResource(R.string.mode_hard)) }
        )
        Text(stringResource(R.string.onboarding_mode_note), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Button(onClick = {
            vm.updateSettings { copy(onboardingDone = true, defaultMode = mode) }
        }) { Text(stringResource(R.string.onboarding_start)) }
    }
}
