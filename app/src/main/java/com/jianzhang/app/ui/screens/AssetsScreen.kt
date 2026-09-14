package com.jianzhang.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.jianzhang.app.util.parseMoneyToCents
import com.jianzhang.app.util.parseMoneyToCentsOrZero

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

private data class AccountTypeOption(
    val type: AccountType,
    val label: String,
    val legacyIcon: String
)

private val accountTypeOptions = listOf(
    AccountTypeOption(AccountType.CASH, "现金", "💵"),
    AccountTypeOption(AccountType.BANK_CARD, "银行卡", "🏦"),
    AccountTypeOption(AccountType.ALIPAY, "支付宝", "🟦"),
    AccountTypeOption(AccountType.WECHAT, "微信", "🟩"),
    AccountTypeOption(AccountType.CREDIT_CARD, "信用卡", "💳"),
    AccountTypeOption(AccountType.OTHER, "理财", "📈")
)

private val AssetEditorAccent = Color(0xFFFFC107)

private fun accountTypeIcon(type: AccountType): ImageVector = when (type) {
    AccountType.CASH, AccountType.BANK_CARD -> Icons.Outlined.AccountBalance
    AccountType.ALIPAY, AccountType.WECHAT -> Icons.Outlined.AccountBalanceWallet
    AccountType.CREDIT_CARD -> Icons.Outlined.CreditCard
    AccountType.OTHER -> Icons.AutoMirrored.Outlined.ShowChart
}

