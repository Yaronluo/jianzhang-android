package com.jianzhang.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jianzhang.app.data.local.entity.AccountEntity
import com.jianzhang.app.data.local.entity.CategoryEntity
import com.jianzhang.app.data.local.entity.CategoryType
import com.jianzhang.app.data.local.entity.TransactionType
import com.jianzhang.app.domain.parser.NaturalLanguageParser
import com.jianzhang.app.domain.parser.ParsedTransaction
import com.jianzhang.app.ui.FinanceUiState
import com.jianzhang.app.ui.components.CategoryIcon
import com.jianzhang.app.ui.components.AppDropdownField
import com.jianzhang.app.ui.components.AppDropdownItem
import com.jianzhang.app.ui.components.AppDropdownMenu
import com.jianzhang.app.util.formatDate
import com.jianzhang.app.util.formatInputMoney
import com.jianzhang.app.util.formatMoney
import com.jianzhang.app.util.parseMoneyToCents
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val entryTypes = listOf(
    TransactionType.EXPENSE to "支出",
    TransactionType.INCOME to "收入",
    TransactionType.TRANSFER to "转账"
)

private val EntryBackdropColor = Color(0xFFBFC0C4)
private val EntrySheetColor = Color(0xFFFCFCFE)
private val EntryMutedColor = Color(0xFF98989F)
private val EntryActionColor = Color(0xFFE9E9EE)
private val EntryExpenseColor = Color(0xFFFF555F)
private val EntryAccountColor = Color(0xFFFFF1CD)
private val EntryAccountContentColor = Color(0xFFE4A721)

