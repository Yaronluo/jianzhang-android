package com.jianzhang.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jianzhang.app.data.local.entity.AccountType
import com.jianzhang.app.data.local.entity.TransactionEntity
import com.jianzhang.app.data.local.entity.TransactionType
import com.jianzhang.app.ui.FinanceUiState
import com.jianzhang.app.ui.components.AppCard
import com.jianzhang.app.ui.components.EmptyState
import com.jianzhang.app.ui.components.SectionTitle
import com.jianzhang.app.ui.components.SummaryCard
import com.jianzhang.app.ui.components.TransactionRow
import com.jianzhang.app.util.formatDate
import com.jianzhang.app.util.formatMoney
import com.jianzhang.app.util.formatMonth
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    state: FinanceUiState,
    onMonthPrevious: () -> Unit,
    onMonthNext: () -> Unit,
    onOpenSettings: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (TransactionEntity) -> Unit,
    onDelete: (String) -> Unit,
    onCreateAccount: (String, AccountType, String) -> Unit,
    onGenerateDemo: () -> Unit
) {
    var searchText by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("全部") }
    var categoryFilterId by rememberSaveable { mutableStateOf<String?>(null) }
    var accountFilterId by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    val monthTransactions = state.transactions.filter { it.date.startsWith(state.selectedMonth) }
    val income = monthTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountInCents }
    val expense = monthTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountInCents }
    val filteredTransactions = monthTransactions.filter { transaction ->
        val category = state.categories.firstOrNull { it.id == transaction.categoryId }
        val account = state.accounts.firstOrNull { it.id == transaction.accountId }
        val toAccount = state.accounts.firstOrNull { it.id == transaction.toAccountId }
        val matchesSearch = searchText.isBlank() || listOfNotNull(transaction.note, category?.name, account?.name, toAccount?.name)
            .joinToString(" ").contains(searchText.trim(), ignoreCase = true)
        val matchesFilter = when (filter) {
            "收入" -> transaction.type == TransactionType.INCOME
            "支出" -> transaction.type == TransactionType.EXPENSE
            else -> true
        }
        val matchesCategory = categoryFilterId == null || transaction.categoryId == categoryFilterId
        val matchesAccount = accountFilterId == null || transaction.accountId == accountFilterId || transaction.toAccountId == accountFilterId
        matchesSearch && matchesFilter && matchesCategory && matchesAccount
    }
    val grouped = filteredTransactions.groupBy { it.date }
    val categoryMap = remember(state.categories) { state.categories.associateBy { it.id } }
    val accountMap = remember(state.accounts) { state.accounts.associateBy { it.id } }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这笔账单？") },
            text = { Text("删除后会同步恢复对应账户余额和统计数据。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete?.let { onDelete(it.id) }
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("简账", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("把每一笔钱都变成看得懂的选择", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "设置") }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onMonthPrevious) { Text("‹") }
                Text(formatMonth(state.selectedMonth), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onMonthNext) { Text("›") }
            }
        }
        item { SummaryCard(income = income, expense = expense, balance = income - expense) }
        if (state.monthlyBudget != null && state.monthlyBudget.amountInCents > 0) {
            item {
                BudgetSummaryCard(expense = expense, budget = state.monthlyBudget.amountInCents)
            }
        }
        if (state.accounts.isEmpty()) {
            item {
                AppCard {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("先创建一个账户", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("账户余额会和账单自动联动，现场演示也更直观。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onCreateAccount("现金", AccountType.CASH, "💵") }) { Text("创建现金账户") }
                            TextButton(onClick = onGenerateDemo) { Text("生成演示数据") }
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) IconButton(onClick = { searchText = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "清除搜索")
                    }
                },
                placeholder = { Text("搜索账单、分类或账户") }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全部", "收入", "支出").forEach { option ->
                    FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option) })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box {
                    FilterChip(
                        selected = categoryFilterId != null,
                        onClick = { categoryMenuExpanded = true },
                        label = { Text(state.categories.firstOrNull { it.id == categoryFilterId }?.let { "分类：${it.name}" } ?: "分类") },
                        trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) }
                    )
                    DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("全部分类") }, onClick = { categoryFilterId = null; categoryMenuExpanded = false })
                        state.categories.forEach { category ->
                            DropdownMenuItem(text = { Text("${category.icon} ${category.name}") }, onClick = { categoryFilterId = category.id; categoryMenuExpanded = false })
                        }
                    }
                }
                androidx.compose.foundation.layout.Box {
                    FilterChip(
                        selected = accountFilterId != null,
                        onClick = { accountMenuExpanded = true },
                        label = { Text(state.accounts.firstOrNull { it.id == accountFilterId }?.let { "账户：${it.name}" } ?: "账户") },
                        trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) }
                    )
                    DropdownMenu(expanded = accountMenuExpanded, onDismissRequest = { accountMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("全部账户") }, onClick = { accountFilterId = null; accountMenuExpanded = false })
                        state.accounts.forEach { account ->
                            DropdownMenuItem(text = { Text("${account.icon} ${account.name}") }, onClick = { accountFilterId = account.id; accountMenuExpanded = false })
                        }
                    }
                }
                if (categoryFilterId != null || accountFilterId != null) {
                    TextButton(onClick = { categoryFilterId = null; accountFilterId = null }) { Text("清除") }
                }
            }
        }
        if (grouped.isEmpty()) {
            item {
                EmptyState(
                    icon = "🧾",
                    title = if (searchText.isBlank() && filter == "全部") "还没有账单" else "没有匹配的账单",
                    message = if (searchText.isBlank() && filter == "全部") "点击下方“+”记录第一笔收支" else "试试更换关键词或筛选条件"
                )
            }
        } else {
            grouped.toSortedMap(compareByDescending { it }).forEach { (date, transactions) ->
                item(key = "date-$date") {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatDate(date), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(formatMoney(transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountInCents }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AppCard {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                                transactions.forEach { transaction ->
                                    TransactionRow(
                                        transaction = transaction,
                                        category = categoryMap[transaction.categoryId],
                                        account = accountMap[transaction.accountId],
                                        toAccount = accountMap[transaction.toAccountId],
                                        onEdit = { onEdit(transaction) },
                                        onDelete = { pendingDelete = transaction }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun BudgetSummaryCard(expense: Long, budget: Long) {
    val progress = (expense.toDouble() / budget.toDouble()).coerceIn(0.0, 1.0).toFloat()
    val remaining = budget - expense
    AppCard {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("本月预算", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("已使用 ${formatMoney(expense)} / ${formatMoney(budget)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (remaining >= 0) "剩余 ${formatMoney(remaining)}" else "已超支 ${formatMoney(-remaining)}", color = if (remaining >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(
                when {
                    progress < 0.7f -> "状态正常，继续保持。"
                    progress < 0.9f -> "需要注意后续支出。"
                    progress < 1f -> "预算快用完了。"
                    else -> "本月已经超出预算。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
