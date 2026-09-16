package com.jianzhang.app.ui.screens

import android.content.Intent
import android.app.StatusBarManager
import android.content.ComponentName
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jianzhang.app.domain.ai.AiReceiptSettings
import com.jianzhang.app.domain.ai.AiRecognitionMode
import com.jianzhang.app.quickcapture.QuickCaptureAccessibilityService
import com.jianzhang.app.quickcapture.QuickCaptureAccountPolicy
import com.jianzhang.app.quickcapture.QuickCapturePreferences
import com.jianzhang.app.quickcapture.QuickCaptureTileService
import com.jianzhang.app.ui.FinanceUiState
import com.jianzhang.app.ui.components.AppCard
import com.jianzhang.app.ui.components.BackTitleBar

@Composable
fun SettingsScreen(
    state: FinanceUiState,
    onBack: () -> Unit,
    onGenerateDemo: () -> Unit,
    onClearDemo: () -> Unit,
    onOpenAssets: () -> Unit,
    onOpenAiSettings: () -> Unit,
    themeMode: String,
    onSetThemeMode: (String) -> Unit,
    aiSettings: AiReceiptSettings,
    exportText: () -> String
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showAbout by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var accessibilityRefresh by remember { mutableStateOf(0) }
    val quickCapturePreferences = remember(context) { QuickCapturePreferences(context) }
    var defaultAccountId by remember { mutableStateOf(quickCapturePreferences.defaultAccountId) }
    val usableAccounts = QuickCaptureAccountPolicy.defaultCandidates(state.accounts)
    LaunchedEffect(defaultAccountId, usableAccounts.map { it.id }) {
        if (defaultAccountId != null && usableAccounts.none { it.id == defaultAccountId }) {
            // A deleted, disabled, or demo account must not remain selected
            // after the account list changes.
            defaultAccountId = null
            quickCapturePreferences.defaultAccountId = null
        }
    }
    val accessibilityEnabled = remember(accessibilityRefresh) { QuickCaptureAccessibilityService.isEnabled(context) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) accessibilityRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于简账·慧录") },
            text = { Text("简账·慧录支持本地票据识别，也允许用户配置自己的 AI 服务。\n\n只有启用云端 AI 后，用户主动选择的图片或 OCR 文字才会发送到所配置的服务商；应用不读取微信、支付宝账户，所有结果均需核对后入账。") },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("知道了") } }
        )
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清除演示数据？") },
            text = { Text("只会删除由“生成演示数据”创建的账单、账户、固定支出和预算，不会删除真实数据。") },
            confirmButton = {
                TextButton(onClick = { onClearDemo(); showClearConfirm = false }) { Text("清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } }
        )
    }
    if (showAccountPicker) {
        AlertDialog(
            onDismissRequest = { showAccountPicker = false },
            title = { Text("选择默认记账账户") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "仅显示资产页中已启用、可用于记账的真实账户；演示账户不会用于一键直记。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (usableAccounts.isEmpty()) {
                        Text("请先在资产页添加或启用一个可用于记账的账户。")
                    } else {
                        usableAccounts.forEach { account ->
                            TextButton(
                                onClick = {
                                    defaultAccountId = account.id
                                    quickCapturePreferences.defaultAccountId = account.id
                                    showAccountPicker = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (account.id == defaultAccountId) "✓ ${account.name}" else account.name) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAccountPicker = false }) { Text("关闭") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTitleBar(title = "设置", onBack = onBack)
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                AppCard(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(54.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(28.dp))
                            }
                        }
                        Column(
                            modifier = Modifier.padding(start = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text("简账·慧录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "让每一笔收支，都清楚又安心",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                SettingsSectionTitle("数据与账户")
                AppCard(modifier = Modifier.padding(top = 10.dp)) {
                    Column {
                        SettingsActionRow(
                            icon = Icons.Default.AccountBalanceWallet,
                            title = "账户管理",
                            subtitle = "管理现金、银行卡与支付账户",
                            actionLabel = "查看",
                            onClick = onOpenAssets
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                        SettingsActionRow(
                            icon = Icons.Default.FileUpload,
                            title = "导出账单",
                            subtitle = "生成 CSV，可分享至文件或表格应用",
                            actionLabel = "导出",
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_SUBJECT, "简账数据导出")
                                    putExtra(Intent.EXTRA_TEXT, exportText())
                                }
                                context.startActivity(Intent.createChooser(intent, "导出简账数据"))
                            }
                        )
                    }
                }
            }
            item {
                SettingsSectionTitle("AI 识票")
                AppCard(modifier = Modifier.padding(top = 10.dp)) {
                    val active = aiSettings.activeProfile
                    SettingsActionRow(
                        icon = Icons.Default.AutoAwesome,
                        title = "AI 服务管理",
                        subtitle = when {
                            !aiSettings.enabled -> "未启用 · 已保存 ${aiSettings.profiles.size} 个服务"
                            active == null -> "尚未选择服务"
                            !active.isConfigured -> "${active.name} 配置不完整"
                            else -> "正在使用 ${active.name}"
                        },
                        actionLabel = "管理",
                        onClick = onOpenAiSettings
                    )
                }
            }
            item {
                SettingsSectionTitle("一键慧录")
                AppCard(modifier = Modifier.padding(top = 10.dp)) {
                    Column {
                        SettingsActionRow(
                            icon = Icons.Default.TouchApp,
                            title = "无障碍截屏服务",
                            subtitle = if (accessibilityEnabled) "已开启 · 仅点击磁贴时截屏" else "未开启 · 需要允许截屏与临时卡片",
                            actionLabel = if (accessibilityEnabled) "已开启" else "开启",
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                        SettingsActionRow(
                            icon = Icons.Default.AutoAwesome,
                            title = "当前识别模式",
                            subtitle = when (aiSettings.mode) {
                                AiRecognitionMode.VISION -> "AI"
                                AiRecognitionMode.OCR_TEXT -> "AI+OCR"
                                AiRecognitionMode.LOCAL -> "OCR"
                            },
                            actionLabel = "管理",
                            onClick = onOpenAiSettings
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                        SettingsActionRow(
                            icon = Icons.Default.Wallet,
                            title = "默认记账账户",
                            subtitle = usableAccounts.firstOrNull { it.id == defaultAccountId }?.name ?: "未设置，识别后需进入核对页",
                            actionLabel = "选择",
                            onClick = { showAccountPicker = true }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                                            ComponentName(context, QuickCaptureTileService::class.java),
                                            "截屏记账",
                                            android.graphics.drawable.Icon.createWithResource(context, com.jianzhang.app.R.drawable.ic_quick_capture_tile),
                                            context.mainExecutor
                                        ) { result ->
                                            Toast.makeText(context, if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) "已添加到控制中心" else "请在控制中心编辑页手动添加", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "请下拉控制中心，点击编辑并添加“截屏记账”", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("添加到控制中心") }
                            OutlinedButton(
                                onClick = {
                                    val service = QuickCaptureAccessibilityService.connectedInstance
                                    if (service != null) service.showTestCard()
                                    else Toast.makeText(context, "请先开启无障碍截屏服务", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("测试识别卡片") }
                        }
                    }
                }
            }
            item {
                SettingsSectionTitle("外观")
                AppCard(modifier = Modifier.padding(top = 10.dp)) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SettingsIcon(Icons.Default.Palette)
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text("主题模式", fontWeight = FontWeight.SemiBold)
                                Text("选择更适合你的显示方式", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (mode, label) ->
                                val selected = themeMode == mode
                                Surface(
                                    onClick = { onSetThemeMode(mode) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ) {
                                    Text(
                                        label,
                                        modifier = Modifier.padding(vertical = 11.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                SettingsSectionTitle("演示数据")
                AppCard(modifier = Modifier.padding(top = 10.dp)) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("快速生成一套账本、资产与图表数据，方便现场展示。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = onGenerateDemo, modifier = Modifier.weight(1.35f).height(50.dp), shape = RoundedCornerShape(16.dp)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Text(" 生成演示数据")
                            }
                            OutlinedButton(onClick = { showClearConfirm = true }, modifier = Modifier.weight(0.75f).height(50.dp), shape = RoundedCornerShape(16.dp)) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                Text(" 清除")
                            }
                        }
                    }
                }
            }
            item {
                SettingsSectionTitle("关于")
                AppCard(modifier = Modifier.padding(top = 10.dp, bottom = 20.dp)) {
                    SettingsActionRow(
                        icon = Icons.Default.Info,
                        title = "关于简账·慧录",
                        subtitle = "版本 1.0 · 本地识别与自定义 AI",
                        actionLabel = "查看",
                        onClick = { showAbout = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun SettingsIcon(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.padding(start = 5.dp).size(13.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
    }
}
