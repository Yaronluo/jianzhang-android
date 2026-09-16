package com.jianzhang.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jianzhang.app.data.local.entity.CategoryType
import com.jianzhang.app.data.local.entity.RecurringCycle
import com.jianzhang.app.data.local.entity.RecurringExpenseEntity
import com.jianzhang.app.data.local.entity.TransactionEntity
import com.jianzhang.app.data.local.entity.TransactionType
import com.jianzhang.app.domain.account.AccountSelectionPolicy
import com.jianzhang.app.ui.FinanceUiState
import com.jianzhang.app.ui.components.AppCard
import com.jianzhang.app.ui.components.AppDropdownItem
import com.jianzhang.app.ui.components.AppDropdownMenu
import com.jianzhang.app.ui.components.AccountTypeIcon
import com.jianzhang.app.ui.components.CategoryIcon
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
                        categoryId = expense.categoryId,
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
    var amount by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.let { com.jianzhang.app.util.formatInputMoney(it.amountInCents) } ?: "")
    }
    var categoryId by rememberSaveable(existing?.id) { mutableStateOf(existing?.categoryId) }
    var accountId by rememberSaveable(existing?.id) { mutableStateOf(existing?.accountId) }
    var cycleName by rememberSaveable(existing?.id) { mutableStateOf(existing?.cycle?.name ?: RecurringCycle.MONTHLY.name) }
    var nextDate by rememberSaveable(existing?.id) { mutableStateOf(existing?.nextDate ?: LocalDate.now().toString()) }
    var note by rememberSaveable(existing?.id) { mutableStateOf(existing?.note ?: "") }
    var error by remember { mutableStateOf("") }
    val expenseCategories = remember(state.categories) {
        state.categories
            .filter { it.type == CategoryType.EXPENSE && it.enabled }
            .sortedBy { it.sortOrder }
    }
    val category = expenseCategories.firstOrNull { it.id == categoryId }
    val availableAccounts = AccountSelectionPolicy.transactionAccounts(
        accounts = state.accounts,
        retainedAccountIds = setOfNotNull(accountId)
    )
    val account = availableAccounts.firstOrNull { it.id == accountId }
    val cycle = runCatching { RecurringCycle.valueOf(cycleName) }.getOrDefault(RecurringCycle.MONTHLY)
    val dateValid = runCatching { LocalDate.parse(nextDate.trim()) }.isSuccess
    val canSubmit = name.isNotBlank() && parseMoneyToCents(amount) != null && dateValid

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
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            if (existing == null) "添加固定支出" else "编辑固定支出",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Surface(
                        onClick = {
                            error = when {
                                name.isBlank() -> "请填写名称"
                                parseMoneyToCents(amount) == null -> "请输入正确的金额"
                                !dateValid -> "日期格式应为 2026-09-12"
                                else -> ""
                            }
                            if (error.isBlank()) {
                                onSave(existing?.id, name.trim(), amount, categoryId, accountId, cycle, nextDate.trim(), note.trim())
                            }
                        },
                        shape = RoundedCornerShape(50),
                        color = if (canSubmit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (canSubmit) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                        shadowElevation = if (canSubmit) 1.dp else 0.dp
                    ) {
                        Text(
                            if (existing == null) "添加" else "保存",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                    AppDropdownMenu(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = expenseCategories.isNotEmpty(),
                        anchor = { onClick, expanded ->
                            val arrowRotation by animateFloatAsState(
                                targetValue = if (expanded) 180f else 0f,
                                label = "category-arrow"
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onClick,
                                enabled = expenseCategories.isNotEmpty(),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (category != null) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = CircleShape,
                                        color = if (category == null) {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                                        }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (category == null) {
                                                Icon(
                                                    imageVector = Icons.Outlined.MoreHoriz,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(22.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                CategoryIcon(
                                                    categoryId = category.id,
                                                    categoryName = category.name,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        category?.name ?: "选择分类",
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 14.dp),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (category == null) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "选择分类",
                                        modifier = Modifier
                                            .size(22.dp)
                                            .rotate(arrowRotation),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        menuContent = { dismiss ->
                            expenseCategories.forEach { item ->
                                AppDropdownItem(
                                    text = item.name,
                                    selected = item.id == categoryId,
                                    leadingContent = {
                                        CategoryIcon(
                                            categoryId = item.id,
                                            categoryName = item.name,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {
                                        categoryId = item.id
                                        dismiss()
                                    }
                                )
                            }
                        }
                    )

                    RecurringEditorSectionTitle("基本信息")
                    RecurringEditorCard {
                        RecurringEditorTextRow(
                            label = "名称",
                            value = name,
                            placeholder = "名称",
                            onValueChange = { name = it; error = "" },
                            keyboardType = KeyboardType.Text
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        RecurringEditorTextRow(
                            label = "金额",
                            value = amount,
                            placeholder = "0",
                            onValueChange = { amount = it; error = "" },
                            keyboardType = KeyboardType.Decimal
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        RecurringEditorValueRow(label = "币种", value = "CNY")
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        AppDropdownMenu(
                            modifier = Modifier.fillMaxWidth(),
                            anchor = { onClick, expanded ->
                                RecurringEditorValueRow(
                                    label = "周期",
                                    value = if (cycle == RecurringCycle.MONTHLY) "每月" else "每年",
                                    onClick = onClick,
                                    showTrailingIcon = true,
                                    expanded = expanded
                                )
                            },
                            menuContent = { dismiss ->
                                AppDropdownItem(
                                    text = "每月",
                                    selected = cycle == RecurringCycle.MONTHLY,
                                    onClick = {
                                        cycleName = RecurringCycle.MONTHLY.name
                                        dismiss()
                                    }
                                )
                                AppDropdownItem(
                                    text = "每年",
                                    selected = cycle == RecurringCycle.YEARLY,
                                    onClick = {
                                        cycleName = RecurringCycle.YEARLY.name
                                        dismiss()
                                    }
                                )
                            }
                        )
                    }

                    RecurringEditorSectionTitle("日期")
                    RecurringEditorCard {
                        RecurringEditorTextRow(
                            label = "开始日期",
                            value = nextDate,
                            placeholder = LocalDate.now().toString(),
                            onValueChange = { nextDate = it; error = "" },
                            keyboardType = KeyboardType.Text
                        )
                    }

                    RecurringEditorCard {
                        RecurringEditorSwitchRow(label = "自动续费", checked = existing?.enabled ?: true)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        AppDropdownMenu(
                            modifier = Modifier.fillMaxWidth(),
                            anchor = { onClick, expanded ->
                                RecurringEditorValueRow(
                                    label = "付款账户",
                                    value = account?.name ?: "未选择",
                                    valueColor = if (account == null) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    onClick = onClick,
                                    showTrailingIcon = true,
                                    expanded = expanded
                                )
                            },
                            menuContent = { dismiss ->
                                availableAccounts.forEach { item ->
                                    AppDropdownItem(
                                        text = item.name,
                                        selected = item.id == accountId,
                                        leadingContent = {
                                            AccountTypeIcon(
                                                type = item.type,
                                                modifier = Modifier.size(22.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        onClick = {
                                            accountId = item.id
                                            dismiss()
                                        }
                                    )
                                }
                            }
                        )
                    }

                    RecurringEditorSectionTitle("备注")
                    RecurringEditorCard {
                        RecurringEditorTextRow(
                            label = "备注",
                            value = note,
                            placeholder = "备注（可选）",
                            onValueChange = { note = it },
                            keyboardType = KeyboardType.Text
                        )
                    }

                    if (error.isNotBlank()) {
                        Text(
                            error,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun RecurringEditorSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun RecurringEditorCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(content = content)
    }
}

@Composable
private fun RecurringEditorTextRow(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
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
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
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
private fun RecurringEditorValueRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    showTrailingIcon: Boolean = false,
    expanded: Boolean = false
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "recurring-value-arrow"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable { onClick() })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = valueColor)
        if (showTrailingIcon) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .rotate(arrowRotation),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun RecurringEditorSwitchRow(label: String, checked: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