@Composable
fun AssetsScreen(
    state: FinanceUiState,
    openAdd: Boolean,
    onAddConsumed: () -> Unit,
    onCreateAccount: (String, AccountType, String, String, Boolean, String, String, Boolean, Boolean) -> Unit,
    onUpdateAccount: (AccountEntity, String, AccountType, String, String, String, Boolean, Boolean) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onAdjustBalance: (AccountEntity, String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var adjustingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<AccountEntity?>(null) }
    var openSwipeAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var filterName by rememberSaveable { mutableStateOf(AssetFilter.FUNDS.name) }

    val selectedFilter = AssetFilter.values().firstOrNull { it.name == filterName } ?: AssetFilter.FUNDS
    val visibleAccounts = state.accounts.filter { it.assetFilter() == selectedFilter }
    val netWorthAccounts = state.accounts.filter { it.includeInNetWorth }
    val totalAssets = netWorthAccounts.sumOf { it.balanceInCents.coerceAtLeast(0L) }
    val totalLiabilities = netWorthAccounts.sumOf { (-it.balanceInCents).coerceAtLeast(0L) }
    val netWorth = totalAssets - totalLiabilities

    LaunchedEffect(openAdd) {
        if (openAdd) {
            editingAccount = null
            showDialog = true
            onAddConsumed()
        }
    }

    if (showDialog) {
        AccountEditorDialog(
            existing = editingAccount,
            onDismiss = { showDialog = false; editingAccount = null },
            onSave = { existing, name, type, balance, icon, currencyCode, note, includeInNetWorth, canUseForTransactions ->
                if (existing == null) {
                    onCreateAccount(
                        name,
                        type,
                        balance,
                        icon,
                        type == AccountType.CREDIT_CARD,
                        currencyCode,
                        note,
                        includeInNetWorth,
                        canUseForTransactions
                    )
                } else {
                    onUpdateAccount(
                        existing,
                        name,
                        type,
                        icon,
                        currencyCode,
                        note,
                        includeInNetWorth,
                        canUseForTransactions
                    )
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
            collapseProgress = headerCollapseProgress
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
private fun AccountEditorDialog(
    existing: AccountEntity?,
    onDismiss: () -> Unit,
    onSave: (AccountEntity?, String, AccountType, String, String, String, String, Boolean, Boolean) -> Unit
) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var balance by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.let { formatInputMoney(kotlin.math.abs(it.balanceInCents)) } ?: "0")
    }
    var typeName by rememberSaveable(existing?.id) { mutableStateOf(existing?.type?.name ?: AccountType.CASH.name) }
    var currencyCode by rememberSaveable(existing?.id) { mutableStateOf(existing?.currencyCode ?: "CNY") }
    var note by rememberSaveable(existing?.id) { mutableStateOf(existing?.note ?: "") }
    var includeInNetWorth by rememberSaveable(existing?.id) { mutableStateOf(existing?.includeInNetWorth ?: true) }
    var canUseForTransactions by rememberSaveable(existing?.id) { mutableStateOf(existing?.canUseForTransactions ?: true) }
    var showIconChoices by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val type = runCatching { AccountType.valueOf(typeName) }.getOrDefault(AccountType.CASH)
    val selectedType = accountTypeOptions.firstOrNull { it.type == type } ?: accountTypeOptions.first()
    val canSubmit = name.isNotBlank()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 1.dp
                    ) {
                        Text(
                            "取消",
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            if (existing == null) "添加资产" else "编辑资产",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        onClick = {
                            if (!canSubmit) {
                                error = "请填写资产名称"
                            } else if (existing == null && parseMoneyToCentsOrZero(balance) == null) {
                                error = "请输入正确的初始余额"
                            } else {
                                error = ""
                                onSave(
                                    existing,
                                    name.trim(),
                                    type,
                                    balance,
                                    selectedType.legacyIcon,
                                    currencyCode,
                                    note.trim(),
                                    includeInNetWorth,
                                    canUseForTransactions
                                )
                            }
                        },
                        shape = RoundedCornerShape(50),
                        color = if (canSubmit) AssetEditorAccent else MaterialTheme.colorScheme.surface,
                        contentColor = if (canSubmit) Color(0xFF3A2B00) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                        shadowElevation = if (canSubmit) 1.dp else 0.dp
                    ) {
                        Text(
                            if (existing == null) "添加" else "保存",
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = 20.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EditorSectionTitle("类型")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        accountTypeOptions.forEach { option ->
                            val selected = option.type == type
                            Surface(
                                onClick = { typeName = option.type.name },
                                shape = RoundedCornerShape(50),
                                color = if (selected) AssetEditorAccent else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selected) Color(0xFF3A2B00) else MaterialTheme.colorScheme.onSurface
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(accountTypeIcon(option.type), contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text(option.label, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }

                    EditorSectionTitle("基本信息")
                    EditorCard {
                        Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showIconChoices = !showIconChoices }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("图标", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.weight(1f))
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = accountTypeIcon(type),
                                        contentDescription = "选择图标",
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        if (showIconChoices) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                accountTypeOptions.forEach { option ->
                                    Surface(
                                        onClick = {
                                            typeName = option.type.name
                                            showIconChoices = false
                                        },
                                        modifier = Modifier.size(38.dp),
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = if (option.type == type) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = accountTypeIcon(option.type),
                                                contentDescription = option.label,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        EditorTextRow(
                            label = "名称",
                            value = name,
                            placeholder = when (type) {
                                AccountType.CASH -> "例如：日常现金"
                                AccountType.BANK_CARD -> "例如：招商银行"
                                AccountType.ALIPAY -> "例如：支付宝"
                                AccountType.WECHAT -> "例如：微信"
                                AccountType.CREDIT_CARD -> "例如：招商信用卡"
                                AccountType.OTHER -> "例如：基金账户"
                            },
                            onValueChange = { name = it },
                            keyboardType = KeyboardType.Text
                        )
                    }

                    EditorSectionTitle("金额")
                    EditorCard {
                        EditorTextRow(
                            label = if (type == AccountType.CREDIT_CARD) "当前欠款" else "当前余额",
                            value = balance,
                            placeholder = "0",
                            onValueChange = { balance = it },
                            keyboardType = KeyboardType.Decimal,
                            readOnly = existing != null,
                            labelColor = AssetEditorAccent
                        )
                        if (existing != null) {
                            Text(
                                "编辑资产不会改变余额；点击资产卡片可调整余额。",
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    EditorSectionTitle("币种")
                    EditorCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("币种", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                "¥ $currencyCode",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    EditorSectionTitle("附加信息")
                    EditorCard {
                        EditorTextRow(
                            label = "备注",
                            value = note,
                            placeholder = "可选",
                            onValueChange = { note = it },
                            keyboardType = KeyboardType.Text
                        )
                    }

                    EditorCard {
                        EditorSwitchRow("计入净资产", includeInNetWorth) { includeInNetWorth = it }
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                        EditorSwitchRow("可选为交易账户", canUseForTransactions) { canUseForTransactions = it }
                    }

                    if (error.isNotBlank()) {
                        Text(
                            error,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun EditorSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun EditorCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(content = content)
    }
}

@Composable
private fun EditorTextRow(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    readOnly: Boolean = false,
    labelColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = labelColor)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            enabled = true,
            readOnly = readOnly,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (value.isBlank()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                            textAlign = TextAlign.End
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun EditorSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
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
