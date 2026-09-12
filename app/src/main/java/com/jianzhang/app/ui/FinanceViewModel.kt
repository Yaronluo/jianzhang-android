package com.jianzhang.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jianzhang.app.data.local.AppDatabase
import com.jianzhang.app.data.local.entity.AccountEntity
import com.jianzhang.app.data.local.entity.AccountType
import com.jianzhang.app.data.local.entity.BudgetEntity
import com.jianzhang.app.data.local.entity.CategoryEntity
import com.jianzhang.app.data.local.entity.RecurringCycle
import com.jianzhang.app.data.local.entity.RecurringExpenseEntity
import com.jianzhang.app.data.local.entity.TransactionEntity
import com.jianzhang.app.data.local.entity.TransactionType
import com.jianzhang.app.data.repository.FinanceRepository
import com.jianzhang.app.util.parseMoneyToCents
import com.jianzhang.app.util.parseMoneyToCentsOrZero
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class FinanceUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val recurringExpenses: List<RecurringExpenseEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val monthlyBudget: BudgetEntity? = null,
    val selectedMonth: String = YearMonth.now().toString()
)

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FinanceRepository(AppDatabase.getInstance(application))
    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val selectedMonth = MutableStateFlow(YearMonth.now().toString())
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val _onboardingVisible = MutableStateFlow(!preferences.getBoolean(KEY_ONBOARDING_COMPLETED, false))
    private val _themeMode = MutableStateFlow(preferences.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM)
    val events = _events.asSharedFlow()
    val onboardingVisible: StateFlow<Boolean> = _onboardingVisible
    val themeMode: StateFlow<String> = _themeMode

    val uiState: StateFlow<FinanceUiState> = combine(
        repository.transactions,
        repository.accounts,
        repository.categories,
        repository.recurringExpenses,
        selectedMonth.flatMapLatest(repository::observeBudgets)
    ) { transactions, accounts, categories, recurringExpenses, budgets ->
        FinanceUiState(
            transactions = transactions,
            accounts = accounts,
            categories = categories,
            recurringExpenses = recurringExpenses,
            budgets = budgets,
            monthlyBudget = budgets.firstOrNull { it.categoryId == null },
            selectedMonth = selectedMonth.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinanceUiState())

    init {
        viewModelScope.launch { repository.ensureDefaults() }
    }

    fun setMonth(month: String) {
        if (runCatching { YearMonth.parse(month) }.isSuccess) selectedMonth.value = month
    }

    fun previousMonth() {
        selectedMonth.value = YearMonth.parse(selectedMonth.value).minusMonths(1).toString()
    }

    fun nextMonth() {
        selectedMonth.value = YearMonth.parse(selectedMonth.value).plusMonths(1).toString()
    }

    fun saveTransaction(
        transactionId: String?,
        amountText: String,
        type: TransactionType,
        categoryId: String?,
        accountId: String?,
        date: String,
        note: String,
        toAccountId: String? = null
    ) {
        val amount = parseMoneyToCents(amountText)
        if (amount == null) {
            _events.tryEmit("请输入正确的金额")
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.saveTransaction(
                    transactionId = transactionId,
                    amountInCents = amount,
                    type = type,
                    categoryId = categoryId,
                    accountId = accountId,
                    toAccountId = toAccountId,
                    date = date,
                    time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                    note = note.trim()
                )
            }.onFailure { _events.emit(it.message ?: "保存失败，请稍后再试") }
        }
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteTransaction(transactionId) }
                .onFailure { _events.emit(it.message ?: "删除失败，请稍后再试") }
        }
    }

    fun createAccount(name: String, type: AccountType, initialBalanceText: String, icon: String, isLiability: Boolean = false) {
        val balance = parseMoneyToCents(initialBalanceText) ?: 0L
        viewModelScope.launch {
            runCatching { repository.createAccount(name.trim(), type, if (isLiability) -balance else balance, icon, isLiability) }
                .onFailure { _events.emit(it.message ?: "账户创建失败") }
        }
    }

    fun createRecommendedAccount(name: String, type: AccountType, icon: String) {
        viewModelScope.launch {
            runCatching { repository.createAccount(name, type, 0L, icon) }
                .onFailure { _events.emit(it.message ?: "账户创建失败") }
        }
    }

    fun finishOnboarding() {
        preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
        _onboardingVisible.value = false
    }

    fun createOnboardingAccount(name: String, type: AccountType, icon: String) {
        createRecommendedAccount(name, type, icon)
        finishOnboarding()
    }

    fun setThemeMode(mode: String) {
        if (mode !in setOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK)) return
        preferences.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeMode.value = mode
    }

    fun removeAccount(accountId: String) {
        viewModelScope.launch {
            runCatching { repository.removeAccount(accountId) }
                .onFailure { _events.emit(it.message ?: "账户操作失败") }
        }
    }

    fun updateAccount(account: AccountEntity, name: String, type: AccountType, icon: String) {
        viewModelScope.launch {
            runCatching {
                repository.updateAccount(
                    account.copy(
                        name = name.trim(),
                        type = type,
                        icon = icon,
                        isLiability = type == AccountType.CREDIT_CARD
                    )
                )
            }.onFailure { _events.emit(it.message ?: "账户保存失败") }
        }
    }

    fun adjustAccountBalance(account: AccountEntity, balanceText: String) {
        val balance = parseMoneyToCentsOrZero(balanceText)
        if (balance == null) {
            _events.tryEmit("请输入不小于 0 的余额")
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.adjustAccountBalance(account.id, if (account.isLiability) -balance else balance)
            }.onFailure { _events.emit(it.message ?: "余额调整失败") }
        }
    }

    fun saveTransfer(
        transactionId: String?,
        amountText: String,
        fromAccountId: String?,
        toAccountId: String?,
        date: String,
        note: String
    ) {
        val amount = parseMoneyToCents(amountText)
        if (amount == null) {
            _events.tryEmit("请输入正确的转账金额")
            return
        }
        if (fromAccountId == null || toAccountId == null || fromAccountId == toAccountId) {
            _events.tryEmit("请选择两个不同的账户")
            return
        }
        if (runCatching { LocalDate.parse(date) }.isFailure) {
            _events.tryEmit("日期格式应为 2026-09-12")
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.saveTransfer(
                    transactionId = transactionId,
                    amountInCents = amount,
                    fromAccountId = fromAccountId,
                    toAccountId = toAccountId,
                    date = date,
                    time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                    note = note.trim()
                )
            }.onFailure { _events.emit(it.message ?: "转账保存失败") }
        }
    }

    fun saveRecurringExpense(
        id: String?,
        name: String,
        amountText: String,
        categoryId: String?,
        accountId: String?,
        cycle: RecurringCycle,
        nextDate: String,
        note: String
    ) {
        val amount = parseMoneyToCents(amountText)
        if (amount == null) {
            _events.tryEmit("请输入正确的金额")
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.saveRecurringExpense(id, name.trim(), amount, categoryId, accountId, cycle, nextDate, note.trim())
            }.onFailure { _events.emit(it.message ?: "固定支出保存失败") }
        }
    }

    fun confirmRecurringExpense(id: String) {
        viewModelScope.launch {
            runCatching { repository.confirmRecurringExpense(id) }
                .onSuccess { created -> _events.emit(if (created) "已确认记账，账户余额已更新" else "该周期已经记过账") }
                .onFailure { _events.emit(it.message ?: "确认记账失败") }
        }
    }

    fun deleteRecurringExpense(id: String) {
        viewModelScope.launch {
            runCatching { repository.deleteRecurringExpense(id) }
                .onFailure { _events.emit(it.message ?: "固定支出删除失败") }
        }
    }

    fun setMonthlyBudget(amountText: String) {
        val amount = if (amountText.isBlank()) 0L else parseMoneyToCents(amountText)
        if (amount == null) {
            _events.tryEmit("请输入正确的预算金额")
            return
        }
        viewModelScope.launch {
            runCatching { repository.setMonthlyBudget(selectedMonth.value, amount) }
                .onFailure { _events.emit(it.message ?: "预算保存失败") }
        }
    }

    fun setCategoryBudget(categoryId: String, amountText: String) {
        val amount = if (amountText.isBlank()) 0L else parseMoneyToCentsOrZero(amountText)
        if (amount == null) {
            _events.tryEmit("请输入正确的分类预算")
            return
        }
        viewModelScope.launch {
            runCatching { repository.setCategoryBudget(selectedMonth.value, categoryId, amount) }
                .onFailure { _events.emit(it.message ?: "分类预算保存失败") }
        }
    }

    fun generateDemoData() {
        viewModelScope.launch {
            runCatching { repository.generateDemoData() }
                .onSuccess { _events.emit("演示数据已生成") }
                .onFailure { _events.emit(it.message ?: "演示数据生成失败") }
        }
    }

    fun clearDemoData() {
        viewModelScope.launch {
            runCatching { repository.clearDemoData() }
                .onSuccess { _events.emit("演示数据已清除，真实数据未受影响") }
                .onFailure { _events.emit(it.message ?: "演示数据清除失败") }
        }
    }

    fun selectedDateOrToday(date: String?): String = date ?: LocalDate.now().toString()

    fun exportText(): String {
        val state = uiState.value
        val categoryNames = state.categories.associate { it.id to it.name }
        val accountNames = state.accounts.associate { it.id to it.name }
        return buildString {
            appendLine("简账数据导出")
            appendLine("导出时间,${LocalDate.now()}")
            appendLine()
            appendLine("日期,时间,类型,金额,分类,转出账户,转入账户,备注")
            state.transactions.sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.time }).forEach { item ->
                val type = when (item.type) {
                    TransactionType.INCOME -> "收入"
                    TransactionType.EXPENSE -> "支出"
                    TransactionType.TRANSFER -> "转账"
                }
                appendLine(listOf(
                    item.date,
                    item.time,
                    type,
                    com.jianzhang.app.util.formatInputMoney(item.amountInCents),
                    categoryNames[item.categoryId].orEmpty(),
                    accountNames[item.accountId].orEmpty(),
                    accountNames[item.toAccountId].orEmpty(),
                    item.note
                ).joinToString(",") { csvEscape(it) })
            }
        }
    }

    private fun csvEscape(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        private const val PREFERENCES_NAME = "jianzhang.preferences"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
