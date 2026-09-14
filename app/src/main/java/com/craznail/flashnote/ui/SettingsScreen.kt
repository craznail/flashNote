package com.craznail.flashnote.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craznail.flashnote.BuildConfig
import com.craznail.flashnote.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    localSummaryEnabled: Boolean,
    simulatePremium: Boolean,
    remoteAiEnabled: Boolean,
    onLocalSummaryChange: (Boolean) -> Unit,
    onSimulatePremiumChange: (Boolean) -> Unit,
    onRemoteAiChange: (Boolean) -> Unit,
    onOpenBatterySettings: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            SettingSwitchRow(
                title = stringResource(R.string.local_summary),
                desc = stringResource(R.string.local_summary_desc),
                checked = localSummaryEnabled,
                onCheckedChange = onLocalSummaryChange
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SettingSwitchRow(
                title = stringResource(R.string.simulate_premium),
                desc = stringResource(R.string.simulate_premium_desc),
                checked = simulatePremium,
                onCheckedChange = onSimulatePremiumChange
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SettingSwitchRow(
                title = stringResource(R.string.remote_ai),
                desc = if (simulatePremium) {
                    stringResource(R.string.remote_ai_desc)
                } else {
                    stringResource(R.string.remote_ai_locked)
                },
                checked = remoteAiEnabled && simulatePremium,
                enabled = simulatePremium,
                onCheckedChange = onRemoteAiChange
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(
                text = stringResource(R.string.premium_stub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(
                stringResource(R.string.battery_unrestricted),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.battery_unrestricted_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            OutlinedButton(
                onClick = onOpenBatterySettings,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.open_battery_settings))
            }
            Text(
                text = stringResource(
                    R.string.app_version,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
