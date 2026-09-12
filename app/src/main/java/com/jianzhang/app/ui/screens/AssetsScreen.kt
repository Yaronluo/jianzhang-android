package com.jianzhang.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jianzhang.app.data.local.entity.AccountEntity
import com.jianzhang.app.data.local.entity.AccountType
import com.jianzhang.app.ui.FinanceUiState
import com.jianzhang.app.ui.components.AccountRow
import com.jianzhang.app.ui.components.AppCard
import com.jianzhang.app.ui.components.EmptyState
import com.jianzhang.app.ui.components.SectionTitle
import com.jianzhang.app.util.formatMoney
import com.jianzhang.app.util.formatInputMoney

@Composable
fun AssetsScreen(
    state: FinanceUiState,
    onCreateAccount: (String, AccountType, String, String, Boolean) -> Unit,
    onUpdateAccount: (AccountEntity, String, AccountType, String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onAdjustBalance: (AccountEntity, String) -> Unit,
    onTransfer: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var adjustingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<AccountEntity?>(null) }
    val totalAssets = state.accounts.filter { !it.isLiability }.sumOf { it.balanceInCents.coerceAtLeast(0L) }
    val totalLiabilities = state.accounts.filter { it.isLiability }.sumOf { (-it.balanceInCents).coerceAtLeast(0L) }

    if (showDialog) {
        AccountDialog(
            existing = editingAccount,
            onDismiss = { showDialog = false; editingAccount = null },
            onSave = { existing, name, type, balance, icon ->
                if (existing == null) onCreateAccount(name, type, balance, icon, type == AccountType.CREDIT_CARD)
                else onUpdateAccount(existing, name, type, icon)
                showDialog = false
                editingAccount = null
            }
        )
    }
    pendingDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("停用“${account.name}”？") },
            text = { Text("有历史账单的账户会被保留但不再出现在新增账单列表中。") },
            confirmButton = {
                TextButton(onClick = { onRemoveAccount(account.id); pendingDelete = null }) { Text("确认", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }
    adjustingAccount?.let { account ->
        BalanceAdjustmentDialog(
            account = account,
            onDismiss = { adjustingAccount = null },
            onSave = { balance ->
                onAdjustBalance(account, balance)
                adjustingAccount = null
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("资产", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("看见每一笔交易如何影响你的钱包", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onTransfer) { Icon(Icons.Default.SwapHoriz, contentDescription = "账户转账") }
                    IconButton(onClick = { editingAccount = null; showDialog = true }) { Icon(Icons.Default.Add, contentDescription = "新增账户") }
                }
            }
        }
        item {
            AppCard {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("净资产", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney(totalAssets - totalLiabilities), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (totalAssets >= totalLiabilities) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("总资产", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatMoney(totalAssets), fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text("总负债", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatMoney(totalLiabilities), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        item { SectionTitle("账户列表") }
        if (state.accounts.isEmpty()) {
            item { EmptyState("👛", "还没有账户", "创建现金、支付宝或银行卡，开始建立你的资产视图") }
        } else {
            item {
                AppCard {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                        state.accounts.forEach { account ->
                            AccountRow(
                                account = account,
                                onEdit = { editingAccount = account; showDialog = true },
                                onAdjust = { adjustingAccount = account },
                                onDelete = { pendingDelete = account }
                            )
                        }
                    }
                }
            }
        }
        item {
            Button(onClick = { editingAccount = null; showDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("  新增账户")
            }
        }
    }
}

@Composable
private fun AccountDialog(
    existing: AccountEntity?,
    onDismiss: () -> Unit,
    onSave: (AccountEntity?, String, AccountType, String, String) -> Unit
) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var balance by rememberSaveable(existing?.id) { mutableStateOf(existing?.let { formatInputMoney(kotlin.math.abs(it.balanceInCents)) } ?: "0") }
    var typeName by rememberSaveable(existing?.id) { mutableStateOf(existing?.type?.name ?: AccountType.CASH.name) }
    var error by remember { mutableStateOf("") }
    val type = runCatching { AccountType.valueOf(typeName) }.getOrDefault(AccountType.CASH)
    val options = listOf(
        AccountType.CASH to ("现金" to "💵"),
        AccountType.ALIPAY to ("支付宝" to "🟦"),
        AccountType.WECHAT to ("微信" to "🟩"),
        AccountType.BANK_CARD to ("银行卡" to "🏦"),
        AccountType.CREDIT_CARD to ("信用卡" to "💳")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新增账户" else "编辑账户") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("账户名称") }, placeholder = { Text("例如：支付宝") })
                Text("账户类型", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.take(3).forEach { (option, display) -> FilterChip(selected = type == option, onClick = { typeName = option.name }, label = { Text(display.first) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.drop(3).forEach { (option, display) -> FilterChip(selected = type == option, onClick = { typeName = option.name }, label = { Text(display.first) }) }
                }
                OutlinedTextField(value = balance, onValueChange = { balance = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, readOnly = existing != null, label = { Text(if (existing != null) "当前余额" else if (type == AccountType.CREDIT_CARD) "初始欠款" else "初始余额") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (existing != null) Text("编辑账户不会改变余额；请在账户列表中使用“调整余额”。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = if (name.isBlank()) "请填写账户名称" else ""
                if (error.isBlank()) onSave(existing, name, type, balance, options.firstOrNull { it.first == type }?.second?.second ?: "💳")
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun BalanceAdjustmentDialog(
    account: AccountEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var balance by rememberSaveable(account.id) { mutableStateOf(formatInputMoney(kotlin.math.abs(account.balanceInCents))) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调整${account.name}余额") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("用于校正实际余额，不会新增一笔收入或支出。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it; error = "" },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(if (account.isLiability) "当前欠款" else "当前余额") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = error.isNotBlank()
                )
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val raw = balance.trim().replace("¥", "").replace(",", "").replace("元", "").replace("块", "")
                val valid = runCatching { raw.toBigDecimal() }.getOrNull()?.let { it >= java.math.BigDecimal.ZERO } == true
                if (valid) onSave(balance) else error = "请输入不小于 0 的金额"
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
