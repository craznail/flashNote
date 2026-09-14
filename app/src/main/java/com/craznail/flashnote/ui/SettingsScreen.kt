package com.craznail.flashnote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craznail.flashnote.BuildConfig
import com.craznail.flashnote.R
import com.craznail.flashnote.ui.theme.FlashBackground
import com.craznail.flashnote.ui.theme.FlashOnSurfaceMuted
import com.craznail.flashnote.ui.theme.FlashPrimary
import com.craznail.flashnote.ui.theme.FlashSuccess

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
        containerColor = FlashBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color(0xFF111827)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF111827),
                    navigationIconContentColor = Color(0xFF111827)
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Card 1: 本地摘要
            SettingsCard {
                SettingSwitchRow(
                    icon = Icons.Default.Description,
                    iconBg = FlashPrimary.copy(alpha = 0.12f),
                    iconTint = FlashPrimary,
                    title = stringResource(R.string.local_summary),
                    desc = stringResource(R.string.local_summary_desc),
                    checked = localSummaryEnabled,
                    onCheckedChange = onLocalSummaryChange
                )
            }

            Spacer(Modifier.height(12.dp))

            // Card 2: 模拟付费 / 订阅 → toggles simulatePremium
            SettingsCard {
                Column {
                    SettingSwitchRow(
                        icon = Icons.Default.WorkspacePremium,
                        iconBg = FlashPrimary.copy(alpha = 0.12f),
                        iconTint = FlashPrimary,
                        title = stringResource(R.string.simulate_premium),
                        desc = stringResource(R.string.simulate_premium_desc),
                        checked = simulatePremium,
                        onCheckedChange = onSimulatePremiumChange
                    )
                    if (!simulatePremium) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(FlashPrimary.copy(alpha = 0.08f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Diamond,
                                contentDescription = null,
                                tint = FlashPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.upgrade_to_pro),
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF111827),
                                    fontSize = 14.sp
                                )
                                Text(
                                    stringResource(R.string.simulate_premium_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FlashOnSurfaceMuted,
                                    maxLines = 1
                                )
                            }
                            Button(
                                onClick = { onSimulatePremiumChange(true) },
                                colors = ButtonDefaults.buttonColors(containerColor = FlashPrimary),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 12.dp,
                                    vertical = 6.dp
                                )
                            ) {
                                Text(stringResource(R.string.go_subscribe), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Card 3: 远端 AI 概要
            SettingsCard {
                Column(Modifier.alpha(if (simulatePremium) 1f else 0.55f)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBox(
                            icon = Icons.Default.Cloud,
                            bg = if (simulatePremium) {
                                FlashPrimary.copy(alpha = 0.12f)
                            } else {
                                Color(0xFFE5E7EB)
                            },
                            tint = if (simulatePremium) FlashPrimary else Color(0xFF9CA3AF)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                stringResource(R.string.remote_ai),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF111827)
                            )
                            Text(
                                stringResource(R.string.remote_ai_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = FlashOnSurfaceMuted,
                                maxLines = 2
                            )
                        }
                        if (simulatePremium) {
                            Switch(
                                checked = remoteAiEnabled,
                                onCheckedChange = onRemoteAiChange,
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = FlashPrimary
                                )
                            )
                        } else {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFFE5E7EB))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF9CA3AF),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.remote_ai_locked),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF6B7280)
                                )
                            }
                        }
                    }
                    if (!simulatePremium) {
                        Spacer(Modifier.height(12.dp))
                        FeatureBullet(stringResource(R.string.remote_ai_feature_1))
                        Spacer(Modifier.height(4.dp))
                        FeatureBullet(stringResource(R.string.remote_ai_feature_2))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Card 4: 电池无限制
            SettingsCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenBatterySettings),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBox(
                        icon = Icons.Default.BatteryChargingFull,
                        bg = FlashSuccess.copy(alpha = 0.12f),
                        tint = FlashSuccess
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            stringResource(R.string.battery_unrestricted),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF111827)
                        )
                        Text(
                            stringResource(R.string.battery_unrestricted_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = FlashOnSurfaceMuted,
                            maxLines = 2
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFF9CA3AF)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9CA3AF),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun IconBox(icon: ImageVector, bg: Color, tint: Color) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
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
        IconBox(icon = icon, bg = iconBg, tint = iconTint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111827)
            )
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = FlashOnSurfaceMuted,
                maxLines = 2
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = FlashPrimary)
        )
    }
}

@Composable
private fun FeatureBullet(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("✓", color = FlashSuccess, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = FlashOnSurfaceMuted
        )
    }
}