@Composable
fun AddTransactionScreen(
    state: FinanceUiState,
    transactionId: String?,
    onBack: () -> Unit,
    onSave: (String?, String, TransactionType, String?, String?, String, String) -> Unit,
    onSaveTransfer: (String?, String, String?, String?, String, String) -> Unit,
    onSaveAndContinue: (String?, String, TransactionType, String?, String?, String, String) -> Unit,
    onSaveTransferAndContinue: (String?, String, String?, String?, String, String) -> Unit
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
    var defaultAccountApplied by rememberSaveable(transactionId) { mutableStateOf(editing != null) }
    var date by rememberSaveable(transactionId) { mutableStateOf(editing?.date ?: LocalDate.now().toString()) }
    var note by rememberSaveable(transactionId) { mutableStateOf(editing?.note ?: "") }
    var pendingOperand by remember { mutableStateOf<String?>(null) }
    var pendingOperator by remember { mutableStateOf<String?>(null) }
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
        TransactionType.EXPENSE -> EntryExpenseColor
        TransactionType.INCOME, TransactionType.TRANSFER -> MaterialTheme.colorScheme.primary
    }
    val availableAccounts = state.accounts.filter {
        it.canUseForTransactions || it.id == accountId || it.id == fromAccountId || it.id == toAccountId
    }
    val timeLabel = remember(transactionId) {
        editing?.time ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    fun resolvedAmount(): String? = when {
        pendingOperand == null || pendingOperator == null -> amountText
        amountText.isBlank() -> pendingOperand
        else -> calculateAmount(pendingOperand.orEmpty(), amountText, pendingOperator.orEmpty())
    }

    fun applyOperation(operator: String) {
        if (amountText.isBlank()) {
            if (pendingOperand != null) pendingOperator = operator
            return
        }
        if (parseMoneyToCents(amountText) == null) {
            amountError = true
            return
        }
        val previousOperand = pendingOperand
        val previousOperator = pendingOperator
        if (previousOperand != null && previousOperator != null) {
            val result = calculateAmount(previousOperand, amountText, previousOperator)
            if (result == null) {
                amountError = true
                return
            }
            pendingOperand = result
        } else {
            pendingOperand = amountText
        }
        amountText = ""
        pendingOperator = operator
        amountError = false
    }

    fun submit(keepEditing: Boolean) {
        val amountForSave = resolvedAmount().orEmpty()
        amountError = parseMoneyToCents(amountForSave) == null
        dateError = runCatching { LocalDate.parse(date) }.isFailure
        accountsError = type == TransactionType.TRANSFER &&
            (fromAccountId == null || toAccountId == null || fromAccountId == toAccountId)
        if (amountError || dateError || accountsError) return

        if (type == TransactionType.TRANSFER) {
            if (keepEditing) {
                onSaveTransferAndContinue(transactionId, amountForSave, fromAccountId, toAccountId, date, note)
            } else {
                onSaveTransfer(transactionId, amountForSave, fromAccountId, toAccountId, date, note)
            }
        } else {
            if (keepEditing) {
                onSaveAndContinue(transactionId, amountForSave, type, categoryId, accountId, date, note)
            } else {
                onSave(transactionId, amountForSave, type, categoryId, accountId, date, note)
            }
        }
        if (keepEditing) {
            amountText = ""
            note = ""
            pendingOperand = null
            pendingOperator = null
        }
    }

    LaunchedEffect(categories, type) {
        if (type != TransactionType.TRANSFER && (categoryId == null || categories.none { it.id == categoryId })) {
            categoryId = categories.firstOrNull()?.id
        }
    }
    LaunchedEffect(state.accounts, type, accountId, fromAccountId, toAccountId) {
        val validIds = availableAccounts.mapTo(mutableSetOf()) { it.id }
        if (type == TransactionType.TRANSFER) {
            if (fromAccountId !in validIds) {
                fromAccountId = accountId?.takeIf { it in validIds } ?: availableAccounts.firstOrNull()?.id
            }
            if (toAccountId !in validIds || toAccountId == fromAccountId) {
                toAccountId = availableAccounts.firstOrNull { it.id != fromAccountId }?.id
            }
        } else if (!defaultAccountApplied) {
            accountId = availableAccounts.firstOrNull()?.id
            defaultAccountApplied = true
        } else if (accountId != null && accountId !in validIds) {
            accountId = availableAccounts.firstOrNull()?.id
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

    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EntryBackdropColor)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarInset + 10.dp),
            shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp),
            color = EntrySheetColor
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
                    accounts = availableAccounts,
                    fromAccountId = fromAccountId,
                    toAccountId = toAccountId,
                    onFromSelected = {
                        fromAccountId = it
                        if (toAccountId == it) {
                            toAccountId = availableAccounts.firstOrNull { account -> account.id != it }?.id
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
                accounts = availableAccounts,
                selectedAccountId = accountId,
                onAccountSelected = { accountId = it },
                showAccount = type != TransactionType.TRANSFER,
                onEditDate = { showDateDialog = true },
                onEditNote = { showNoteDialog = true },
                onNaturalEntry = { showNaturalDialog = true }
            )

            AmountDisplay(
                amountText = amountText,
                color = amountColor,
                date = date,
                timeLabel = timeLabel,
                note = note,
                onEditDate = { showDateDialog = true },
                onEditNote = { showNoteDialog = true },
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
                onOperation = ::applyOperation,
                onBackspace = {
                    if (amountText.isNotEmpty()) {
                        amountText = amountText.dropLast(1)
                    } else if (pendingOperand != null) {
                        amountText = pendingOperand.orEmpty()
                        pendingOperand = null
                        pendingOperator = null
                    }
                    amountError = false
                },
                onSaveAndContinue = { submit(keepEditing = true) },
                onDone = { submit(keepEditing = false) }
            )
            }
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(42.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = EntryMutedColor
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .width(232.dp),
            shape = RoundedCornerShape(50),
            color = Color(0xFFF5F5F7),
            border = BorderStroke(1.dp, Color.White)
        ) {
            Row(modifier = Modifier.padding(3.dp)) {
                entryTypes.filter { allowTransfer || it.first != TransactionType.TRANSFER }.forEach { (type, label) ->
                    val selected = selectedType == type
                    Surface(
                        onClick = { onTypeSelected(type) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        color = if (selected) Color.White else Color.Transparent,
                        border = if (selected) BorderStroke(1.dp, Color.White) else null,
                        shadowElevation = if (selected) 1.dp else 0.dp
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(vertical = 6.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            color = if (selected) Color(0xFF252529) else EntryMutedColor
                        )
                    }
                }
            }
        }
        IconButton(
            onClick = onNaturalEntry,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(42.dp)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = "一句话记账", tint = EntryMutedColor)
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
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(categories, key = { it.id }) { category ->
                EntryCategoryTile(
                    category = category,
                    selected = category.id == selectedCategoryId,
                    onClick = { onCategorySelected(category.id) }
                )
            }
        }
    }
}

