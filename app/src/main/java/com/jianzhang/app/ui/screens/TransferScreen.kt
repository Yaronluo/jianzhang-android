package com.jianzhang.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.jianzhang.app.data.local.entity.AccountEntity
import com.jianzhang.app.data.local.entity.TransactionType
import com.jianzhang.app.domain.account.AccountSelectionPolicy
import com.jianzhang.app.ui.FinanceUiState
import com.jianzhang.app.ui.components.AppCard
import com.jianzhang.app.ui.components.AppDropdownField
import com.jianzhang.app.ui.components.AppDropdownItem
import com.jianzhang.app.ui.components.AccountTypeIcon
import com.jianzhang.app.ui.components.BackTitleBar
import com.jianzhang.app.util.formatInputMoney
import com.jianzhang.app.util.formatMoney
import com.jianzhang.app.util.parseMoneyToCents
import java.time.LocalDate

@Composable
fun TransferScreen(
    state: FinanceUiState,
    transactionId: String?,
    onBack: () -> Unit,
    onSave: (String?, String, String?, String?, String, String) -> Unit
) {
    val editing = remember(state.transactions, transactionId) {
        state.transactions.firstOrNull { it.id == transactionId && it.type == TransactionType.TRANSFER }
    }
    var amount by rememberSaveable(transactionId) { mutableStateOf(editing?.let { formatInputMoney(it.amountInCents) } ?: "") }
    var fromId by rememberSaveable(transactionId) { mutableStateOf(editing?.accountId) }
    var toId by rememberSaveable(transactionId) { mutableStateOf(editing?.toAccountId) }
    var date by rememberSaveable(transactionId) { mutableStateOf(editing?.date ?: LocalDate.now().toString()) }
    var note by rememberSaveable(transactionId) { mutableStateOf(editing?.note ?: "") }
    var amountError by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }
    var accountsError by remember { mutableStateOf(false) }
    val availableAccounts = AccountSelectionPolicy.transactionAccounts(
        accounts = state.accounts,
        retainedAccountIds = setOfNotNull(fromId, toId)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        BackTitleBar(title = if (editing == null) "账户转账" else "编辑转账", onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AppCard {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("账户之间的资金转移不会计入收入或支出。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it; amountError = false },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = amountError,
                            label = { Text("转账金额") },
                            prefix = { Text("¥") },
                            placeholder = { Text("0.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        if (amountError) Text("请输入大于 0 的金额", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                AccountPicker("转出账户", availableAccounts, fromId, excludedId = toId, onSelected = { fromId = it; accountsError = false })
            }
            item {
                AccountPicker("转入账户", availableAccounts, toId, excludedId = fromId, onSelected = { toId = it; accountsError = false })
            }
            item {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it; dateError = false },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = dateError,
                    label = { Text("日期") },
                    supportingText = { if (dateError) Text("格式：2026-09-12") }
                )
            }
            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注") },
                    placeholder = { Text("例如：银行卡转入支付宝") },
                    minLines = 2,
                    maxLines = 3
                )
            }
            item {
                if (accountsError) Text("请选择两个不同的账户", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = {
                        amountError = parseMoneyToCents(amount) == null
                        dateError = runCatching { LocalDate.parse(date) }.isFailure
                        accountsError = fromId == null || toId == null || fromId == toId
                        if (!amountError && !dateError && !accountsError) onSave(transactionId, amount, fromId, toId, date, note)
                    },
                    enabled = availableAccounts.size >= 2,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (editing == null) "确认转账" else "保存修改") }
                if (availableAccounts.size < 2) {
                    Text("至少需要两个账户才能转账。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AccountPicker(
    label: String,
    accounts: List<AccountEntity>,
    selectedId: String?,
    excludedId: String?,
    onSelected: (String) -> Unit
) {
    val selected = accounts.firstOrNull { it.id == selectedId }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        AppDropdownField(
            label = label,
            value = selected?.name ?: "",
            placeholder = "请选择账户",
            modifier = Modifier.fillMaxWidth(),
            leadingContent = selected?.let { account ->
                {
                    AccountTypeIcon(
                        type = account.type,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        ) { dismiss ->
            accounts.filter { it.id != excludedId }.forEach { account ->
                AppDropdownItem(
                    text = account.name,
                    supportingText = formatMoney(account.balanceInCents),
                    selected = account.id == selectedId,
                    leadingContent = {
                        AccountTypeIcon(
                            type = account.type,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {
                        onSelected(account.id)
                        dismiss()
                    }
                )
            }
        }
    }
}
