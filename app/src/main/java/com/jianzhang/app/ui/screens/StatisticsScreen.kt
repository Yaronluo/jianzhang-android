package com.jianzhang.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jianzhang.app.data.local.entity.TransactionType
import com.jianzhang.app.ui.FinanceUiState
import com.jianzhang.app.ui.components.AppCard
import com.jianzhang.app.ui.components.BackTitleBar
import com.jianzhang.app.ui.components.EmptyState
import com.jianzhang.app.ui.components.SectionTitle
import com.jianzhang.app.util.formatMoney
import com.jianzhang.app.util.formatMonth
import com.jianzhang.app.util.formatDate
import com.jianzhang.app.util.percentText
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

@Composable
fun StatisticsScreen(
    state: FinanceUiState,
    onMonthPrevious: () -> Unit,
    onMonthNext: () -> Unit,
    onSetBudget: (String) -> Unit,
    onSetCategoryBudget: (String, String) -> Unit
) {
    var showBudgetDialog by remember { mutableStateOf(false) }
    var selectedCalendarDate by remember { mutableStateOf<String?>(null) }
    var selectedCategoryBudget by remember { mutableStateOf<String?>(null) }
    val month = runCatching { YearMonth.parse(state.selectedMonth) }.getOrDefault(YearMonth.now())
    val monthTransactions = state.transactions.filter { it.date.startsWith(state.selectedMonth) }
    val income = monthTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountInCents }
    val expense = monthTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountInCents }
    val expenseByCategory = monthTransactions
        .filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.categoryId }
        .mapValues { (_, values) -> values.sumOf { it.amountInCents } }
        .toList()
        .sortedByDescending { it.second }
    val previousMonth = month.minusMonths(1).toString()
    val previousExpense = state.transactions.filter { it.date.startsWith(previousMonth) && it.type == TransactionType.EXPENSE }.sumOf { it.amountInCents }
    val daysElapsed = when {
        month < YearMonth.now() -> month.lengthOfMonth()
        month == YearMonth.now() -> LocalDate.now().dayOfMonth
        else -> 0
    }
    val predictedExpense = if (daysElapsed > 0 && expense > 0) expense * month.lengthOfMonth().toLong() / daysElapsed else 0L
    val budget = state.monthlyBudget?.amountInCents ?: 0L
    val budgetRatio = if (budget > 0) expense.toDouble() / budget.toDouble() else 0.0

    if (showBudgetDialog) {
        BudgetDialog(
            current = state.monthlyBudget?.amountInCents,
            onDismiss = { showBudgetDialog = false },
            onSave = { amount -> onSetBudget(amount); showBudgetDialog = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        BackTitleBar(title = "统计", onBack = {}, showBack = false)
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onMonthPrevious) { Text("‹") }
                    Text(formatMonth(state.selectedMonth), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onMonthNext) { Text("›") }
                }
            }
            item {
                if (monthTransactions.isEmpty()) {
                    AppCard { EmptyState("📊", "数据还不够", "开始记账后，这里会逐渐生成你的消费分析", Modifier.padding(horizontal = 12.dp)) }
                } else {
                    AppCard {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("收支概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                StatValue("收入", formatMoney(income), MaterialTheme.colorScheme.primary)
                                StatValue("支出", formatMoney(expense), MaterialTheme.colorScheme.error)
                                StatValue("结余", formatMoney(income - expense), MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
            item {
                SectionTitle("本月预算") {
                    IconButton(onClick = { showBudgetDialog = true }) { Icon(Icons.Default.Edit, contentDescription = "编辑预算") }
                }
                AppCard(modifier = Modifier.padding(top = 8.dp)) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        if (budget <= 0) {
                            Text("还没有设置月度总预算", fontWeight = FontWeight.SemiBold)
                            Text("设置一个预算，简账会在接近上限时提醒你。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { showBudgetDialog = true }) { Text("设置预算") }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("已使用", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatMoney(expense), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("预算", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatMoney(budget), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                }
                            }
                            LinearProgressIndicator(progress = { budgetRatio.coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth())
                            Text("${percentText(expense, budget)} · ${if (budgetRatio > 1) "已超支 ${formatMoney(expense - budget)}" else "剩余 ${formatMoney(budget - expense)}"}", color = if (budgetRatio > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                SectionTitle("分类消费占比")
                if (expenseByCategory.isEmpty()) {
                    Text("暂无支出分类数据", modifier = Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    AppCard(modifier = Modifier.padding(top = 8.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                            expenseByCategory.take(6).forEachIndexed { index, (categoryId, value) ->
                                val category = state.categories.firstOrNull { it.id == categoryId }
                                CategoryBar(index + 1, category?.icon ?: "🧾", category?.name ?: "其他", value, expense)
                            }
                        }
                    }
                }
            }
            item {
                SectionTitle("分类预算")
                val expenseCategories = state.categories.filter { it.type == com.jianzhang.app.data.local.entity.CategoryType.EXPENSE }
                if (expenseCategories.isEmpty()) {
                    Text("设置分类预算后，可以更细致地控制消费。", modifier = Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    AppCard(modifier = Modifier.padding(top = 8.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            expenseCategories.forEach { category ->
                                val used = monthTransactions.filter { it.type == TransactionType.EXPENSE && it.categoryId == category.id }.sumOf { it.amountInCents }
                                val categoryBudget = state.budgets.firstOrNull { it.categoryId == category.id }
                                val limit = categoryBudget?.amountInCents ?: 0L
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${category.icon} ${category.name}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                    Text(if (limit > 0L) "${formatMoney(used)} / ${formatMoney(limit)}" else "未设置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(onClick = { selectedCategoryBudget = category.id }) { Text(if (limit > 0L) "修改" else "设置") }
                                }
                                if (limit > 0L) {
                                    LinearProgressIndicator(progress = { (used.toDouble() / limit.toDouble()).coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                            Text("留空或填 0 可移除分类预算。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                SectionTitle("消费趋势")
                TrendChart(transactions = monthTransactions, month = month)
            }
            item {
                SectionTitle("消费日历")
                SpendingCalendar(
                    month = month,
                    transactions = monthTransactions,
                    onDayClick = { selectedCalendarDate = it }
                )
            }
            item {
                SectionTitle("异常提醒")
                AnomalyCard(transactions = monthTransactions)
            }
            item {
                SectionTitle("财务洞察")
                InsightCard(
                    expense = expense,
                    income = income,
                    previousExpense = previousExpense,
                    budgetRatio = budgetRatio,
                    predictedExpense = predictedExpense,
                    budget = budget,
                    largestCategoryName = expenseByCategory.firstOrNull()?.first?.let { id -> state.categories.firstOrNull { it.id == id }?.name },
                    largestCategoryAmount = expenseByCategory.firstOrNull()?.second ?: 0L
                )
            }
        }
    }

    selectedCalendarDate?.let { date ->
        val dayTransactions = monthTransactions.filter { it.date == date && it.type == TransactionType.EXPENSE }
        AlertDialog(
            onDismissRequest = { selectedCalendarDate = null },
            title = { Text("${month.monthValue} 月 ${date.takeLast(2).toIntOrNull() ?: ""} 日") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("总支出 ${formatMoney(dayTransactions.sumOf { it.amountInCents })}", fontWeight = FontWeight.SemiBold)
                    if (dayTransactions.isEmpty()) Text("当天没有支出记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else dayTransactions.forEach { transaction -> Text("${transaction.note.ifBlank { "未命名账单" }} · ${formatMoney(transaction.amountInCents)}") }
                }
            },
            confirmButton = { TextButton(onClick = { selectedCalendarDate = null }) { Text("关闭") } }
        )
    }
    selectedCategoryBudget?.let { categoryId ->
        val category = state.categories.firstOrNull { it.id == categoryId }
        CategoryBudgetDialog(
            categoryName = category?.let { "${it.icon} ${it.name}" } ?: "分类",
            current = state.budgets.firstOrNull { it.categoryId == categoryId }?.amountInCents,
            onDismiss = { selectedCategoryBudget = null },
            onSave = { value ->
                onSetCategoryBudget(categoryId, value)
                selectedCategoryBudget = null
            }
        )
    }
}

@Composable
private fun StatValue(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun CategoryBar(index: Int, icon: String, name: String, value: Long, total: Long) {
    val ratio = if (total > 0) value.toDouble() / total.toDouble() else 0.0
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$index", modifier = Modifier.width(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$icon $name", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(formatMoney(value), fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(progress = { ratio.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(start = 24.dp))
    }
}

@Composable
private fun TrendChart(transactions: List<com.jianzhang.app.data.local.entity.TransactionEntity>, month: YearMonth) {
    val daily = transactions.filter { it.type == TransactionType.EXPENSE }.groupBy { it.date }.mapValues { (_, values) -> values.sumOf { it.amountInCents } }
    val max = daily.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    AppCard(modifier = Modifier.padding(top = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("每天支出", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                items((1..month.lengthOfMonth()).toList()) { day ->
                    val date = month.atDay(day).toString()
                    val value = daily[date] ?: 0L
                    val height = if (value == 0L) 8.dp else (18 + (value.toDouble() / max.toDouble() * 76)).dp
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.height(height).width(7.dp).clip(RoundedCornerShape(6.dp)).background(if (value == 0L) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary)
                        )
                        if (day == 1 || day == month.lengthOfMonth() || day % 5 == 0) Text(day.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpendingCalendar(
    month: YearMonth,
    transactions: List<com.jianzhang.app.data.local.entity.TransactionEntity>,
    onDayClick: (String) -> Unit
) {
    val daily = transactions.filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.date }
        .mapValues { (_, values) -> values.sumOf { it.amountInCents } }
    val max = daily.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val offset = month.atDay(1).dayOfWeek.value - 1
    val cells: List<Int?> = List(offset) { null } + (1..month.lengthOfMonth()).toList()
    AppCard(modifier = Modifier.padding(top = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                    Text(it, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(7) { index ->
                        val day = week.getOrNull(index)
                        val date = day?.let { month.atDay(it).toString() }
                        val value = date?.let { daily[it] } ?: 0L
                        val ratio = value.toDouble() / max.toDouble()
                        val cellColor = if (day == null) Color.Transparent else if (value == 0L) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f + ratio.toFloat().coerceIn(0f, 1f) * 0.8f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(cellColor)
                                .clickable(enabled = date != null) { date?.let(onDayClick) },
                            contentAlignment = Alignment.Center
                        ) { if (day != null) Text(day.toString(), style = MaterialTheme.typography.labelSmall, color = if (value > 0L && ratio > 0.55) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                    }
                    repeat(7 - week.size) {
                        Box(modifier = Modifier.weight(1f).padding(2.dp).aspectRatio(1f))
                    }
                }
            }
            Text("颜色越深，代表当天支出越多", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnomalyCard(transactions: List<com.jianzhang.app.data.local.entity.TransactionEntity>) {
    val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
    val sorted = expenses.map { it.amountInCents }.sorted()
    val average = if (sorted.isEmpty()) 0.0 else sorted.average()
    val median = when {
        sorted.isEmpty() -> 0.0
        sorted.size % 2 == 1 -> sorted[sorted.size / 2].toDouble()
        else -> (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]).toDouble() / 2.0
    }
    val anomaly = expenses.maxByOrNull { it.amountInCents }
    val threshold = maxOf(average * 2.0, median * 2.0)
    AppCard(modifier = Modifier.padding(top = 8.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            when {
                expenses.size < 3 -> {
                    Text("继续记账，样本更多后这里会检查异常消费。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                anomaly != null && anomaly.amountInCents.toDouble() > threshold -> {
                    Text("发现一笔明显高于近期平均水平的消费", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    Text("${formatDate(anomaly.date)} · ${anomaly.note.ifBlank { "未命名账单" }} · ${formatMoney(anomaly.amountInCents)}")
                }
                else -> {
                    Text("暂未发现明显异常消费", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text("当前支出波动在正常范围内。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun InsightCard(
    expense: Long,
    income: Long,
    previousExpense: Long,
    budgetRatio: Double,
    predictedExpense: Long,
    budget: Long,
    largestCategoryName: String?,
    largestCategoryAmount: Long
) {
    val changeText = when {
        previousExpense <= 0L && expense > 0L -> "本月已经开始产生支出记录。"
        previousExpense <= 0L -> "继续记账，简账会逐步看见你的消费规律。"
        else -> {
            val change = (expense - previousExpense).toDouble() / previousExpense.toDouble() * 100.0
            "本月支出较上月${if (change >= 0) "增加" else "减少"} ${String.format(Locale.CHINA, "%.1f", kotlin.math.abs(change))}%。"
        }
    }
    val savingRate = if (income > 0L) ((income - expense).toDouble() / income.toDouble() * 100.0).coerceIn(-999.0, 999.0) else null
    AppCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(changeText)
            val largestText = if (expense > 0L && largestCategoryName != null) {
                "本月最大的消费类别是$largestCategoryName，占总支出的 ${percentText(largestCategoryAmount, expense)}。"
            } else {
                "记录更多支出后，这里会显示你的主要消费类别。"
            }
            Text(largestText)
            if (savingRate != null) Text("本月当前储蓄率为 ${String.format(Locale.CHINA, "%.1f", savingRate)}%。")
            if (budget > 0L && budgetRatio >= 0.7) Text("本月预算已经使用 ${String.format(Locale.CHINA, "%.1f", budgetRatio * 100)}%，建议适当控制后续支出。", color = if (budgetRatio >= 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            if (predictedExpense > 0L) Text("按照目前的消费速度，预计本月支出约 ${formatMoney(predictedExpense)}。")
        }
    }
}

@Composable
private fun BudgetDialog(current: Long?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(current?.let { com.jianzhang.app.util.formatInputMoney(it) } ?: "") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置本月预算") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = value, onValueChange = { value = it; error = "" }, singleLine = true, label = { Text("预算金额") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (value.isNotBlank() && com.jianzhang.app.util.parseMoneyToCents(value) == null) error = "请输入正确的预算金额"
                else onSave(value)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun CategoryBudgetDialog(categoryName: String, current: Long?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable(categoryName) { mutableStateOf(current?.let { com.jianzhang.app.util.formatInputMoney(it) } ?: "") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置分类预算") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(categoryName, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = value, onValueChange = { value = it; error = "" }, singleLine = true, label = { Text("预算金额") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = error.isNotBlank())
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (value.isBlank() || com.jianzhang.app.util.parseMoneyToCentsOrZero(value) == null) error = "请输入不小于 0 的金额"
                else onSave(value)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