@Composable
private fun EntryCategoryTile(category: CategoryEntity, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f) else Color.Transparent,
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
        ) {
            CategoryIcon(
                categoryId = category.id,
                categoryName = category.name,
                modifier = Modifier.size(25.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                category.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
    val selected = accounts.firstOrNull { it.id == selectedId }
    AppDropdownField(
        label = label,
        value = selected?.let { "${it.icon} ${it.name}" } ?: "",
        placeholder = "请选择账户",
        modifier = Modifier.fillMaxWidth(),
        supportingText = selected?.let { formatMoney(it.balanceInCents) }
    ) { dismiss ->
        accounts.filter { it.id != excludedId }.forEach { account ->
            AppDropdownItem(
                text = "${account.icon} ${account.name}",
                supportingText = formatMoney(account.balanceInCents),
                selected = account.id == selectedId,
                onClick = {
                    onSelected(account.id)
                    dismiss()
                }
            )
        }
    }
}

@Composable
private fun EntryMetadataRow(
    accounts: List<AccountEntity>,
    selectedAccountId: String?,
    onAccountSelected: (String?) -> Unit,
    showAccount: Boolean,
    onEditDate: () -> Unit,
    onEditNote: () -> Unit,
    onNaturalEntry: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showAccount) {
            item {
                AccountMetadataChip(
                    accounts = accounts,
                    selectedAccountId = selectedAccountId,
                    onSelected = onAccountSelected
                )
            }
        }
        item {
            EntryToolItem("日期", Icons.Default.CalendarMonth, onClick = onEditDate)
        }
        item {
            EntryToolItem("备注", Icons.Default.EditNote, onClick = onEditNote)
        }
        item {
            EntryToolItem("智能", Icons.Default.AutoAwesome, onClick = onNaturalEntry)
        }
    }
}

@Composable
private fun AccountMetadataChip(
    accounts: List<AccountEntity>,
    selectedAccountId: String?,
    onSelected: (String?) -> Unit
) {
    val selected = accounts.firstOrNull { it.id == selectedAccountId }
    AppDropdownMenu(
        minMenuWidth = 220.dp,
        anchor = { onClick, expanded ->
            val arrowRotation by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                label = "account-chip-arrow"
            )
            Surface(
                onClick = onClick,
                shape = RoundedCornerShape(50),
                color = EntryAccountColor,
                contentColor = EntryAccountContentColor
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(selected?.icon ?: "💳", fontSize = 16.sp)
                    Text(
                        selected?.name ?: "选择账户",
                        modifier = Modifier.padding(start = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "选择账户",
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(18.dp)
                            .rotate(arrowRotation)
                    )
                }
            }
        },
        menuContent = { dismiss ->
            AppDropdownItem(
                text = "未指定账户",
                selected = selectedAccountId == null,
                onClick = {
                    onSelected(null)
                    dismiss()
                }
            )
            accounts.forEach { account ->
                AppDropdownItem(
                    text = "${account.icon} ${account.name}",
                    selected = account.id == selectedAccountId,
                    onClick = {
                        onSelected(account.id)
                        dismiss()
                    }
                )
            }
        }
    )
}

@Composable
private fun EntryToolItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = EntryMutedColor)
        Text(
            label,
            modifier = Modifier.padding(start = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = EntryMutedColor
        )
    }
}

