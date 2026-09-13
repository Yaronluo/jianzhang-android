package com.jianzhang.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import java.time.YearMonth

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    state: FinanceUiState,
    onMonthPrevious: () -> Unit,
    onMonthNext: () -> Unit,
    onMonthCurrent: () -> Unit,
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
    val isCurrentMonth = state.selectedMonth == YearMonth.now().toString()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val compactSearchHeight = 44.dp
    val compactSearchHeightPx = with(density) { compactSearchHeight.toPx() }
    val headerCollapseDistancePx = with(density) { 68.dp.toPx() }
    val monthPickerExtentPx = with(density) { 50.dp.toPx() }
    val searchScrollPx by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat()
            else compactSearchHeightPx
        }
    }
    val searchTargetHeightPx = (compactSearchHeightPx - searchScrollPx).coerceAtLeast(0f)
    val animatedSearchHeightPx by animateFloatAsState(
        targetValue = searchTargetHeightPx,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "搜索栏收起展开"
    )
    val searchVisibleHeight = with(density) {
        animatedSearchHeightPx.toDp()
    }
    val headerCollapseProgress by remember {
        derivedStateOf {
            val scrollPastSearch = when (listState.firstVisibleItemIndex) {
                0 -> 0f
                1 -> listState.firstVisibleItemScrollOffset.toFloat()
                2 -> monthPickerExtentPx + listState.firstVisibleItemScrollOffset.toFloat()
                else -> headerCollapseDistancePx
            }
            (scrollPastSearch / headerCollapseDistancePx).coerceIn(0f, 1f)
        }
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            // The title bar is an overlay. Only the content below it scrolls away.
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = statusBarInset + 96.dp, end = 20.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
        item(key = "search") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(searchVisibleHeight)
                    .clipToBounds()
                    .graphicsLayer { translationY = searchScrollPx }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(compactSearchHeight)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                        .padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchText.isEmpty()) {
                            Text(
                                "搜索账单、分类或账户",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "搜索账单、分类或账户" },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "清除搜索", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        item(key = "month-picker") {
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MonthNavigationButton(symbol = "‹", description = "上个月", onClick = onMonthPrevious)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatMonth(state.selectedMonth), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (!isCurrentMonth) {
                        Surface(
                            onClick = onMonthCurrent,
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 3.dp
                        ) {
                            Text(
                                "回到本月",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                MonthNavigationButton(symbol = "›", description = "下个月", onClick = onMonthNext)
            }
        }
        item(key = "monthly-overview") {
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
        item(key = "filters") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全部", "收入", "支出").forEach { option ->
                    val selected = filter == option
                    Surface(
                        onClick = { filter = option },
                        shape = RoundedCornerShape(50),
                        color = if (selected) Color(0xFF242528) else Color.Transparent,
                        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(
                            option,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
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
                            Text(
                                "${formatDate(date)} · ${weekdayText(date)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "收 ${formatMoney(transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountInCents })}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "支 ${formatMoney(transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountInCents })}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Column {
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
        item { Spacer(modifier = Modifier.height(12.dp)) }
        }
        LedgerCollapsingHeader(
            collapseProgress = headerCollapseProgress,
            onOpenSettings = onOpenSettings
        )
    }
}

@Composable
private fun LedgerCollapsingHeader(
    collapseProgress: Float,
    onOpenSettings: () -> Unit
) {
    val progress = collapseProgress
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val largeTitleProgress = smoothStep((progress / 0.52f).coerceIn(0f, 1f))
    val compactTitleProgress = smoothStep(((progress - 0.60f) / 0.40f).coerceIn(0f, 1f))
    val frostProgress = smoothStep(progress)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(statusBarInset + 104.dp)
            .zIndex(3f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 1f - largeTitleProgress
                    translationY = -48.dp.toPx() * largeTitleProgress
                }
        ) {
            Text(
                "账本",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 10.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusBarInset + 128.dp)
                .align(Alignment.TopCenter)
                .zIndex(1f)
                .graphicsLayer { alpha = frostProgress }
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                            0.28f to MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            0.58f to MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
                            0.82f to MaterialTheme.colorScheme.surface.copy(alpha = 0.30f),
                            1f to MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                        )
                    )
                )
        )
        Text(
            "账本",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusBarInset + 18.dp)
                .zIndex(2f)
                .graphicsLayer {
                    alpha = compactTitleProgress
                    translationY = 10.dp.toPx() * (1f - compactTitleProgress)
                },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Surface(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusBarInset + 8.dp, end = 12.dp)
                .size(44.dp)
                .zIndex(2f),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Settings, contentDescription = "设置", modifier = Modifier.size(24.dp))
            }
        }
    }
}

private fun smoothStep(value: Float): Float = value * value * (3f - 2f * value)

@Composable
private fun MonthNavigationButton(symbol: String, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(34.dp)
            .semantics { contentDescription = description },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
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
                    .height(128.dp),
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
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pageCount) { page ->
                        Text(
                            text = if (page == pagerState.currentPage) "●" else "○",
                            color = if (page == pagerState.currentPage) Color(0xFFF4C542) else Color(0xFFF4C542).copy(alpha = 0.52f),
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text("本月结余", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            formatMoney(balance),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("本月预算", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("已使用 ${formatMoney(expense)} / ${formatMoney(budget)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (remaining >= 0) "剩余 ${formatMoney(remaining)}" else "已超支 ${formatMoney(-remaining)}",
                color = if (remaining >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
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

private fun weekdayText(date: String): String = runCatching {
    when (java.time.LocalDate.parse(date).dayOfWeek) {
        java.time.DayOfWeek.MONDAY -> "星期一"
        java.time.DayOfWeek.TUESDAY -> "星期二"
        java.time.DayOfWeek.WEDNESDAY -> "星期三"
        java.time.DayOfWeek.THURSDAY -> "星期四"
        java.time.DayOfWeek.FRIDAY -> "星期五"
        java.time.DayOfWeek.SATURDAY -> "星期六"
        java.time.DayOfWeek.SUNDAY -> "星期日"
    }
}.getOrDefault("")

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
