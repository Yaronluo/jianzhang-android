package com.jianzhang.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jianzhang.app.data.local.entity.AccountEntity
import com.jianzhang.app.data.local.entity.BudgetEntity
import com.jianzhang.app.data.local.entity.CategoryEntity
import com.jianzhang.app.data.local.entity.CategoryType
import com.jianzhang.app.data.local.entity.TransactionEntity
import com.jianzhang.app.data.local.entity.TransactionType
import com.jianzhang.app.ui.FinanceUiState
import com.jianzhang.app.ui.components.AppCard
import com.jianzhang.app.ui.components.CategoryIcon
import com.jianzhang.app.ui.components.CollapsingTitleHeader
import com.jianzhang.app.util.formatMoney
import com.jianzhang.app.util.formatInputMoney
import com.jianzhang.app.util.formatMonth
import com.jianzhang.app.util.parseMoneyToCentsOrZero
import com.jianzhang.app.util.percentText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class StatisticsPeriod(val label: String) {
    WEEK("周"),
    MONTH("月"),
    YEAR("年"),
    ALL("全部")
}

private enum class CashFlowMetric(val label: String) {
    EXPENSE("支出"),
    INCOME("收入"),
    BALANCE("结余")
}

private enum class CategoryMetric(val label: String) {
    EXPENSE("支出"),
    INCOME("收入")
}

private enum class RankingMetric(val label: String) {
    EXPENSE("支出"),
    INCOME("收入")
}

private enum class AssetMetric(val label: String) {
    NET_WORTH("净资产"),
    TOTAL_ASSETS("总资产"),
    TOTAL_LIABILITIES("总负债")
}

internal data class StatisticsRange(
    val start: LocalDate,
    val end: LocalDate,
    val label: String
)

internal data class DatedTransaction(
    val transaction: TransactionEntity,
    val date: LocalDate
)

private val ChartYAxisWidth = 40.dp
private val ChartHorizontalInset = 12.dp

internal data class ChartWindow(
    val label: String,
    val start: LocalDate,
    val end: LocalDate
)

internal data class StatisticsBucket(
    val label: String,
    val start: LocalDate,
    val end: LocalDate,
    val income: Long,
    val expense: Long
) {
    val balance: Long
        get() = income - expense
}

internal data class CategorySlice(
    val name: String,
    val icon: String,
    val amount: Long,
    val categoryId: String? = null
)

internal data class AssetSnapshot(
    val totalAssets: Long,
    val totalLiabilities: Long
) {
    val netWorth: Long
        get() = totalAssets - totalLiabilities
}

internal data class AssetPoint(
    val label: String,
    val netWorth: Long,
    val totalAssets: Long,
    val totalLiabilities: Long
)

private data class LineSeries(
    val label: String,
    val color: Color,
    val values: List<Long>,
    val actualCount: Int = values.size
)