@Composable
private fun AmountDisplay(
    amountText: String,
    color: Color,
    date: String,
    timeLabel: String,
    note: String,
    onEditDate: () -> Unit,
    onEditNote: () -> Unit,
    amountError: Boolean,
    dateError: Boolean,
    accountsError: Boolean
) {
    val dateLabel = if (date == LocalDate.now().toString()) timeLabel else formatDate(date)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "¥${amountText.ifBlank { "0" }}",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onEditDate)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Schedule, contentDescription = "编辑日期", modifier = Modifier.size(17.dp), tint = EntryMutedColor)
                Text(
                    dateLabel,
                    modifier = Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = EntryMutedColor
                )
            }
            Text(
                note.ifBlank { "点击填写备注" },
                modifier = Modifier
                    .padding(start = 28.dp)
                    .clickable(onClick = onEditNote)
                    .padding(vertical = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = EntryMutedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val error = when {
            amountError -> "请输入大于 0 的金额"
            dateError -> "日期格式应为 2026-09-12"
            accountsError -> "请选择两个不同的账户"
            else -> null
        }
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AmountKeypad(
    type: TransactionType,
    isEditing: Boolean,
    onToken: (String) -> Unit,
    onOperation: (String) -> Unit,
    onBackspace: () -> Unit,
    onSaveAndContinue: () -> Unit,
    onDone: () -> Unit
) {
    val numberRows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9")
    )
    val doneColor = if (type == TransactionType.EXPENSE) EntryExpenseColor else MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(252.dp)
            .background(EntrySheetColor)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            numberRows.forEach { row ->
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
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                KeypadTextButton(text = ".", onClick = { onToken(".") }, modifier = Modifier.weight(1f))
                KeypadTextButton(text = "0", onClick = { onToken("0") }, modifier = Modifier.weight(1f))
                KeypadIconButton(onClick = onBackspace, modifier = Modifier.weight(1f))
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.width(94.dp).fillMaxHeight()) {
            KeypadDualOperationButton(
                primary = "+",
                secondary = "×",
                onPrimary = { onOperation("+") },
                onSecondary = { onOperation("×") },
                modifier = Modifier.weight(1f)
            )
            KeypadDualOperationButton(
                primary = "−",
                secondary = "÷",
                onPrimary = { onOperation("-") },
                onSecondary = { onOperation("÷") },
                modifier = Modifier.weight(1f)
            )
            KeypadActionButton(
                text = if (isEditing) "保存" else "保存再记",
                onClick = if (isEditing) onDone else onSaveAndContinue,
                modifier = Modifier.weight(1f),
                color = EntryActionColor,
                contentColor = Color(0xFF2D2D33)
            )
            KeypadActionButton(
                text = "完成",
                onClick = onDone,
                modifier = Modifier.weight(1f),
                color = doneColor,
                contentColor = Color.White
            )
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
            .padding(1.dp),
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = textStyle, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun KeypadIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxSize()
            .padding(1.dp),
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "删除一位")
        }
    }
}

@Composable
private fun KeypadDualOperationButton(
    primary: String,
    secondary: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(16.dp),
        color = EntryActionColor
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = onPrimary),
                contentAlignment = Alignment.Center
            ) {
                Text(primary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = onSecondary),
                contentAlignment = Alignment.Center
            ) {
                Text(secondary, style = MaterialTheme.typography.titleMedium, color = EntryMutedColor)
            }
        }
    }
}

@Composable
private fun KeypadActionButton(
    text: String,
    onClick: () -> Unit,
    color: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(16.dp),
        color = color,
        contentColor = contentColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        }
    }
}

private fun calculateAmount(leftText: String, rightText: String, operator: String): String? {
    val left = parseMoneyToCents(leftText) ?: return null
    val right = parseMoneyToCents(rightText) ?: return null
    if (operator == "÷" && right == 0L) return null
    val result = when (operator) {
        "+" -> left + right
        "-" -> left - right
        "×" -> BigDecimal.valueOf(left)
            .multiply(BigDecimal.valueOf(right))
            .divide(BigDecimal.valueOf(100L), 0, RoundingMode.HALF_UP)
            .longValueExact()
        "÷" -> BigDecimal.valueOf(left)
            .multiply(BigDecimal.valueOf(100L))
            .divide(BigDecimal.valueOf(right), 0, RoundingMode.HALF_UP)
            .longValueExact()
        else -> return null
    }
    return result.takeIf { it > 0L }?.let(::formatInputMoney)
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
