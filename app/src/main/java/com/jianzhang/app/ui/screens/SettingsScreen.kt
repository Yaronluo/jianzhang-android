package com.jianzhang.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jianzhang.app.domain.ai.AiReceiptSettings
import com.jianzhang.app.ui.components.AppCard
import com.jianzhang.app.ui.components.BackTitleBar

@Composable
fun SettingsScreen(
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
    var showAbout by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
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

    Column(modifier = Modifier.fillMaxSize()) {
        BackTitleBar(title = "设置", onBack = onBack)
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AppCard {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("简账·慧录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("本地识别或自定义 AI，核对后记账。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Text("数据与账户", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp))
                AppCard(modifier = Modifier.padding(top = 8.dp)) {
                    Column {
                        ListItem(
                            headlineContent = { Text("账户管理") },
                            supportingContent = { Text("创建、停用现金和各类支付账户") },
                            leadingContent = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            trailingContent = { TextButton(onClick = onOpenAssets) { Text("查看") } }
                        )
                        ListItem(
                            headlineContent = { Text("导出账单") },
                            supportingContent = { Text("生成 CSV 文本，可分享到文件或表格应用") },
                            leadingContent = { Text("↗️") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingContent = {
                                TextButton(onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_SUBJECT, "简账数据导出")
                                        putExtra(Intent.EXTRA_TEXT, exportText())
                                    }
                                    context.startActivity(Intent.createChooser(intent, "导出简账数据"))
                                }) { Text("导出") }
                            }
                        )
                    }
                }
            }
            item {
                Text("AI 识票", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp))
                AppCard(modifier = Modifier.padding(top = 8.dp)) {
                    ListItem(
                        headlineContent = { Text("AI 服务管理") },
                        supportingContent = {
                            val active = aiSettings.activeProfile
                            Text(
                                when {
                                    !aiSettings.enabled -> "未启用 · 已保存 ${aiSettings.profiles.size} 个服务"
                                    active == null -> "尚未选择服务"
                                    !active.isConfigured -> "${active.name} 配置不完整"
                                    else -> "正在使用 ${active.name} · 已保存 ${aiSettings.profiles.size} 个服务"
                                }
                            )
                        },
                        leadingContent = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingContent = { TextButton(onClick = onOpenAiSettings) { Text("管理") } }
                    )
                }
            }
            item {
                Text("外观", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp))
                AppCard(modifier = Modifier.padding(top = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("主题模式", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (mode, label) ->
                                FilterChip(selected = themeMode == mode, onClick = { onSetThemeMode(mode) }, label = { Text(label) })
                            }
                        }
                    }
                }
            }
            item {
                Text("演示数据", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp))
                AppCard(modifier = Modifier.padding(top = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("用于比赛现场快速展示账本、资产、图表和财务洞察。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = onGenerateDemo) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Text(" 生成演示数据")
                            }
                            OutlinedButton(onClick = { showClearConfirm = true }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                Text(" 清除")
                            }
                        }
                    }
                }
            }
            item {
                Text("关于", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp))
                AppCard(modifier = Modifier.padding(top = 8.dp)) {
                    ListItem(
                        headlineContent = { Text("关于简账·慧录") },
                        supportingContent = { Text("版本 1.0 · 本地识别与自定义 AI") },
                        leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingContent = { TextButton(onClick = { showAbout = true }) { Text("查看") } }
                    )
                }
            }
        }
    }
}
