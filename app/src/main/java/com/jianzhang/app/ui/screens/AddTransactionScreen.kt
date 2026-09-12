package com.jianzhang.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
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
import com.jianzhang.app.data.local.entity.TransactionType
import com.jianzhang.app.domain.parser.NaturalLanguageParser
import com.jianzhang.app.domain.parser.ParsedTransaction
import com.jianzhang.app.ui.FinanceUiState
import com.jianzhang.app.ui.components.AppCard
import com.jianzhang.app.ui.components.BackTitleBar
import com.jianzhang.app.ui.components.SmallSpacer
import com.jianzhang.app.util.formatInputMoney
import com.jianzhang.app.util.parseMoneyToCents
import java.time.LocalDate

@Composable
fun AddTransactionScreen(
    state: FinanceUiState,
    transactionId: String?,
    onBack: () -> Unit,
    onSave: (String?, String, TransactionType, String?, String?, String, String) -> Unit
) {
    val editing = remember(state.transactions, transactionId) {
        state.transactions.firstOrNull { it.id == transactionId }
    }
    var typeName by rememberSaveable(transactionId) { mutableStateOf(editing?.type?.name ?: TransactionType.EXPENSE.name) }
    var amountText by rememberSaveable(transactionId) { mutableStateOf(editing?.let { formatInputMoney(it.amountInCents) } ?: "") }
    var categoryId by rememberSaveable(transactionId) { mutableStateOf(editing?.categoryId) }
    var accountId by rememberSaveable(transactionId) { mutableStateOf(editing?.accountId) }
    var date by rememberSaveable(transactionId) { mutableStateOf(editing?.date ?: LocalDate.now().toString()) }
    var note by rememberSaveable(transactionId) { mutableStateOf(editing?.note ?: "") }
    var naturalText by rememberSaveable { mutableStateOf("") }
    var parsed by remember { mutableStateOf<ParsedTransaction?>(null) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }
    val type = runCatching { TransactionType.valueOf(typeName) }.getOrDefault(TransactionType.EXPENSE)
    val categories = state.categories.filter {
        it.type == if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
    }
    val selectedAccount = state.accounts.firstOrNull { it.id == accountId }

    LaunchedEffect(categories, type) {
        if (categoryId == null || categories.none { it.id == categoryId }) categoryId = categories.firstOrNull()?.id
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        BackTitleBar(title = if (editing == null) "新增账单" else "编辑账单", onBack = onBack)
        androidx.compose.foundation.lazy.LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = type == TransactionType.EXPENSE, onClick = { typeName = TransactionType.EXPENSE.name }, label = { Text("支出") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = type == TransactionType.INCOME, onClick = { typeName = TransactionType.INCOME.name }, label = { Text("收入") }, modifier = Modifier.weight(1f))
                }
            }
            item {
                AppCard {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("金额", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it; amountError = false },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = amountError,
                            textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            prefix = { Text("¥", style = MaterialTheme.typography.headlineSmall) },
                            placeholder = { Text("0.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        if (amountError) Text("请输入大于 0 的金额", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Text("分类", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    items(categories) { category ->
                        FilterChip(
                            selected = categoryId == category.id,
                            onClick = { categoryId = category.id },
                            label = { Text("${category.icon} ${category.name}") }
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("账户", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    androidx.compose.foundation.layout.Box {
                        OutlinedButton(onClick = { accountMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedAccount?.let { "${it.icon} ${it.name}" } ?: "选择账户", modifier = Modifier.weight(1f))
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = accountMenuExpanded, onDismissRequest = { accountMenuExpanded = false }) {
                            state.accounts.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text("${account.icon} ${account.name}") },
                                    onClick = { accountId = account.id; accountMenuExpanded = false }
                                )
                            }
                        }
                    }
                    if (state.accounts.isEmpty()) Text("还没有账户，保存后也可以先用未指定账户记录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = date,
                            onValueChange = { date = it; dateError = false },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = dateError,
                            label = { Text("日期") },
                            leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) }
                        )
                        if (dateError) Text("格式：2026-09-12", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { date = LocalDate.now().toString(); dateError = false }, modifier = Modifier.align(Alignment.CenterVertically)) { Text("今天") }
                }
            }
            item {
                OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("备注") }, placeholder = { Text("写点说明，方便以后回想") }, minLines = 2, maxLines = 3)
            }
            item {
                AppCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(" 一句话记账", fontWeight = FontWeight.SemiBold)
                        }
                        Text("例如：今天中午吃饭花了18块", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(value = naturalText, onValueChange = { naturalText = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("输入一句话，自动识别金额和分类") })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { parsed = NaturalLanguageParser.parse(naturalText) }, enabled = naturalText.isNotBlank()) { Text("识别") }
                            TextButton(onClick = { naturalText = "今天喝奶茶花了16块" }) { Text("填入示例") }
                        }
                        parsed?.let { result ->
                            Text("识别结果：${formatInputMoney(result.amountInCents)} 元 · ${if (result.type == TransactionType.INCOME) "收入" else "支出"} · ${state.categories.firstOrNull { it.id == result.categoryId }?.name ?: "其他"}")
                            TextButton(onClick = {
                                amountText = formatInputMoney(result.amountInCents)
                                typeName = result.type.name
                                categoryId = result.categoryId
                                date = result.date
                                note = result.note
                                parsed = null
                            }) { Text("应用识别结果") }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        amountError = parseMoneyToCents(amountText) == null
                        dateError = runCatching { LocalDate.parse(date) }.isFailure
                        if (!amountError && !dateError) onSave(transactionId, amountText, type, categoryId, accountId, date, note)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (editing == null) "确认记账" else "保存修改") }
            }
            item { SmallSpacer() }
        }
    }
}
