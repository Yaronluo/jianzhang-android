package com.jianzhang.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jianzhang.app.data.local.entity.CategoryType
import com.jianzhang.app.data.local.entity.RecurringCycle
import com.jianzhang.app.data.local.entity.RecurringExpenseEntity
import com.jianzhang.app.data.local.entity.TransactionEntity
import com.jianzhang.app.data.local.entity.TransactionType
import com.jianzhang.app.ui.FinanceUiState
import com.jianzhang.app.ui.components.AppCard
import com.jianzhang.app.ui.components.CollapsingTitleHeader
import com.jianzhang.app.ui.components.EmptyState
import com.jianzhang.app.ui.components.RecurringExpenseRow
import com.jianzhang.app.util.formatMoney
import com.jianzhang.app.util.formatMonth
import com.jianzhang.app.util.parseMoneyToCents
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.YearMonth

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecurringScreen(
    state: FinanceUiState,
    openAdd: Boolean,
    onAddConsumed: () -> Unit,
    onSave: (String?, String, String, String?, String?, RecurringCycle, String, String) -> Unit,
    onConfirm: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<RecurringExpenseEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<RecurringExpenseEntity?>(null) }
    var openSwipeRecurringId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val floatingControlsClearance = navigationBarInset + 12.dp + 64.dp + 16.dp + 56.dp + 12.dp
    val headerCollapseDistancePx = with(density) { 68.dp.toPx() }
    val headerCollapseProgress by remember {
        derivedStateOf {
            val scrolledPx = if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                headerCollapseDistancePx
            }
            (scrolledPx / headerCollapseDistancePx).coerceIn(0f, 1f)
        }
    }
    val currentMonth = YearMonth.from(LocalDate.now())
    val monthSummaries = remember(state.recurringExpenses, state.transactions, currentMonth) {
        listOf(currentMonth, currentMonth.plusMonths(1)).map { month ->
            recurringMonthSummary(month, state.recurringExpenses, state.transactions)
        }
    }

    LaunchedEffect(openAdd) {
        if (openAdd) {
            editingExpense = null
            showDialog = true
            onAddConsumed()
        }
    }

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
            text = { Text("已有的历史账单不会被删除，只会移除这条固定支出规则。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(expense.id)
                    openSwipeRecurringId = null
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = statusBarInset + 112.dp,
                end = 20.dp,
                bottom = floatingControlsClearance
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item(key = "recurring-overview") {
                FixedExpenseOverviewPager(summaries = monthSummaries)
            }
            if (state.recurringExpenses.isEmpty()) {
                item(key = "recurring-empty") {
                    EmptyState("🔁", "暂无固定支出", "点击右下角“+”添加房租、套餐等每月必付项目")
                }
            } else {
                items(state.recurringExpenses, key = { it.id }) { expense ->
                    RecurringExpenseRow(
                        expense = expense,
                        categoryName = state.categories.firstOrNull { it.id == expense.categoryId }?.name,
                        accountName = state.accounts.firstOrNull { it.id == expense.accountId }?.name,
                        categoryIcon = state.categories.firstOrNull { it.id == expense.categoryId }?.icon ?: "🔁",
                        onConfirm = {
                            openSwipeRecurringId = null
                            onConfirm(expense.id)
                        },
                        onEdit = { editingExpense = expense; showDialog = true },
                        onDelete = { pendingDelete = expense },
                        onClick = { editingExpense = expense; showDialog = true },
                        isSwipeOpen = openSwipeRecurringId == expense.id,
                        onSwipeOpen = { openSwipeRecurringId = expense.id },
                        onSwipeClose = {
                            if (openSwipeRecurringId == expense.id) openSwipeRecurringId = null
                        }
                    )
                }
            }
            item(key = "recurring-bottom-spacer") { Spacer(modifier = Modifier.height(12.dp)) }
        }
        CollapsingTitleHeader(
            title = "固定支出",
            collapseProgress = headerCollapseProgress
        )
    }
}

private data class RecurringMonthSummary(
    val month: YearMonth,
    val total: Long,
    val paid: Long,
    val itemCount: Int
) {
    val pending: Long get() = (total - paid).coerceAtLeast(0L)
}

private fun recurringMonthSummary(
    month: YearMonth,
    expenses: List<RecurringExpenseEntity>,
    transactions: List<TransactionEntity>
): RecurringMonthSummary {
    val monthKey = month.toString()
    val paidByExpenseId = transactions
        .filter {
            it.type == TransactionType.EXPENSE &&
                it.date.startsWith(monthKey) &&
                it.sourceRecurringExpenseId != null
        }
        .groupBy { it.sourceRecurringExpenseId!! }
        .mapValues { (_, entries) -> entries.sumOf { it.amountInCents } }
    val scheduled = expenses.filter { expense ->
        paidByExpenseId.containsKey(expense.id) || recurringOccursInMonth(expense, month)
    }
    return RecurringMonthSummary(
        month = month,
        total = scheduled.sumOf { paidByExpenseId[it.id] ?: it.amountInCents },
        paid = scheduled.sumOf { paidByExpenseId[it.id] ?: 0L },
        itemCount = scheduled.size
    )
}

private fun recurringOccursInMonth(expense: RecurringExpenseEntity, month: YearMonth): Boolean {
    val firstMonth = runCatching { YearMonth.from(LocalDate.parse(expense.nextDate)) }.getOrNull() ?: return true
    if (month.isBefore(firstMonth)) return false
    val monthsSinceFirst = ChronoUnit.MONTHS.between(firstMonth, month)
    return expense.cycle == RecurringCycle.MONTHLY || monthsSinceFirst % 12L == 0L
}

@Composable
private fun FixedExpenseOverviewPager(summaries: List<RecurringMonthSummary>) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { summaries.size })
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(154.dp)
            ) { page ->
                FixedExpenseSummaryPage(
                    summary = summaries[page],
                    label = if (page == 0) "本月支出" else "下月支出"
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 7.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                summaries.indices.forEach { page ->
                    Text(
                        text = if (page == pagerState.currentPage) "●" else "○",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FixedExpenseSummaryPage(summary: RecurringMonthSummary, label: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatMonth(summary.month.toString()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${summary.itemCount} 项", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatMoney(summary.total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("已支付 ${formatMoney(summary.paid)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Text(
                if (summary.pending > 0) "待支付 ${formatMoney(summary.pending)}" else "已全部记账",
                style = MaterialTheme.typography.bodySmall,
                color = if (summary.pending > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
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