@Composable
fun StatisticsScreen(
    state: FinanceUiState,
    onSetBudget: (String, String) -> Unit,
    onSetCategoryBudget: (String, String, String) -> Unit,
    onEditTransaction: (TransactionEntity) -> Unit
) {
    val today = LocalDate.now()
    var selectedPeriodName by rememberSaveable { mutableStateOf(StatisticsPeriod.MONTH.name) }
    var selectedCashFlowName by rememberSaveable { mutableStateOf(CashFlowMetric.EXPENSE.name) }
    var selectedCategoryName by rememberSaveable { mutableStateOf(CategoryMetric.EXPENSE.name) }
    var selectedRankingName by rememberSaveable { mutableStateOf(RankingMetric.EXPENSE.name) }
    var selectedAssetName by rememberSaveable { mutableStateOf(AssetMetric.NET_WORTH.name) }
    var showBudgetDialog by rememberSaveable { mutableStateOf(false) }
    var selectedCategoryBudgetId by rememberSaveable { mutableStateOf<String?>(null) }
    // 统计页的时间轴是自己的筛选状态，不能跟随账本页最后浏览的月份。
    // 否则用户在账本页翻到未来月份后进入统计页，会直接看到无数据的未来区间。
    var anchorDateName by rememberSaveable {
        mutableStateOf(today.toString())
    }
    LaunchedEffect(Unit) {
        // 清理旧版本可能恢复的账本月份锚点，进入统计页始终从当前日期开始。
        anchorDateName = today.toString()
    }

    val selectedPeriod = StatisticsPeriod.values().firstOrNull { it.name == selectedPeriodName }
        ?: StatisticsPeriod.MONTH
    val selectedCashFlow = CashFlowMetric.values().firstOrNull { it.name == selectedCashFlowName }
        ?: CashFlowMetric.EXPENSE
    val selectedCategory = CategoryMetric.values().firstOrNull { it.name == selectedCategoryName }
        ?: CategoryMetric.EXPENSE
    val selectedRanking = RankingMetric.values().firstOrNull { it.name == selectedRankingName }
        ?: RankingMetric.EXPENSE
    val selectedAsset = AssetMetric.values().firstOrNull { it.name == selectedAssetName }
        ?: AssetMetric.NET_WORTH

    val anchorDate = runCatching { LocalDate.parse(anchorDateName) }.getOrDefault(today)
    val budgetMonth = YearMonth.from(anchorDate).toString()
    val monthBudgets = remember(state.budgets, budgetMonth) {
        state.budgets.filter { it.month == budgetMonth }
    }
    val monthlyBudget = monthBudgets.firstOrNull { it.categoryId == null }
    val allDatedTransactions = remember(state.transactions) {
        state.transactions.mapNotNull { transaction ->
            runCatching { LocalDate.parse(transaction.date) }
                .getOrNull()
                ?.let { date -> DatedTransaction(transaction, date) }
        }
    }
    val range = remember(selectedPeriod, anchorDate, allDatedTransactions, today) {
        statisticsRange(selectedPeriod, anchorDate, allDatedTransactions, today)
    }
    val currentRange = remember(selectedPeriod, allDatedTransactions, today) {
        statisticsRange(selectedPeriod, today, allDatedTransactions, today)
    }
    val visibleDatedTransactions = remember(range, allDatedTransactions) {
        allDatedTransactions.filter { it.date >= range.start && it.date <= range.end }
    }
    val visibleTransactions = remember(visibleDatedTransactions) {
        visibleDatedTransactions
            .filter { it.transaction.type == TransactionType.INCOME || it.transaction.type == TransactionType.EXPENSE }
            .map { it.transaction }
    }
    val income = visibleTransactions
        .filter { it.type == TransactionType.INCOME }
        .sumOf { it.amountInCents }
    val expense = visibleTransactions
        .filter { it.type == TransactionType.EXPENSE }
        .sumOf { it.amountInCents }
    val activeDays = activeDayCount(range, today)
    val averageExpense = if (activeDays > 0L) expense / activeDays else 0L
    val buckets = remember(selectedPeriod, range, visibleDatedTransactions) {
        statisticsBuckets(selectedPeriod, range, visibleDatedTransactions)
    }
    val expenseCategories = remember(visibleTransactions, state.categories) {
        categorySlices(visibleTransactions, state.categories, TransactionType.EXPENSE)
    }
    val incomeCategories = remember(visibleTransactions, state.categories) {
        categorySlices(visibleTransactions, state.categories, TransactionType.INCOME)
    }
    val assetPoints = remember(selectedPeriod, range, allDatedTransactions, state.accounts) {
        assetTrendPoints(
            period = selectedPeriod,
            range = range,
            datedTransactions = allDatedTransactions,
            accounts = state.accounts
        )
    }

    val listState = rememberLazyListState()
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val headerCollapseDistancePx = with(LocalDensity.current) { 68.dp.toPx() }
    val headerCollapseProgress by remember {
        androidx.compose.runtime.derivedStateOf {
            val scrollPx = if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                headerCollapseDistancePx
            }
            (scrollPx / headerCollapseDistancePx).coerceIn(0f, 1f)
        }
    }
    val floatingControlsClearance = navigationBarInset + 12.dp + 64.dp + 16.dp + 56.dp + 12.dp

    if (showBudgetDialog) {
        BudgetAmountDialog(
            title = "设置${formatMonth(budgetMonth)}预算",
            current = monthlyBudget?.amountInCents,
            onDismiss = { showBudgetDialog = false },
            onSave = { value ->
                onSetBudget(budgetMonth, value)
                showBudgetDialog = false
            }
        )
    }
    selectedCategoryBudgetId?.let { categoryId ->
        val category = state.categories.firstOrNull { it.id == categoryId }
        BudgetAmountDialog(
            title = "设置${category?.name ?: "分类"}预算",
            current = monthBudgets.firstOrNull { it.categoryId == categoryId }?.amountInCents,
            onDismiss = { selectedCategoryBudgetId = null },
            onSave = { value ->
                onSetCategoryBudget(budgetMonth, categoryId, value)
                selectedCategoryBudgetId = null
            }
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
                top = statusBarInset + 96.dp,
                end = 20.dp,
                bottom = floatingControlsClearance
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "period-navigation") {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    StatisticsFilterRow(
                        options = StatisticsPeriod.values().map { it.label },
                        selected = selectedPeriod.label,
                        onSelect = { label ->
                            StatisticsPeriod.values().firstOrNull { it.label == label }?.let {
                                selectedPeriodName = it.name
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                    )
                    StatisticsDateNavigationRow(
                        period = selectedPeriod,
                        rangeLabel = range.label,
                        isCurrent = range.start == currentRange.start && range.end == currentRange.end,
                        onPrevious = {
                            anchorDateName = shiftStatisticsAnchor(anchorDate, selectedPeriod, -1).toString()
                        },
                        onNext = {
                            anchorDateName = shiftStatisticsAnchor(anchorDate, selectedPeriod, 1).toString()
                        },
                        onCurrent = { anchorDateName = today.toString() }
                    )
                }
            }
            item(key = "summary") {
                StatisticsSummaryCard(
                    expense = expense,
                    income = income,
                    balance = income - expense,
                    averageExpense = averageExpense
                )
            }
            if (selectedPeriod == StatisticsPeriod.MONTH) {
                item(key = "budget") {
                    BudgetManagementCard(
                        monthLabel = range.label,
                        expense = expense,
                        monthlyBudget = monthlyBudget,
                        categoryBudgets = monthBudgets,
                        expenseCategories = state.categories.filter { it.type == CategoryType.EXPENSE },
                        categoryExpenses = visibleTransactions
                            .filter { it.type == TransactionType.EXPENSE }
                            .groupBy { it.categoryId }
                            .mapValues { (_, transactions) -> transactions.sumOf { it.amountInCents } },
                        onEditMonthly = { showBudgetDialog = true },
                        onEditCategory = { selectedCategoryBudgetId = it }
                    )
                }
            }
            item(key = "cash-flow-trend") {
                CashFlowTrendCard(
                    period = selectedPeriod,
                    range = range,
                    today = today,
                    buckets = buckets,
                    selectedMetric = selectedCashFlow,
                    onMetricSelect = { selectedCashFlowName = it.name }
                )
            }
            item(key = "category-distribution") {
                CategoryDistributionCard(
                    selectedMetric = selectedCategory,
                    onMetricSelect = { selectedCategoryName = it.name },
                    expenseCategories = expenseCategories,
                    incomeCategories = incomeCategories
                )
            }
            item(key = "transaction-ranking") {
                TransactionRankingCard(
                    transactions = visibleTransactions,
                    categories = state.categories,
                    selectedMetric = selectedRanking,
                    onMetricSelect = { selectedRankingName = it.name },
                    onEdit = onEditTransaction
                )
            }
            item(key = "asset-trend") {
                AssetTrendCard(
                    period = selectedPeriod,
                    range = range,
                    today = today,
                    points = assetPoints,
                    selectedMetric = selectedAsset,
                    onMetricSelect = { selectedAssetName = it.name }
                )
            }
            item(key = "bottom-space") { Spacer(modifier = Modifier.height(12.dp)) }
        }

        // 与账本页共用同一个折叠标题组件，保证大标题和顶部小标题的位置一致。
        CollapsingTitleHeader(title = "统计", collapseProgress = headerCollapseProgress)
    }
}

