package com.jianzhang.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jianzhang.app.data.local.entity.AccountEntity
import com.jianzhang.app.data.local.entity.CategoryEntity
import com.jianzhang.app.data.local.entity.CategoryType
import com.jianzhang.app.data.local.entity.TransactionType
import com.jianzhang.app.domain.parser.NaturalLanguageParser
import com.jianzhang.app.domain.parser.ParsedTransaction
import com.jianzhang.app.ui.FinanceUiState
import com.jianzhang.app.util.formatDate
import com.jianzhang.app.util.formatInputMoney
import com.jianzhang.app.util.formatMoney
import com.jianzhang.app.util.parseMoneyToCents
import java.time.LocalDate

private val entryTypes = listOf(
    TransactionType.EXPENSE to "支出",
    TransactionType.INCOME to "收入",
    TransactionType.TRANSFER to "转账"
)

@Composable
fun AddTransactionScreen(
    state: FinanceUiState,
    transactionId: String?,
    onBack: () -> Unit,
    onSave: (String?, String, TransactionType, String?, String?, String, String) -> Unit,
    onSaveTransfer: (String?, String, String?, String?, String, String) -> Unit
) {
    val editing = remember(state.transactions, transactionId) {
        state.transactions.firstOrNull { it.id == transactionId }
    }
    var typeName by rememberSaveable(transactionId) {
        mutableStateOf(editing?.type?.name ?: TransactionType.EXPENSE.name)
    }
    var amountText by rememberSaveable(transactionId) {
        mutableStateOf(editing?.let { formatInputMoney(it.amountInCents) } ?: "")
    }
    var categoryId by rememberSaveable(transactionId) { mutableStateOf(editing?.categoryId) }
    var accountId by rememberSaveable(transactionId) { mutableStateOf(editing?.accountId) }
    var fromAccountId by rememberSaveable(transactionId) { mutableStateOf(editing?.accountId) }
    var toAccountId by rememberSaveable(transactionId) { mutableStateOf(editing?.toAccountId) }
    var date by rememberSaveable(transactionId) { mutableStateOf(editing?.date ?: LocalDate.now().toString()) }
    var note by rememberSaveable(transactionId) { mutableStateOf(editing?.note ?: "") }
    var showDateDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showNaturalDialog by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }
    var accountsError by remember { mutableStateOf(false) }
    val type = runCatching { TransactionType.valueOf(typeName) }.getOrDefault(TransactionType.EXPENSE)
    val categories = state.categories.filter {
        it.type == if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
    }
    val amountColor = when (type) {
        TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
        TransactionType.INCOME, TransactionType.TRANSFER -> MaterialTheme.colorScheme.primary
    }

    LaunchedEffect(categories, type) {
        if (type != TransactionType.TRANSFER && (categoryId == null || categories.none { it.id == categoryId })) {
            categoryId = categories.firstOrNull()?.id
        }
    }
    LaunchedEffect(state.accounts, type) {
        val validIds = state.accounts.mapTo(mutableSetOf()) { it.id }
        if (type == TransactionType.TRANSFER) {
            if (fromAccountId !in validIds) {
                fromAccountId = accountId?.takeIf { it in validIds } ?: state.accounts.firstOrNull()?.id
            }
            if (toAccountId !in validIds || toAccountId == fromAccountId) {
                toAccountId = state.accounts.firstOrNull { it.id != fromAccountId }?.id
            }
        } else if (accountId !in validIds) {
            accountId = state.accounts.firstOrNull()?.id
        }
    }

    if (showDateDialog) {
        DateEditorDialog(
            initialDate = date,
            onDismiss = { showDateDialog = false },
            onConfirm = {
                date = it
                dateError = false
                showDateDialog = false
            }
        )
    }
    if (showNoteDialog) {
        NoteEditorDialog(
            initialNote = note,
            onDismiss = { showNoteDialog = false },
            onConfirm = {
                note = it
                showNoteDialog = false
            }
        )
    }
    if (showNaturalDialog) {
        NaturalEntryDialog(
            state = state,
            onDismiss = { showNaturalDialog = false },
            onApply = { result ->
                amountText = formatInputMoney(result.amountInCents)
                typeName = result.type.name
                categoryId = result.categoryId
                date = result.date
                note = result.note
                amountError = false
                dateError = false
                showNaturalDialog = false
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EntryHeader(
                selectedType = type,
                allowTransfer = editing == null,
                onTypeSelected = {
                    typeName = it.name
                    amountError = false
                    accountsError = false
                },
                onBack = onBack,
                onNaturalEntry = { showNaturalDialog = true }
            )

            if (type == TransactionType.TRANSFER) {
                TransferAccountArea(
                    accounts = state.accounts,
                    fromAccountId = fromAccountId,
                    toAccountId = toAccountId,
                    onFromSelected = {
                        fromAccountId = it
                        if (toAccountId == it) {
                            toAccountId = state.accounts.firstOrNull { account -> account.id != it }?.id
                        }
                        accountsError = false
                    },
                    onToSelected = {
                        toAccountId = it
                        accountsError = false
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                CategoryArea(
                    categories = categories,
                    selectedCategoryId = categoryId,
                    onCategorySelected = { categoryId = it },
                    modifier = Modifier.weight(1f)
                )
            }

            EntryMetadataRow(
                accounts = state.accounts,
                selectedAccountId = accountId,
                type = type,
                date = date,
                note = note,
                onAccountSelected = { accountId = it },
                onEditDate = { showDateDialog = true },
                onEditNote = { showNoteDialog = true }
            )

            AmountDisplay(
                amountText = amountText,
                type = type,
                color = amountColor,
                amountError = amountError,
                dateError = dateError,
                accountsError = accountsError
            )

            AmountKeypad(
                type = type,
                isEditing = editing != null,
                onToken = {
                    amountText = appendAmountToken(amountText, it)
                    amountError = false
                },
                onBackspace = {
                    amountText = amountText.dropLast(1)
                    amountError = false
                },
                onClear = {
                    amountText = ""
                    amountError = false
                },
                onDone = {
                    amountError = parseMoneyToCents(amountText) == null
                    dateError = runCatching { LocalDate.parse(date) }.isFailure
                    accountsError = type == TransactionType.TRANSFER &&
                        (fromAccountId == null || toAccountId == null || fromAccountId == toAccountId)
                    if (!amountError && !dateError && !accountsError) {
                        if (type == TransactionType.TRANSFER) {
                            onSaveTransfer(transactionId, amountText, fromAccountId, toAccountId, date, note)
                        } else {
                            onSave(transactionId, amountText, type, categoryId, accountId, date, note)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun EntryHeader(
    selectedType: TransactionType,
    allowTransfer: Boolean,
    onTypeSelected: (TransactionType) -> Unit,
    onBack: () -> Unit,
    onNaturalEntry: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        ) {
            Row(modifier = Modifier.padding(4.dp)) {
                entryTypes.filter { allowTransfer || it.first != TransactionType.TRANSFER }.forEach { (type, label) ->
                    val selected = selectedType == type
                    Surface(
                        onClick = { onTypeSelected(type) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation = if (selected) 2.dp else 0.dp
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(vertical = 9.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (selectedType == TransactionType.TRANSFER) {
            Spacer(modifier = Modifier.size(48.dp))
        } else {
            IconButton(onClick = onNaturalEntry, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "一句话记账", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CategoryArea(
    categories: List<CategoryEntity>,
    selectedCategoryId: String?,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Text(
            "选择分类",
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories, key = { it.id }) { category ->
                val selected = category.id == selectedCategoryId
                Surface(
                    onClick = { onCategorySelected(category.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) else null
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(category.icon, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            category.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferAccountArea(
    accounts: List<AccountEntity>,
    fromAccountId: String?,
    toAccountId: String?,
    onFromSelected: (String) -> Unit,
    onToSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("选择转账账户", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "账户之间的资金转移不会计入收入或支出。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TransferAccountPicker(
            label = "转出账户",
            accounts = accounts,
            selectedId = fromAccountId,
            excludedId = toAccountId,
            onSelected = onFromSelected
        )
        TransferAccountPicker(
            label = "转入账户",
            accounts = accounts,
            selectedId = toAccountId,
            excludedId = fromAccountId,
            onSelected = onToSelected
        )
        if (accounts.size < 2) {
            Text("至少需要两个账户才能转账。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TransferAccountPicker(
    label: String,
    accounts: List<AccountEntity>,
    selectedId: String?,
    excludedId: String?,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedId }
    Box {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        selected?.let { "${it.icon} ${it.name} · ${formatMoney(it.balanceInCents)}" } ?: "请选择账户",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.filter { it.id != excludedId }.forEach { account ->
                DropdownMenuItem(
                    text = { Text("${account.icon} ${account.name} · ${formatMoney(account.balanceInCents)}") },
                    onClick = {
                        onSelected(account.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun EntryMetadataRow(
    accounts: List<AccountEntity>,
    selectedAccountId: String?,
    type: TransactionType,
    date: String,
    note: String,
    onAccountSelected: (String?) -> Unit,
    onEditDate: () -> Unit,
    onEditNote: () -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (type != TransactionType.TRANSFER) {
            item {
                AccountMetadataChip(
                    accounts = accounts,
                    selectedAccountId = selectedAccountId,
                    onSelected = onAccountSelected
                )
            }
        }
        item {
            MetadataChip(text = "📅 ${formatDate(date)}", onClick = onEditDate)
        }
        item {
            MetadataChip(
                text = if (note.isBlank()) "📝 添加备注" else "📝 ${note.take(10)}${if (note.length > 10) "…" else ""}",
                onClick = onEditNote
            )
        }
    }
}

@Composable
private fun AccountMetadataChip(
    accounts: List<AccountEntity>,
    selectedAccountId: String?,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedAccountId }
    Box {
        MetadataChip(
            text = selected?.let { "${it.icon} ${it.name}" } ?: "💳 未指定账户",
            onClick = { expanded = true },
            trailingArrow = true
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("未指定账户") },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text("${account.icon} ${account.name}") },
                    onClick = {
                        onSelected(account.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MetadataChip(text: String, onClick: () -> Unit, trailingArrow: Boolean = false) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, style = MaterialTheme.typography.labelMedium)
            if (trailingArrow) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun AmountDisplay(
    amountText: String,
    type: TransactionType,
    color: Color,
    amountError: Boolean,
    dateError: Boolean,
    accountsError: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            when (type) {
                TransactionType.EXPENSE -> "支出金额"
                TransactionType.INCOME -> "收入金额"
                TransactionType.TRANSFER -> "转账金额"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "¥${amountText.ifBlank { "0" }}",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1
        )
        val error = when {
            amountError -> "请输入大于 0 的金额"
            dateError -> "日期格式应为 2026-09-12"
            accountsError -> "请选择两个不同的账户"
            else -> null
        }
        Text(
            text = error ?: " ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun AmountKeypad(
    type: TransactionType,
    isEditing: Boolean,
    onToken: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "00")
    )
    val doneColor = if (type == TransactionType.EXPENSE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val doneContentColor = if (type == TransactionType.EXPENSE) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(268.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.44f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(3f).fillMaxHeight()) {
            rows.forEach { row ->
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    row.forEach { token ->
                        KeypadTextButton(
                            text = token,
                            onClick = { onToken(token) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.width(104.dp).fillMaxHeight()) {
            KeypadIconButton(onClick = onBackspace, modifier = Modifier.weight(1f))
            KeypadTextButton(
                text = "清零",
                onClick = onClear,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.labelLarge
            )
            Surface(
                onClick = onDone,
                modifier = Modifier
                    .weight(2f)
                    .fillMaxWidth()
                    .padding(4.dp),
                shape = RoundedCornerShape(20.dp),
                color = doneColor,
                contentColor = doneContentColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (isEditing) "保存" else "完成",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineSmall
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = textStyle, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun KeypadIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "删除一位")
        }
    }
}

@Composable
private fun DateEditorDialog(initialDate: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by rememberSaveable(initialDate) { mutableStateOf(initialDate) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记账日期") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; error = false },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("日期") },
                    isError = error
                )
                TextButton(onClick = { value = LocalDate.now().toString(); error = false }) { Text("使用今天") }
                if (error) Text("格式：2026-09-12", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = runCatching { LocalDate.parse(value) }.isFailure
                if (!error) onConfirm(value)
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun NoteEditorDialog(initialNote: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by rememberSaveable(initialNote) { mutableStateOf(initialNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("备注") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                placeholder = { Text("写点说明，方便以后回想") }
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.trim()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun NaturalEntryDialog(
    state: FinanceUiState,
    onDismiss: () -> Unit,
    onApply: (ParsedTransaction) -> Unit
) {
    var value by rememberSaveable { mutableStateOf("") }
    var parsed by remember { mutableStateOf<ParsedTransaction?>(null) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
        title = { Text("一句话记账") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("例如：今天中午吃饭花了18块", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        parsed = null
                        error = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3,
                    placeholder = { Text("描述金额、用途和时间") },
                    isError = error
                )
                parsed?.let { result ->
                    val category = state.categories.firstOrNull { it.id == result.categoryId }?.name ?: "其他"
                    Text(
                        "${formatInputMoney(result.amountInCents)} 元 · ${if (result.type == TransactionType.INCOME) "收入" else "支出"} · $category",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (error) Text("没有识别到有效金额，请换一种说法。", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val result = parsed
                    if (result != null) {
                        onApply(result)
                    } else {
                        parsed = NaturalLanguageParser.parse(value)
                        error = parsed == null
                    }
                },
                enabled = value.isNotBlank()
            ) { Text(if (parsed == null) "识别" else "应用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

internal fun appendAmountToken(current: String, token: String): String {
    var result = current
    token.forEach { character ->
        result = when {
            character == '.' && result.contains('.') -> result
            character == '.' && result.isBlank() -> "0."
            character == '.' -> "$result."
            !character.isDigit() -> result
            result.contains('.') && result.substringAfter('.').length >= 2 -> result
            !result.contains('.') && result.trimStart('0').length >= 9 -> result
            result == "0" -> character.toString()
            else -> result + character
        }
    }
    return result
}
