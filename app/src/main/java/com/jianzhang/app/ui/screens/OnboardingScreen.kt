package com.jianzhang.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jianzhang.app.data.local.entity.AccountType
import com.jianzhang.app.ui.components.AppCard
import com.jianzhang.app.ui.components.AccountTypeIcon

@Composable
fun OnboardingScreen(
    onSkip: () -> Unit,
    onCreateAccount: (String, AccountType, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("简记·慧屏", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(
            "AI截图行动助手",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppCard {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("一张截图，让信息直接变成行动", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "点击控制中心的“AI截图助手”，系统会在你确认后记账、固定取餐码和取件码，或生成出行卡片。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FeatureLine(Icons.Default.AutoAwesome, "一张截图可以同时识别多个用途")
                FeatureLine(Icons.Default.AccountBalanceWallet, "账单和资产余额自动联动")
                FeatureLine(Icons.Default.CalendarMonth, "固定支出到期提醒，不再漏记")
                FeatureLine(Icons.Default.Analytics, "预算、趋势和财务洞察一目了然")
            }
        }
        Text("先创建一个常用账户？", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("也可以先跳过，之后在资产页面添加。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AccountChoice(AccountType.CASH, "现金", "适合记录手头现金", onClick = { onCreateAccount("现金", AccountType.CASH, "💵") })
        AccountChoice(AccountType.ALIPAY, "支付宝", "适合日常线上消费", onClick = { onCreateAccount("支付宝", AccountType.ALIPAY, "🟦") })
        AccountChoice(AccountType.BANK_CARD, "银行卡", "适合记录银行卡余额", onClick = { onCreateAccount("银行卡", AccountType.BANK_CARD, "🏦") })
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 12.dp)) {
            Text("跳过，稍后设置")
        }
    }
}

@Composable
private fun FeatureLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text)
    }
}

@Composable
private fun AccountChoice(type: AccountType, name: String, description: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        AccountTypeIcon(
            type = type,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = name
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp), horizontalAlignment = Alignment.Start) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("开始", color = MaterialTheme.colorScheme.primary)
    }
}