@Composable
private fun StatisticsFilterRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            StatisticsFilterPill(
                label = option,
                selected = option == selected,
                onClick = { onSelect(option) }
            )
        }
    }
}

@Composable
private fun StatisticsFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) Color(0xFF242528) else Color.Transparent,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun StatisticsDateNavigationRow(
    period: StatisticsPeriod,
    rangeLabel: String,
    isCurrent: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit
) {
    val canNavigate = period != StatisticsPeriod.ALL
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StatisticsNavigationButton(
            symbol = "‹",
            description = "${period.navigationPrefix()}之前",
            enabled = canNavigate,
            onClick = onPrevious
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = rangeLabel,
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (canNavigate && !isCurrent) {
                Surface(
                    onClick = onCurrent,
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 3.dp
                ) {
                    Text(
                        text = "回到${period.currentLabel()}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
        StatisticsNavigationButton(
            symbol = "›",
            description = "${period.navigationPrefix()}之后",
            enabled = canNavigate,
            onClick = onNext
        )
    }
}

@Composable
private fun StatisticsNavigationButton(
    symbol: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = { if (enabled) onClick() },
        modifier = Modifier
            .size(34.dp)
            .semantics { contentDescription = description },
        shape = CircleShape,
        color = if (enabled) MaterialTheme.colorScheme.surface else Color.Transparent,
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        shadowElevation = if (enabled) 3.dp else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun StatisticsPeriod.navigationPrefix(): String = when (this) {
    StatisticsPeriod.WEEK -> "本周"
    StatisticsPeriod.MONTH -> "本月"
    StatisticsPeriod.YEAR -> "今年"
    StatisticsPeriod.ALL -> "全部"
}

private fun StatisticsPeriod.currentLabel(): String = when (this) {
    StatisticsPeriod.WEEK -> "本周"
    StatisticsPeriod.MONTH -> "本月"
    StatisticsPeriod.YEAR -> "今年"
    StatisticsPeriod.ALL -> "全部"
}

@Composable
private fun StatisticsSummaryCard(
    expense: Long,
    income: Long,
    balance: Long,
    averageExpense: Long
) {
    AppCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ChartCardHeader(title = "概览")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryMetric(
                    label = "支出",
                    value = formatMoney(expense),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    label = "收入",
                    value = formatMoney(income),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryMetric(
                    label = "结余",
                    value = formatMoney(balance),
                    color = if (balance >= 0L) BalanceColor else MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    label = "日均支出",
                    value = formatMoney(averageExpense),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BudgetManagementCard(
    monthLabel: String,
    expense: Long,
    monthlyBudget: BudgetEntity?,
    categoryBudgets: List<BudgetEntity>,
    expenseCategories: List<CategoryEntity>,
    categoryExpenses: Map<String?, Long>,
    onEditMonthly: () -> Unit,
    onEditCategory: (String) -> Unit
) {
    val totalBudget = monthlyBudget?.amountInCents?.takeIf { it > 0L }
    AppCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "预算",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = monthLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onEditMonthly) {
                    Text(if (totalBudget == null) "设置总预算" else "修改总预算")
                }
            }

            if (totalBudget == null) {
                Text(
                    text = "还没有设置月度总预算",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val remaining = totalBudget - expense
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "已用 ${formatMoney(expense)} / ${formatMoney(totalBudget)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = if (remaining >= 0L) {
                            "剩余 ${formatMoney(remaining)}"
                        } else {
                            "超支 ${formatMoney(-remaining)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (remaining >= 0L) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
                LinearProgressIndicator(
                    progress = {
                        (expense.toDouble() / totalBudget.toDouble()).coerceIn(0.0, 1.0).toFloat()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()
            Text(
                text = "分类预算",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            expenseCategories.forEachIndexed { index, category ->
                val used = categoryExpenses[category.id] ?: 0L
                val limit = categoryBudgets
                    .firstOrNull { it.categoryId == category.id }
                    ?.amountInCents
                    ?.takeIf { it > 0L }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditCategory(category.id) }
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryIcon(
                            categoryId = category.id,
                            categoryName = category.name,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = category.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (limit == null) {
                                "未设置  ›"
                            } else {
                                "${formatMoney(used)} / ${formatMoney(limit)}  ›"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (limit != null && used > limit) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1
                        )
                    }
                    if (limit != null) {
                        LinearProgressIndicator(
                            progress = {
                                (used.toDouble() / limit.toDouble()).coerceIn(0.0, 1.0).toFloat()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (index < expenseCategories.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                }
            }
            Text(
                text = "输入 0 可移除预算",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BudgetAmountDialog(
    title: String,
    current: Long?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by rememberSaveable(title) {
        mutableStateOf(current?.let(::formatInputMoney).orEmpty())
    }
    var error by remember(title) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        error = ""
                    },
                    singleLine = true,
                    label = { Text("预算金额") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = error.isNotBlank()
                )
                if (error.isNotBlank()) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = parseMoneyToCentsOrZero(value)
                    if (amount == null) {
                        error = "请输入不小于 0 的金额"
                    } else {
                        onSave(value)
                    }
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun CashFlowTrendCard(
    period: StatisticsPeriod,
    range: StatisticsRange,
    today: LocalDate,
    buckets: List<StatisticsBucket>,
    selectedMetric: CashFlowMetric,
    onMetricSelect: (CashFlowMetric) -> Unit
) {
    val color = when (selectedMetric) {
        CashFlowMetric.EXPENSE -> MaterialTheme.colorScheme.error
        CashFlowMetric.INCOME -> MaterialTheme.colorScheme.primary
        CashFlowMetric.BALANCE -> BalanceColor
    }
    val values = buckets.map { bucket ->
        when (selectedMetric) {
            CashFlowMetric.EXPENSE -> bucket.expense
            CashFlowMetric.INCOME -> bucket.income
            CashFlowMetric.BALANCE -> bucket.balance
        }
    }

    AppCard {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ChartCardHeader(title = "收支趋势")
            StatisticsFilterRow(
                options = CashFlowMetric.values().map { it.label },
                selected = selectedMetric.label,
                onSelect = { label ->
                    CashFlowMetric.values().firstOrNull { it.label == label }?.let(onMetricSelect)
                }
            )
            LineChart(
                labels = buckets.map { it.label },
                series = listOf(
                    LineSeries(
                        label = selectedMetric.label,
                        color = color,
                        values = values,
                        actualCount = chartActualPointCount(period, range, values.size, today)
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
        }
    }
}

@Composable
private fun CategoryDistributionCard(
    selectedMetric: CategoryMetric,
    onMetricSelect: (CategoryMetric) -> Unit,
    expenseCategories: List<CategorySlice>,
    incomeCategories: List<CategorySlice>
) {
    val slices = topCategorySlices(
        if (selectedMetric == CategoryMetric.EXPENSE) expenseCategories else incomeCategories,
        limit = 8
    )
    val total = slices.sumOf { it.amount }
    val colors = categoryColors(
        primary = MaterialTheme.colorScheme.primary,
        error = MaterialTheme.colorScheme.error,
        accent = if (selectedMetric == CategoryMetric.EXPENSE) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }
    )

    AppCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "分类",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                StatisticsFilterRow(
                    options = CategoryMetric.values().map { it.label },
                    selected = selectedMetric.label,
                    onSelect = { label ->
                        CategoryMetric.values().firstOrNull { it.label == label }?.let(onMetricSelect)
                    }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                CategoryDonutChart(
                    slices = slices,
                    colors = colors,
                    modifier = Modifier.size(224.dp)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatMoney(total),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = if (selectedMetric == CategoryMetric.EXPENSE) "总支出" else "总收入",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (slices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无${selectedMetric.label}记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    slices.forEachIndexed { index, slice ->
                        CategoryDetailRow(
                            slice = slice,
                            total = total,
                            color = colors[index % colors.size]
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryDetailRow(
    slice: CategorySlice,
    total: Long,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        CategoryIcon(
            categoryId = slice.categoryId,
            categoryName = slice.name,
            modifier = Modifier
                .width(38.dp)
                .padding(start = 8.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = slice.name,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = formatMoney(slice.amount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = percentText(slice.amount, total),
            modifier = Modifier
                .width(50.dp)
                .padding(start = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1
        )
        Text(
            text = "›",
            modifier = Modifier.width(20.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun TransactionRankingCard(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    selectedMetric: RankingMetric,
    onMetricSelect: (RankingMetric) -> Unit,
    onEdit: (TransactionEntity) -> Unit
) {
    val type = if (selectedMetric == RankingMetric.EXPENSE) {
        TransactionType.EXPENSE
    } else {
        TransactionType.INCOME
    }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val topTransactions = transactions
        .filter { it.type == type }
        .sortedWith(
            compareByDescending<TransactionEntity> { it.amountInCents }
                .thenByDescending { it.date }
                .thenByDescending { it.time }
                .thenByDescending { it.createdAt }
        )
        .take(10)

    AppCard {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "单笔排行",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                StatisticsFilterRow(
                    options = RankingMetric.values().map { it.label },
                    selected = selectedMetric.label,
                    onSelect = { label ->
                        RankingMetric.values().firstOrNull { it.label == label }?.let(onMetricSelect)
                    }
                )
            }
            if (topTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无${selectedMetric.label}记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column {
                    topTransactions.forEachIndexed { index, transaction ->
                        RankingTransactionRow(
                            rank = index + 1,
                            transaction = transaction,
                            category = transaction.categoryId?.let(categoryMap::get),
                            metric = selectedMetric,
                            onClick = { onEdit(transaction) }
                        )
                        if (index < topTransactions.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(start = 44.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingTransactionRow(
    rank: Int,
    transaction: TransactionEntity,
    category: CategoryEntity?,
    metric: RankingMetric,
    onClick: () -> Unit
) {
    val rankColor = when (rank) {
        1 -> Color(0xFFFF951A)
        2 -> Color(0xFF9B9DA5)
        3 -> Color(0xFFB98B68)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    }
    val rankTextColor = if (rank <= 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val title = transaction.note.trim().ifBlank {
        category?.name ?: if (metric == RankingMetric.EXPENSE) "未分类支出" else "未分类收入"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(rankColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = rankTextColor
            )
        }
        CategoryIcon(
            categoryId = category?.id,
            categoryName = category?.name,
            modifier = Modifier
                .width(36.dp)
                .padding(start = 6.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp, end = 6.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = transaction.date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            text = formatMoney(transaction.amountInCents),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = "›",
            modifier = Modifier.width(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun AssetTrendCard(
    period: StatisticsPeriod,
    range: StatisticsRange,
    today: LocalDate,
    points: List<AssetPoint>,
    selectedMetric: AssetMetric,
    onMetricSelect: (AssetMetric) -> Unit
) {
    val color = when (selectedMetric) {
        AssetMetric.NET_WORTH -> MaterialTheme.colorScheme.primary
        AssetMetric.TOTAL_ASSETS -> BalanceColor
        AssetMetric.TOTAL_LIABILITIES -> MaterialTheme.colorScheme.error
    }
    val currentValue = points.lastOrNull()?.let { point ->
        when (selectedMetric) {
            AssetMetric.NET_WORTH -> point.netWorth
            AssetMetric.TOTAL_ASSETS -> point.totalAssets
            AssetMetric.TOTAL_LIABILITIES -> point.totalLiabilities
        }
    } ?: 0L
    val values = points.map { point ->
        when (selectedMetric) {
            AssetMetric.NET_WORTH -> point.netWorth
            AssetMetric.TOTAL_ASSETS -> point.totalAssets
            AssetMetric.TOTAL_LIABILITIES -> point.totalLiabilities
        }
    }

    AppCard {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ChartCardHeader(
                title = "资产趋势",
                trailing = formatMoney(currentValue),
                trailingColor = color
            )
            StatisticsFilterRow(
                options = AssetMetric.values().map { it.label },
                selected = selectedMetric.label,
                onSelect = { label ->
                    AssetMetric.values().firstOrNull { it.label == label }?.let(onMetricSelect)
                }
            )
            LineChart(
                labels = points.map { it.label },
                series = listOf(
                    LineSeries(
                        label = selectedMetric.label,
                        color = color,
                        values = values,
                        actualCount = chartActualPointCount(period, range, values.size, today)
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
        }
    }
}

@Composable
private fun ChartCardHeader(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    trailingColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = if (trailing == null) 0.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = trailingColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LineChart(
    labels: List<String>,
    series: List<LineSeries>,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val emptyColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
    val displaySeries = series.map { line ->
        line.copy(values = line.displayValues())
    }
    val values = displaySeries.flatMap { it.values }
    val defaultSelectedIndex = displaySeries.firstOrNull()?.let { line ->
        val availableCount = minOf(labels.size, line.values.size)
        if (availableCount == 0) {
            null
        } else {
            val actualCount = line.actualCount.coerceIn(0, availableCount)
            if (actualCount > 0) actualCount - 1 else 0
        }
    }
    var selectedIndex by remember(labels, series) { mutableStateOf(defaultSelectedIndex) }

    Column(modifier = modifier) {
        if (labels.isEmpty() || values.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无趋势数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = emptyColor
                )
            }
        } else {
            val rawMinValue = values.minOrNull() ?: 0L
            val rawMaxValue = values.maxOrNull() ?: 0L
            val constantSeriesPadding = if (rawMinValue == rawMaxValue) {
                maxOf(abs(rawMaxValue) / 10L, 100L)
            } else {
                0L
            }
            val minValue = rawMinValue - constantSeriesPadding
            val maxValue = rawMaxValue + constantSeriesPadding
            val selectedLine = displaySeries.firstOrNull()
            val selectedInfo = selectedIndex
                ?.takeIf { it in labels.indices }
                ?.let { index ->
                    val valueText = selectedLine?.let { line ->
                        if (index < line.actualCount.coerceIn(0, line.values.size)) {
                            line.values.getOrNull(index)?.let(::formatMoney)
                        } else {
                            "暂无数据"
                        }
                    } ?: "暂无数据"
                    "${labels[index]} · ${selectedLine?.label.orEmpty()} $valueText"
                }
            if (selectedInfo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .padding(start = ChartYAxisWidth, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    selectedLine?.let { line ->
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(line.color, CircleShape)
                        )
                    }
                    Text(
                        text = selectedInfo,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                ChartYAxisLabels(minValue = minValue, maxValue = maxValue)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 2.dp)
                            .pointerInput(labels, displaySeries) {
                                detectTapGestures { offset ->
                                    if (labels.isNotEmpty()) {
                                        val plotLeft = ChartHorizontalInset.toPx()
                                        val plotWidth = (size.width.toFloat() - plotLeft * 2f).coerceAtLeast(1f)
                                        val fraction = ((offset.x - plotLeft) / plotWidth).coerceIn(0f, 1f)
                                        selectedIndex = if (labels.size == 1) {
                                            0
                                        } else {
                                            (fraction * labels.lastIndex).roundToInt()
                                        }
                                    }
                                }
                            }
                            .semantics {
                                contentDescription = "趋势图，点击查看数据"
                            }
                    ) {
                        val plotLeft = ChartHorizontalInset.toPx()
                        val plotRight = size.width - ChartHorizontalInset.toPx()
                        val plotTop = 10f
                        val plotBottom = size.height - 10f
                        val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
                        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
                        val valueRange = (maxValue - minValue).coerceAtLeast(1L).toFloat()
                        val gridPathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))

                        fun pointAt(lineValues: List<Long>, index: Int): Offset {
                            val x = if (lineValues.size == 1) {
                                plotLeft + plotWidth / 2f
                            } else {
                                plotLeft + plotWidth * index / (lineValues.lastIndex).toFloat()
                            }
                            val y = plotTop + (maxValue - lineValues[index]).toFloat() / valueRange * plotHeight
                            return Offset(x, y)
                        }

                        fun appendSmoothPath(
                            path: Path,
                            lineValues: List<Long>,
                            fromIndex: Int,
                            toIndex: Int
                        ) {
                            if (lineValues.isEmpty() || fromIndex > toIndex) return
                            val start = pointAt(lineValues, fromIndex)
                            path.moveTo(start.x, start.y)
                            for (index in fromIndex until toIndex) {
                                val previous = pointAt(lineValues, (index - 1).coerceAtLeast(0))
                                val current = pointAt(lineValues, index)
                                val next = pointAt(lineValues, index + 1)
                                val afterNext = pointAt(
                                    lineValues,
                                    (index + 2).coerceAtMost(lineValues.lastIndex)
                                )
                                // Keep the spline inside the range of its two end points.
                                // Without this clamp, a sharp change can overshoot below zero
                                // and make a non-negative asset balance look negative.
                                val segmentMinY = minOf(current.y, next.y)
                                val segmentMaxY = maxOf(current.y, next.y)
                                val controlOne = Offset(
                                    x = current.x + (next.x - previous.x) / 6f,
                                    y = (current.y + (next.y - previous.y) / 6f)
                                        .coerceIn(segmentMinY, segmentMaxY)
                                )
                                val controlTwo = Offset(
                                    x = next.x - (afterNext.x - current.x) / 6f,
                                    y = (next.y - (afterNext.y - current.y) / 6f)
                                        .coerceIn(segmentMinY, segmentMaxY)
                                )
                                path.cubicTo(
                                    controlOne.x,
                                    controlOne.y,
                                    controlTwo.x,
                                    controlTwo.y,
                                    next.x,
                                    next.y
                                )
                            }
                        }

                        fun appendLinearPath(
                            path: Path,
                            lineValues: List<Long>,
                            fromIndex: Int,
                            toIndex: Int,
                            moveToStart: Boolean = true
                        ) {
                            if (lineValues.isEmpty() || fromIndex > toIndex) return
                            val start = pointAt(lineValues, fromIndex)
                            if (moveToStart) path.moveTo(start.x, start.y)
                            for (index in (fromIndex + 1)..toIndex) {
                                val point = pointAt(lineValues, index)
                                path.lineTo(point.x, point.y)
                            }
                        }

                        (0..3).forEach { index ->
                            val y = plotTop + plotHeight * index / 3f
                            drawLine(
                                color = gridColor,
                                start = Offset(plotLeft, y),
                                end = Offset(plotRight, y),
                                strokeWidth = 1f,
                                pathEffect = gridPathEffect
                            )
                        }

                        displaySeries.forEach { line ->
                            if (line.values.isNotEmpty()) {
                                val actualCount = line.actualCount.coerceIn(0, line.values.size)
                                val fullPath = Path()
                                if (actualCount >= 2) {
                                    appendSmoothPath(fullPath, line.values, 0, actualCount - 1)
                                    if (actualCount < line.values.size) {
                                        appendLinearPath(
                                            path = fullPath,
                                            lineValues = line.values,
                                            fromIndex = actualCount - 1,
                                            toIndex = line.values.lastIndex,
                                            moveToStart = false
                                        )
                                    }
                                } else {
                                    appendLinearPath(
                                        path = fullPath,
                                        lineValues = line.values,
                                        fromIndex = 0,
                                        toIndex = line.values.lastIndex
                                    )
                                }
                                val firstPoint = pointAt(line.values, 0)
                                val lastPoint = pointAt(line.values, line.values.lastIndex)
                                val areaPath = Path().apply {
                                    addPath(fullPath)
                                    lineTo(lastPoint.x, plotBottom)
                                    lineTo(firstPoint.x, plotBottom)
                                    close()
                                }
                                drawPath(
                                    path = areaPath,
                                    color = line.color.copy(alpha = 0.14f)
                                )

                                if (actualCount >= 2) {
                                    val actualPath = Path()
                                    appendSmoothPath(
                                        path = actualPath,
                                        lineValues = line.values,
                                        fromIndex = 0,
                                        toIndex = actualCount - 1
                                    )
                                    drawPath(
                                        path = actualPath,
                                        color = line.color,
                                        style = Stroke(
                                            width = 4f,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }

                                if (actualCount == 0 && line.values.size > 1) {
                                    val futurePath = Path()
                                    appendLinearPath(futurePath, line.values, 0, line.values.lastIndex)
                                    drawPath(
                                        path = futurePath,
                                        color = line.color,
                                        style = Stroke(
                                            width = 4f,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                                        )
                                    )
                                } else if (actualCount in 1 until line.values.size) {
                                    val futurePath = Path()
                                    appendLinearPath(
                                        futurePath,
                                        line.values,
                                        actualCount - 1,
                                        line.values.lastIndex
                                    )
                                    drawPath(
                                        path = futurePath,
                                        color = line.color,
                                        style = Stroke(
                                            width = 4f,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                                        )
                                    )
                                }

                            }
                        }

                        selectedIndex
                            ?.takeIf { it in labels.indices }
                            ?.let { index ->
                                val selectedX = if (labels.size == 1) {
                                    plotLeft + plotWidth / 2f
                                } else {
                                    plotLeft + plotWidth * index / labels.lastIndex.toFloat()
                                }
                                drawLine(
                                    color = guideColor,
                                    start = Offset(selectedX, plotTop),
                                    end = Offset(selectedX, plotBottom),
                                    strokeWidth = 2f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                                )
                                displaySeries.firstOrNull { index < it.values.size }?.let { line ->
                                    val point = pointAt(line.values, index)
                                    drawCircle(color = surfaceColor, radius = 8f, center = point)
                                    drawCircle(
                                        color = line.color,
                                        radius = 8f,
                                        center = point,
                                        style = Stroke(width = 3f)
                                    )
                                }
                            }
                    }
                }
            }
        }
        if (labels.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(ChartYAxisWidth))
                ChartLabelRow(
                    labels = labels,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun LineSeries.displayValues(): List<Long> {
    val count = actualCount.coerceIn(0, values.size)
    if (count == 0 || count == values.size) return values
    val lastActualValue = values[count - 1]
    return values.mapIndexed { index, value ->
        if (index >= count) lastActualValue else value
    }
}

@Composable
private fun ChartYAxisLabels(minValue: Long, maxValue: Long) {
    val values = (0..3).map { index ->
        val fraction = index / 3.0
        (maxValue.toDouble() - (maxValue - minValue).toDouble() * fraction).toLong()
    }
    Column(
        modifier = Modifier
            .width(ChartYAxisWidth)
            .fillMaxHeight()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End
    ) {
        values.forEach { value ->
            Text(
                text = formatChartAxisValue(value),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

private fun formatChartAxisValue(cents: Long): String {
    val yuan = cents / 100.0
    val amount = abs(yuan)
    val sign = if (yuan < 0) "-" else ""
    return when {
        amount >= 100_000_000.0 -> "$sign¥${String.format(Locale.CHINA, "%.1f", amount / 100_000_000.0)}亿"
        amount >= 100_000.0 -> "$sign¥${String.format(Locale.CHINA, "%.1f", amount / 10_000.0)}万"
        amount >= 1_000.0 -> "$sign¥${String.format(Locale.CHINA, "%.1f", amount / 1_000.0)}k"
        amount >= 100.0 -> "$sign¥${String.format(Locale.CHINA, "%.0f", amount)}"
        amount >= 10.0 -> "$sign¥${String.format(Locale.CHINA, "%.1f", amount)}"
        amount > 0.0 -> "$sign¥${String.format(Locale.CHINA, "%.2f", amount)}"
        else -> "¥0"
    }
}

@Composable
private fun ChartLabelRow(
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    val visibleLabels = labels.mapIndexedNotNull { index, label ->
        (index to label).takeIf { shouldShowChartLabel(index, labels.size) }
    }
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            visibleLabels.forEach { (_, label) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }
        val layoutWidth = constraints.maxWidth
        val layoutHeight = placeables.maxOfOrNull { it.height } ?: 0
        val horizontalInset = ChartHorizontalInset.roundToPx()
        val pointWidth = (layoutWidth - horizontalInset * 2).coerceAtLeast(0)
        layout(layoutWidth, layoutHeight) {
            visibleLabels.zip(placeables).forEach { (indexedLabel, placeable) ->
                val index = indexedLabel.first
                val centerX = if (labels.size <= 1) {
                    layoutWidth / 2
                } else {
                    horizontalInset +
                        (pointWidth.toFloat() * index / labels.lastIndex).roundToInt()
                }
                val x = (centerX - placeable.width / 2)
                    .coerceIn(0, (layoutWidth - placeable.width).coerceAtLeast(0))
                placeable.placeRelative(x, 0)
            }
        }
    }
}

@Composable
private fun CategoryDonutChart(
    slices: List<CategorySlice>,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    Canvas(modifier = modifier) {
        val strokeWidth = 36f
        val diameter = (minOf(size.width, size.height) - strokeWidth).coerceAtLeast(1f)
        val topLeft = Offset(
            x = (size.width - diameter) / 2f,
            y = (size.height - diameter) / 2f
        )
        val chartSize = Size(diameter, diameter)
        val total = slices.sumOf { it.amount }
        if (total <= 0L) {
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = chartSize,
                style = Stroke(width = strokeWidth)
            )
        } else {
            var startAngle = -90f
            slices.forEachIndexed { index, slice ->
                val sweep = slice.amount.toFloat() / total.toFloat() * 360f
                val visibleSweep = (sweep - 4f).coerceAtLeast(1f)
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = startAngle + 2f,
                    sweepAngle = visibleSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = chartSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
                startAngle += sweep
            }
        }
    }
}

private val BalanceColor = Color(0xFF2E9B70)

private fun categoryColors(primary: Color, error: Color, accent: Color): List<Color> = listOf(
    accent,
    Color(0xFFFF8617),
    Color(0xFFF0B400),
    Color(0xFF22BB63),
    Color(0xFF16B7A5),
    Color(0xFF347CE6),
    if (accent == error) primary else error,
    Color(0xFF8A55E8),
    Color(0xFF89909A)
)

private fun shouldShowChartLabel(index: Int, count: Int): Boolean = when {
    count <= 8 -> true
    count <= 14 -> true
    count <= 31 -> index == 0 || index == count - 1 || index % 5 == 0
    else -> index == 0 || index == count - 1 || index % (count / 6).coerceAtLeast(1) == 0
}

private fun chartActualPointCount(
    period: StatisticsPeriod,
    range: StatisticsRange,
    pointCount: Int,
    today: LocalDate = LocalDate.now()
): Int {
    val lastActualIndex = chartWindows(period, range).indexOfLast { !it.start.isAfter(today) }
    return (lastActualIndex + 1).coerceIn(0, pointCount)
}

internal fun statisticsRange(
    period: StatisticsPeriod,
    anchorDate: LocalDate,
    datedTransactions: List<DatedTransaction>,
    today: LocalDate = LocalDate.now()
): StatisticsRange {
    return when (period) {
        StatisticsPeriod.WEEK -> {
            val start = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            StatisticsRange(
                start = start,
                end = start.plusDays(6),
                label = "${start.monthValue}月${start.dayOfMonth}日 - ${start.plusDays(6).monthValue}月${start.plusDays(6).dayOfMonth}日"
            )
        }

        StatisticsPeriod.MONTH -> {
            val anchorMonth = YearMonth.from(anchorDate)
            StatisticsRange(
                start = anchorMonth.atDay(1),
                end = anchorMonth.atEndOfMonth(),
                label = formatMonth(anchorMonth.toString())
            )
        }

        StatisticsPeriod.YEAR -> {
            val anchorMonth = YearMonth.from(anchorDate)
            StatisticsRange(
                start = LocalDate.of(anchorMonth.year, 1, 1),
                end = LocalDate.of(anchorMonth.year, 12, 31),
                label = "${anchorMonth.year} 年"
            )
        }

        StatisticsPeriod.ALL -> {
            // 全部历史始终覆盖今天；只有未来记录时也不能让范围从未来开始。
            val firstTransactionDate = datedTransactions.minByOrNull { it.date }?.date ?: today
            val first = minOf(firstTransactionDate, today)
            val lastTransactionDate = datedTransactions.maxByOrNull { it.date }?.date ?: today
            val last = maxOf(lastTransactionDate, today)
            StatisticsRange(
                start = first,
                end = last,
                label = if (datedTransactions.isEmpty()) {
                    "全部历史"
                } else {
                    "${first.year}.${first.monthValue}.${first.dayOfMonth} - ${last.year}.${last.monthValue}.${last.dayOfMonth}"
                }
            )
        }
    }
}

private fun shiftStatisticsAnchor(
    anchorDate: LocalDate,
    period: StatisticsPeriod,
    amount: Long
): LocalDate = when (period) {
    StatisticsPeriod.WEEK -> anchorDate.plusWeeks(amount)
    StatisticsPeriod.MONTH -> anchorDate.plusMonths(amount)
    StatisticsPeriod.YEAR -> anchorDate.plusYears(amount)
    StatisticsPeriod.ALL -> anchorDate
}

private fun activeDayCount(range: StatisticsRange, today: LocalDate = LocalDate.now()): Long {
    val end = minOf(range.end, today)
    return if (end.isBefore(range.start)) 1L else ChronoUnit.DAYS.between(range.start, end) + 1L
}

internal fun chartWindows(
    period: StatisticsPeriod,
    range: StatisticsRange
): List<ChartWindow> {
    return when (period) {
        StatisticsPeriod.WEEK, StatisticsPeriod.MONTH -> {
            val dayCount = ChronoUnit.DAYS.between(range.start, range.end).toInt() + 1
            (0 until dayCount.coerceAtLeast(1)).map { offset ->
                val date = range.start.plusDays(offset.toLong())
                ChartWindow(
                    label = if (period == StatisticsPeriod.WEEK) {
                        weekdayLabel(date.dayOfWeek)
                    } else {
                        "${date.monthValue}-${date.dayOfMonth}"
                    },
                    start = date,
                    end = date
                )
            }
        }

        StatisticsPeriod.YEAR -> (1..12).map { monthValue ->
            val start = LocalDate.of(range.start.year, monthValue, 1)
            ChartWindow(
                label = "${monthValue}月",
                start = start,
                end = start.with(TemporalAdjusters.lastDayOfMonth())
            )
        }

        StatisticsPeriod.ALL -> {
            val firstMonth = YearMonth.from(range.start)
            val lastMonth = YearMonth.from(range.end)
            val monthCount = ChronoUnit.MONTHS
                .between(firstMonth.atDay(1), lastMonth.atDay(1))
                .toInt()
            (0..monthCount.coerceAtLeast(0)).map { offset ->
                val month = firstMonth.plusMonths(offset.toLong())
                ChartWindow(
                    label = "${month.monthValue}月",
                    start = month.atDay(1),
                    end = month.atEndOfMonth()
                )
            }
        }
    }
}

private fun weekdayLabel(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.SUNDAY -> "周日"
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
}

internal fun statisticsBuckets(
    period: StatisticsPeriod,
    range: StatisticsRange,
    datedTransactions: List<DatedTransaction>
): List<StatisticsBucket> {
    return chartWindows(period, range).map { window ->
        val inWindow = datedTransactions.filter {
            it.date >= window.start && it.date <= window.end
        }
        StatisticsBucket(
            label = window.label,
            start = window.start,
            end = window.end,
            income = inWindow
                .filter { it.transaction.type == TransactionType.INCOME }
                .sumOf { it.transaction.amountInCents },
            expense = inWindow
                .filter { it.transaction.type == TransactionType.EXPENSE }
                .sumOf { it.transaction.amountInCents }
        )
    }
}

internal fun categorySlices(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    type: TransactionType
): List<CategorySlice> {
    val categoryMap = categories
        .filter {
            (type == TransactionType.EXPENSE && it.type == CategoryType.EXPENSE) ||
                (type == TransactionType.INCOME && it.type == CategoryType.INCOME)
        }
        .associateBy { it.id }
    return transactions
        .filter { it.type == type }
        .groupBy { transaction ->
            val category = transaction.categoryId?.let(categoryMap::get)
            category?.id?.takeUnless { category.name == "其他" } ?: "__other__"
        }
        .map { (_, categoryTransactions) ->
            val category = categoryTransactions
                .asSequence()
                .mapNotNull { it.categoryId?.let(categoryMap::get) }
                .firstOrNull()
            CategorySlice(
                name = category?.name ?: "其他",
                icon = category?.icon ?: "other",
                amount = categoryTransactions.sumOf { it.amountInCents },
                categoryId = category?.id
            )
        }
        .sortedByDescending { it.amount }
}

internal fun topCategorySlices(slices: List<CategorySlice>, limit: Int): List<CategorySlice> {
    if (limit <= 0 || slices.isEmpty()) return emptyList()
    if (slices.size <= limit) return slices

    val namedSlices = slices.filterNot { it.name == "其他" }
    val visibleNamed = namedSlices.take((limit - 1).coerceAtLeast(0))
    val otherAmount = slices
        .filter { it.name == "其他" }
        .sumOf { it.amount } + namedSlices.drop(visibleNamed.size).sumOf { it.amount }
    return if (otherAmount > 0L) {
        visibleNamed + CategorySlice("其他", "other", otherAmount)
    } else {
        visibleNamed
    }
}

internal fun assetSnapshot(accounts: List<AccountEntity>): AssetSnapshot {
    val includedAccounts = accounts.filter { it.includeInNetWorth }
    return AssetSnapshot(
        totalAssets = includedAccounts.sumOf { it.balanceInCents.coerceAtLeast(0L) },
        totalLiabilities = includedAccounts.sumOf { (-it.balanceInCents).coerceAtLeast(0L) }
    )
}

private fun reverseTransactionFromBalances(
    balances: MutableMap<String, Long>,
    transaction: TransactionEntity
) {
    fun reverse(accountId: String?, signedAmount: Long) {
        val id = accountId?.takeIf(balances::containsKey) ?: return
        balances[id] = balances.getValue(id) - signedAmount
    }

    when (transaction.type) {
        TransactionType.INCOME -> reverse(transaction.accountId, transaction.amountInCents)
        TransactionType.EXPENSE -> reverse(transaction.accountId, -transaction.amountInCents)
        TransactionType.TRANSFER -> {
            reverse(transaction.accountId, -transaction.amountInCents)
            reverse(transaction.toAccountId, transaction.amountInCents)
        }
    }
}

private fun assetSnapshot(balances: Map<String, Long>): AssetSnapshot = AssetSnapshot(
    totalAssets = balances.values.sumOf { it.coerceAtLeast(0L) },
    totalLiabilities = balances.values.sumOf { (-it).coerceAtLeast(0L) }
)

internal fun assetTrendPoints(
    period: StatisticsPeriod,
    range: StatisticsRange,
    datedTransactions: List<DatedTransaction>,
    accounts: List<AccountEntity>
): List<AssetPoint> {
    val windows = chartWindows(period, range)
    if (windows.isEmpty()) return emptyList()

    // Excluded accounts are omitted from the running map so their historical
    // transactions cannot leak back into the net-worth trend.
    val runningBalances = accounts
        .filter { it.includeInNetWorth }
        .associate { it.id to it.balanceInCents }
        .toMutableMap()
    datedTransactions
        .asSequence()
        .filter { it.date > range.end }
        .forEach { reverseTransactionFromBalances(runningBalances, it.transaction) }

    // 逐账户从当前余额倒推。必须先还原账户余额再按正负拆分资产和负债，
    // 否则账户透支或还清并转正时，历史的资产/负债数值会被错误地分到两边。
    val points = MutableList<AssetPoint?>(windows.size) { null }
    for (index in windows.indices.reversed()) {
        val window = windows[index]
        val snapshot = assetSnapshot(runningBalances)
        points[index] = AssetPoint(
            label = window.label,
            netWorth = snapshot.netWorth,
            totalAssets = snapshot.totalAssets,
            totalLiabilities = snapshot.totalLiabilities
        )
        datedTransactions
            .asSequence()
            .filter { it.date >= window.start && it.date <= window.end }
            .forEach { reverseTransactionFromBalances(runningBalances, it.transaction) }
    }
    return points.filterNotNull()
}
