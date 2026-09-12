package com.jianzhang.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jianzhang.app.data.local.entity.AccountType
import com.jianzhang.app.data.local.entity.TransactionEntity
import com.jianzhang.app.data.local.entity.TransactionType
import com.jianzhang.app.ui.FinanceUiState
import com.jianzhang.app.ui.components.AppCard
import com.jianzhang.app.ui.components.EmptyState
import com.jianzhang.app.ui.components.TransactionRow
import com.jianzhang.app.util.formatDate
import com.jianzhang.app.util.formatMoney
import com.jianzhang.app.util.formatMonth

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    state: FinanceUiState,
    onMonthPrevious: () -> Unit,
    onMonthNext: () -> Unit,
    onOpenSettings: () -> Unit,
    onEdit: (TransactionEntity) -> Unit,
    onDelete: (String) -> Unit,
    onCreateAccount: (String, AccountType, String) -> Unit,
    onGenerateDemo: () -> Unit
) {
    var searchText by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("全部") }
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
        matchesSearch && matchesFilter
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
        // Scaffold already applies the top system-bar inset to the NavHost.
        // Applying statusBarsPadding() here as well leaves a large empty band on phones with a cutout.
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("账本", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "设置") }
            }
        }
        item {
            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .align(Alignment.Center),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) IconButton(onClick = { searchText = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除搜索")
                        }
                    },
                    placeholder = { Text("搜索账单、分类或账户") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        errorContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent
                    )
                )
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
        item {
            MonthlyOverviewPager(
                income = income,
                expense = expense,
                balance = income - expense,
                budget = state.monthlyBudget?.amountInCents?.takeIf { it > 0 }
            )
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全部", "收入", "支出").forEach { option ->
                    FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option) })
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
private fun MonthlyOverviewPager(
    income: Long,
    expense: Long,
    balance: Long,
    budget: Long?
) {
    val pageCount = if (budget != null) 2 else 1
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })

    AppCard {
        Column {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp),
                userScrollEnabled = pageCount > 1
            ) { page ->
                if (page == 0) {
                    BalanceSummaryPage(income = income, expense = expense, balance = balance)
                } else {
                    BudgetSummaryPage(expense = expense, budget = budget ?: 0L)
                }
            }
            if (pageCount > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pageCount) { page ->
                        Text(
                            text = if (page == pagerState.currentPage) "●" else "○",
                            color = if (page == pagerState.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceSummaryPage(income: Long, expense: Long, balance: Long) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("本月结余", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            formatMoney(balance),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (balance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            OverviewMetric("收入", formatMoney(income), MaterialTheme.colorScheme.primary, Icons.AutoMirrored.Filled.TrendingUp)
            OverviewMetric("支出", formatMoney(expense), MaterialTheme.colorScheme.error, Icons.AutoMirrored.Filled.TrendingDown)
        }
    }
}

@Composable
private fun BudgetSummaryPage(expense: Long, budget: Long) {
    val progress = (expense.toDouble() / budget.toDouble()).coerceIn(0.0, 1.0).toFloat()
    val remaining = budget - expense
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("本月预算", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("已使用 ${formatMoney(expense)} / ${formatMoney(budget)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (remaining >= 0) "剩余 ${formatMoney(remaining)}" else "已超支 ${formatMoney(-remaining)}",
                color = if (remaining >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
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

@Composable
private fun OverviewMetric(label: String, value: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
