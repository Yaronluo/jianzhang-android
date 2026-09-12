package com.jianzhang.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
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
        Text("简账", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(
            "把每一笔钱都变成看得懂的选择",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppCard {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("从记下第一笔钱开始", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "简账把账单、账户余额、固定支出和消费分析放在一起。核心数据保存在本机，不联网也能使用。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FeatureLine(Icons.Default.AccountBalanceWallet, "账单和资产余额自动联动")
                FeatureLine(Icons.Default.CalendarMonth, "固定支出到期提醒，不再漏记")
                FeatureLine(Icons.Default.Analytics, "预算、趋势和财务洞察一目了然")
            }
        }
        Text("先创建一个常用账户？", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("也可以先跳过，之后在资产页面添加。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AccountChoice("💵", "现金", "适合记录手头现金", onClick = { onCreateAccount("现金", AccountType.CASH, "💵") })
        AccountChoice("🟦", "支付宝", "适合日常线上消费", onClick = { onCreateAccount("支付宝", AccountType.ALIPAY, "🟦") })
        AccountChoice("🏦", "银行卡", "适合记录银行卡余额", onClick = { onCreateAccount("银行卡", AccountType.BANK_CARD, "🏦") })
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
private fun AccountChoice(icon: String, name: String, description: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Text(icon, style = MaterialTheme.typography.titleLarge)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp), horizontalAlignment = Alignment.Start) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("开始", color = MaterialTheme.colorScheme.primary)
    }
}
