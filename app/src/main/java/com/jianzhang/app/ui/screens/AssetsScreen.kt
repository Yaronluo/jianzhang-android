package com.jianzhang.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jianzhang.app.data.local.entity.AccountEntity
import com.jianzhang.app.data.local.entity.AccountType
import com.jianzhang.app.ui.FinanceUiState
import com.jianzhang.app.ui.components.AppCard
import com.jianzhang.app.ui.components.AssetCardRow
import com.jianzhang.app.ui.components.CollapsingTitleHeader
import com.jianzhang.app.ui.components.EmptyState
import com.jianzhang.app.util.formatInputMoney
import com.jianzhang.app.util.formatMoney

private enum class AssetFilter(val label: String) {
    FUNDS("资金"),
    CREDIT("信用"),
    RECHARGE("充值"),
    INVESTMENT("理财")
}

private fun AccountEntity.assetFilter(): AssetFilter = when (type) {
    AccountType.CREDIT_CARD -> AssetFilter.CREDIT
    AccountType.ALIPAY, AccountType.WECHAT -> AssetFilter.RECHARGE
    AccountType.OTHER -> AssetFilter.INVESTMENT
    AccountType.CASH, AccountType.BANK_CARD -> AssetFilter.FUNDS
}

@Composable
fun AssetsScreen(
    state: FinanceUiState,
    openAdd: Boolean,
    onAddConsumed: () -> Unit,
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
    var openSwipeAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var filterName by rememberSaveable { mutableStateOf(AssetFilter.FUNDS.name) }

    val selectedFilter = AssetFilter.values().firstOrNull { it.name == filterName } ?: AssetFilter.FUNDS
    val visibleAccounts = state.accounts.filter { it.assetFilter() == selectedFilter }
    val totalAssets = state.accounts.sumOf { it.balanceInCents.coerceAtLeast(0L) }
    val totalLiabilities = state.accounts.sumOf { (-it.balanceInCents).coerceAtLeast(0L) }
    val netWorth = totalAssets - totalLiabilities

    LaunchedEffect(openAdd) {
        if (openAdd) {
            editingAccount = null
            showDialog = true
            onAddConsumed()
        }
    }

    if (showDialog) {
        AccountDialog(
            existing = editingAccount,
            onDismiss = { showDialog = false; editingAccount = null },
            onSave = { existing, name, type, balance, icon ->
                if (existing == null) {
                    onCreateAccount(name, type, balance, icon, type == AccountType.CREDIT_CARD)
                } else {
                    onUpdateAccount(existing, name, type, icon)
                }
                showDialog = false
                editingAccount = null
            }
        )
    }
    pendingDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("停用“${account.name}”？") },
            text = { Text("有历史账单的资产会被保留，但不再出现在新增账单列表中。") },
            confirmButton = {
                TextButton(onClick = { onRemoveAccount(account.id); pendingDelete = null }) {
                    Text("确认", color = MaterialTheme.colorScheme.error)
                }
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

    val listState = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val headerCollapseDistancePx = with(density) { 68.dp.toPx() }
    val floatingControlsClearance = navigationBarInset + 12.dp + 64.dp + 16.dp + 56.dp + 12.dp
    val headerCollapseProgress by remember {
        derivedStateOf {
            val scrollPx = if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                headerCollapseDistancePx
            }
            (scrollPx / headerCollapseDistancePx).coerceIn(0f, 1f)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = statusBarInset + 96.dp,
                end = 20.dp,
                bottom = floatingControlsClearance
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "net-worth") {
                NetWorthCard(
                    netWorth = netWorth,
                    totalAssets = totalAssets,
                    totalLiabilities = totalLiabilities
                )
            }
            item(key = "filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssetFilter.values().forEach { option ->
                        val selected = selectedFilter == option
                        Surface(
                            onClick = {
                                filterName = option.name
                                openSwipeAccountId = null
                            },
                            shape = RoundedCornerShape(50),
                            color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            contentColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(
                                option.label,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
            if (visibleAccounts.isEmpty()) {
                item(key = "empty-assets") {
                    EmptyState(
                        icon = if (state.accounts.isEmpty()) "👛" else "🔎",
                        title = if (state.accounts.isEmpty()) "还没有资产" else "暂无${selectedFilter.label}资产",
                        message = if (state.accounts.isEmpty()) "点击下方“+”添加现金、银行卡或其他资产" else "试试切换其他资产分类"
                    )
                }
            } else {
                items(visibleAccounts, key = { it.id }) { account ->
                    AssetCardRow(
                        account = account,
                        onEdit = {
                            openSwipeAccountId = null
                            editingAccount = account
                            showDialog = true
                        },
                        onDelete = {
                            openSwipeAccountId = null
                            pendingDelete = account
                        },
                        onClick = {
                            openSwipeAccountId = null
                            adjustingAccount = account
                        },
                        isSwipeOpen = openSwipeAccountId == account.id,
                        onSwipeOpen = { openSwipeAccountId = account.id },
                        onSwipeClose = {
                            if (openSwipeAccountId == account.id) openSwipeAccountId = null
                        }
                    )
                }
            }
            item(key = "bottom-space") { Spacer(modifier = Modifier.height(12.dp)) }
        }
        CollapsingTitleHeader(
            title = "资产",
            collapseProgress = headerCollapseProgress,
            action = {
                Surface(
                    onClick = onTransfer,
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "账户转账", modifier = Modifier.size(24.dp))
                    }
                }
            }
        )
    }
}

