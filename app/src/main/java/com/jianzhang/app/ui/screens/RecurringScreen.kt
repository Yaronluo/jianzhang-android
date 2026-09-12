package com.jianzhang.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jianzhang.app.data.local.entity.AccountEntity
import com.jianzhang.app.data.local.entity.CategoryType
import com.jianzhang.app.data.local.entity.RecurringCycle
import com.jianzhang.app.data.local.entity.RecurringExpenseEntity
import com.jianzhang.app.data.local.entity.TransactionType
import com.jianzhang.app.ui.FinanceUiState
import com.jianzhang.app.ui.components.AppCard
import com.jianzhang.app.ui.components.BackTitleBar
import com.jianzhang.app.ui.components.CheckBadge
import com.jianzhang.app.ui.components.EmptyState
import com.jianzhang.app.util.formatDate
import com.jianzhang.app.util.formatMoney
import com.jianzhang.app.util.parseMoneyToCents
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun RecurringScreen(
    state: FinanceUiState,
    onBack: () -> Unit,
    onSave: (String?, String, String, String?, String?, RecurringCycle, String, String) -> Unit,
    onConfirm: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<RecurringExpenseEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<RecurringExpenseEntity?>(null) }
    val monthTransactions = state.transactions.filter { it.date.startsWith(state.selectedMonth) }
    val recurringIds = state.recurringExpenses.map { it.id }.toSet()
    val paid = monthTransactions.filter { it.sourceRecurringExpenseId in recurringIds }.sumOf { it.amountInCents }
    val total = state.recurringExpenses.sumOf { if (it.cycle == RecurringCycle.YEARLY) it.amountInCents / 12 else it.amountInCents }
    val pending = (total - paid).coerceAtLeast(0L)

    if (showDialog) {
        RecurringDialog(
            state = state,
            existing = editingExpense,
            onDismiss = { showDialog = false },
            onSave = { id, name, amount, category, account, cycle, date, note ->
                onSave(id, name, amount, category, account, cycle, date, note)
                showDialog = false
            }
        )
    }
    pendingDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除“${expense.name}”？") },
            text = { Text("已确认的历史账单不会被删除，只会移除这条固定支出规则。") },
            confirmButton = {
                TextButton(onClick = { onDelete(expense.id); pendingDelete = null }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTitleBar(
            title = "固定支出",
            onBack = onBack,
            action = {
                IconButton(onClick = { editingExpense = null; showDialog = true }) { Icon(Icons.Default.Add, contentDescription = "新增固定支出") }
            }
        )
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AppCard {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("本月固定支出", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${formatMoney(paid)} / ${formatMoney(total)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Text("已支付 ${formatMoney(paid)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Text("待支付 ${formatMoney(pending)}", style = MaterialTheme.typography.bodySmall, color = if (pending > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (state.recurringExpenses.isEmpty()) {
                item { EmptyState("🔁", "暂无固定支出", "把房租、套餐等每月必付项目交给简账提醒") }
            } else {
                items(state.recurringExpenses, key = { it.id }) { expense ->
                    RecurringCard(
                        expense = expense,
                        categoryName = state.categories.firstOrNull { it.id == expense.categoryId }?.name,
                        account = state.accounts.firstOrNull { it.id == expense.accountId },
                        categoryIcon = state.categories.firstOrNull { it.id == expense.categoryId }?.icon ?: "🔁",
                        onConfirm = { onConfirm(expense.id) },
                        onEdit = { editingExpense = expense; showDialog = true },
                        onDelete = { pendingDelete = expense }
                    )
                }
            }
            item {
                Button(onClick = { editingExpense = null; showDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  新增固定支出")
                }
            }
        }
    }
}

@Composable
private fun RecurringCard(
    expense: RecurringExpenseEntity,
    categoryName: String?,
    account: AccountEntity?,
    categoryIcon: String,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val today = LocalDate.now()
    val dueDate = runCatching { LocalDate.parse(expense.nextDate) }.getOrDefault(today)
    val isDue = !dueDate.isAfter(today)
    val days = ChronoUnit.DAYS.between(today, dueDate)
    AppCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(categoryIcon, style = MaterialTheme.typography.headlineSmall)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(expense.name, fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull(categoryName, account?.name).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatMoney(expense.amountInCents), fontWeight = FontWeight.SemiBold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "编辑固定支出") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "删除固定支出", tint = MaterialTheme.colorScheme.error) }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("每${if (expense.cycle == RecurringCycle.MONTHLY) "月" else "年"} · ${formatDate(expense.nextDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isDue) {
                    Button(onClick = onConfirm) { Text("确认记账") }
                } else {
                    CheckBadge(if (days == 1L) "明天" else "$days 天后")
                }
            }
        }
    }
}

@Composable
private fun RecurringDialog(
    state: FinanceUiState,
    existing: RecurringExpenseEntity?,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, String?, String?, RecurringCycle, String, String) -> Unit
) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var amount by rememberSaveable(existing?.id) { mutableStateOf(existing?.let { com.jianzhang.app.util.formatInputMoney(it.amountInCents) } ?: "") }
    var categoryId by rememberSaveable(existing?.id) { mutableStateOf(existing?.categoryId ?: state.categories.firstOrNull { it.type == CategoryType.EXPENSE }?.id) }
    var accountId by rememberSaveable(existing?.id) { mutableStateOf(existing?.accountId ?: state.accounts.firstOrNull()?.id) }
    var cycleName by rememberSaveable(existing?.id) { mutableStateOf(existing?.cycle?.name ?: RecurringCycle.MONTHLY.name) }
    var nextDate by rememberSaveable(existing?.id) { mutableStateOf(existing?.nextDate ?: LocalDate.now().toString()) }
    var note by rememberSaveable(existing?.id) { mutableStateOf(existing?.note ?: "") }
    var accountExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val account = state.accounts.firstOrNull { it.id == accountId }
    val cycle = runCatching { RecurringCycle.valueOf(cycleName) }.getOrDefault(RecurringCycle.MONTHLY)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新增固定支出" else "编辑固定支出") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("名称") }, singleLine = true, placeholder = { Text("例如：房租") })
                OutlinedTextField(value = amount, onValueChange = { amount = it }, modifier = Modifier.fillMaxWidth(), label = { Text("金额") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = cycle == RecurringCycle.MONTHLY, onClick = { cycleName = RecurringCycle.MONTHLY.name }, label = { Text("每月") })
                    FilterChip(selected = cycle == RecurringCycle.YEARLY, onClick = { cycleName = RecurringCycle.YEARLY.name }, label = { Text("每年") })
                }
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { accountExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(account?.let { "${it.icon} ${it.name}" } ?: "选择扣款账户", modifier = Modifier.weight(1f))
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                        state.accounts.forEach { item ->
                            DropdownMenuItem(text = { Text("${item.icon} ${item.name}") }, onClick = { accountId = item.id; accountExpanded = false })
                        }
                    }
                }
                OutlinedTextField(value = nextDate, onValueChange = { nextDate = it }, modifier = Modifier.fillMaxWidth(), label = { Text("下次日期") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("备注") }, singleLine = true)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    name.isBlank() -> "请填写名称"
                    parseMoneyToCents(amount) == null -> "请输入正确的金额"
                    runCatching { LocalDate.parse(nextDate) }.isFailure -> "日期格式应为 2026-09-12"
                    else -> ""
                }
                if (error.isBlank()) onSave(existing?.id, name, amount, categoryId, accountId, cycle, nextDate, note)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