@Composable
private fun NetWorthCard(netWorth: Long, totalAssets: Long, totalLiabilities: Long) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                "净资产",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                formatMoney(netWorth),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (netWorth >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                NetWorthMetric(
                    "总资产",
                    formatMoney(totalAssets),
                    MaterialTheme.colorScheme.primary,
                    Modifier.weight(1f)
                )
                NetWorthMetric(
                    "总负债",
                    formatMoney(totalLiabilities),
                    MaterialTheme.colorScheme.error,
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NetWorthMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.72f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun AccountDialog(
    existing: AccountEntity?,
    onDismiss: () -> Unit,
    onSave: (AccountEntity?, String, AccountType, String, String) -> Unit
) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var balance by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.let { formatInputMoney(kotlin.math.abs(it.balanceInCents)) } ?: "0")
    }
    var typeName by rememberSaveable(existing?.id) { mutableStateOf(existing?.type?.name ?: AccountType.CASH.name) }
    var error by remember { mutableStateOf("") }
    val type = runCatching { AccountType.valueOf(typeName) }.getOrDefault(AccountType.CASH)
    val options = listOf(
        AccountType.CASH to ("现金" to "💵"),
        AccountType.ALIPAY to ("支付宝" to "🟦"),
        AccountType.WECHAT to ("微信" to "🟩"),
        AccountType.BANK_CARD to ("银行卡" to "🏦"),
        AccountType.CREDIT_CARD to ("信用卡" to "💳"),
        AccountType.OTHER to ("理财" to "📈")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "添加资产" else "编辑资产") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("资产名称") },
                    placeholder = { Text("例如：工商银行") }
                )
                Text("资产类型", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.take(3).forEach { (option, display) ->
                        FilterChip(
                            selected = type == option,
                            onClick = { typeName = option.name },
                            label = { Text(display.first) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.drop(3).forEach { (option, display) ->
                        FilterChip(
                            selected = type == option,
                            onClick = { typeName = option.name },
                            label = { Text(display.first) }
                        )
                    }
                }
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    readOnly = existing != null,
                    label = {
                        Text(
                            if (existing != null) "当前余额"
                            else if (type == AccountType.CREDIT_CARD) "初始欠款"
                            else "初始余额"
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (existing != null) {
                    Text(
                        "编辑资产不会改变余额；点击资产卡片可调整余额。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = if (name.isBlank()) "请填写资产名称" else ""
                if (error.isBlank()) {
                    onSave(existing, name, type, balance, options.firstOrNull { it.first == type }?.second?.second ?: "💼")
                }
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
    var balance by rememberSaveable(account.id) {
        mutableStateOf(formatInputMoney(kotlin.math.abs(account.balanceInCents)))
    }
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
