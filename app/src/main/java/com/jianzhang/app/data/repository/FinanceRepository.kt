package com.jianzhang.app.data.repository

import androidx.room.withTransaction
import com.jianzhang.app.data.local.AppDatabase
import com.jianzhang.app.data.local.FinanceDao
import com.jianzhang.app.data.local.entity.AccountEntity
import com.jianzhang.app.data.local.entity.AccountType
import com.jianzhang.app.data.local.entity.BudgetEntity
import com.jianzhang.app.data.local.entity.RecurringCycle
import com.jianzhang.app.data.local.entity.RecurringExpenseEntity
import com.jianzhang.app.data.local.entity.TransactionEntity
import com.jianzhang.app.data.local.entity.TransactionType
import com.jianzhang.app.data.model.DefaultData
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class FinanceRepository(private val database: AppDatabase) {
    private val dao: FinanceDao = database.financeDao()

    val transactions: Flow<List<TransactionEntity>> = dao.observeTransactions()
    val accounts: Flow<List<AccountEntity>> = dao.observeAccounts()
    val categories = dao.observeCategories()
    val recurringExpenses: Flow<List<RecurringExpenseEntity>> = dao.observeRecurringExpenses()
    val budgets: Flow<List<BudgetEntity>> = dao.observeAllBudgets()

    suspend fun ensureDefaults() {
        dao.insertCategories(DefaultData.categories)
        DefaultData.categories.forEach { category ->
            dao.updateCategoryPresentation(category.id, category.icon, category.sortOrder)
        }
    }

    suspend fun saveTransaction(
        transactionId: String?,
        amountInCents: Long,
        type: TransactionType,
        categoryId: String?,
        accountId: String?,
        toAccountId: String? = null,
        date: String,
        time: String,
        note: String,
        sourceRecurringExpenseId: String? = null
    ): String {
        require(amountInCents > 0) { "金额必须大于 0" }
        require(type != TransactionType.TRANSFER || (accountId != null && toAccountId != null && accountId != toAccountId)) {
            "转账必须选择两个不同的账户"
        }
        val now = System.currentTimeMillis()
        val id = transactionId ?: newId("transaction")

        database.withTransaction {
            val old: TransactionEntity? = if (transactionId == null) null else dao.getTransaction(transactionId)
            if (old != null) {
                reverseTransactionEffect(old, now)
            }

            val transaction = TransactionEntity(
                id = id,
                amountInCents = amountInCents,
                type = type,
                categoryId = categoryId,
                accountId = accountId,
                toAccountId = toAccountId ?: old?.toAccountId,
                date = date,
                time = time,
                note = note,
                sourceRecurringExpenseId = sourceRecurringExpenseId ?: old?.sourceRecurringExpenseId,
                isDemo = old?.isDemo ?: false,
                createdAt = old?.createdAt ?: now,
                updatedAt = now
            )
            if (old == null) {
                dao.insertTransaction(transaction)
            } else {
                dao.updateTransaction(transaction)
            }
            applyTransactionEffect(transaction, now)
        }
        return id
    }

    suspend fun deleteTransaction(transactionId: String) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            val transaction = dao.getTransaction(transactionId) ?: return@withTransaction
            reverseTransactionEffect(transaction, now)
            dao.deleteTransaction(transaction)
        }
    }

    suspend fun createAccount(
        name: String,
        type: AccountType,
        initialBalanceInCents: Long,
        icon: String,
        isLiability: Boolean = false,
        isDemo: Boolean = false,
        currencyCode: String = "CNY",
        note: String = "",
        includeInNetWorth: Boolean = true,
        canUseForTransactions: Boolean = true
    ): String {
        val now = System.currentTimeMillis()
        val id = newId("account")
        dao.insertAccount(
            AccountEntity(
                id = id,
                name = name,
                type = type,
                balanceInCents = initialBalanceInCents,
                icon = icon,
                isLiability = isLiability,
                isDemo = isDemo,
                createdAt = now,
                updatedAt = now,
                currencyCode = currencyCode,
                note = note,
                includeInNetWorth = includeInNetWorth,
                canUseForTransactions = canUseForTransactions
            )
        )
        return id
    }

    suspend fun updateAccount(account: AccountEntity) {
        database.withTransaction {
            val current = dao.getAccount(account.id) ?: return@withTransaction
            val normalizedBalance = if (current.isLiability == account.isLiability) {
                current.balanceInCents
            } else if (account.isLiability) {
                -kotlin.math.abs(current.balanceInCents)
            } else {
                kotlin.math.abs(current.balanceInCents)
            }
            dao.updateAccount(account.copy(balanceInCents = normalizedBalance, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun adjustAccountBalance(accountId: String, balanceInCents: Long) {
        dao.setAccountBalance(accountId, balanceInCents, System.currentTimeMillis())
    }

    suspend fun saveTransfer(
        transactionId: String?,
        amountInCents: Long,
        fromAccountId: String,
        toAccountId: String,
        date: String,
        time: String,
        note: String
    ): String = saveTransaction(
        transactionId = transactionId,
        amountInCents = amountInCents,
        type = TransactionType.TRANSFER,
        categoryId = null,
        accountId = fromAccountId,
        toAccountId = toAccountId,
        date = date,
        time = time,
        note = note
    )

    suspend fun removeAccount(accountId: String) {
        val now = System.currentTimeMillis()
        if (dao.countTransactionsForAccount(accountId) == 0) {
            dao.deleteAccount(accountId)
        } else {
            dao.disableAccount(accountId, now)
        }
    }

    suspend fun saveRecurringExpense(
        id: String?,
        name: String,
        amountInCents: Long,
        categoryId: String?,
        accountId: String?,
        cycle: RecurringCycle,
        nextDate: String,
        note: String
    ): String {
        require(amountInCents > 0) { "金额必须大于 0" }
        val expenseId = id ?: newId("recurring")
        val old: RecurringExpenseEntity? = if (id == null) null else dao.getRecurringExpense(id)
        val expense = RecurringExpenseEntity(
            id = expenseId,
            name = name,
            amountInCents = amountInCents,
            categoryId = categoryId,
            accountId = accountId,
            cycle = cycle,
            nextDate = nextDate,
            note = note,
            enabled = true,
            isDemo = old?.isDemo ?: false
        )
        if (old == null) dao.insertRecurringExpense(expense) else dao.updateRecurringExpense(expense)
        return expenseId
    }

    suspend fun deleteRecurringExpense(id: String) {
        dao.deleteRecurringExpense(id)
    }

    suspend fun confirmRecurringExpense(recurringId: String): Boolean {
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        var created = false
        database.withTransaction {
            val recurring = dao.getRecurringExpense(recurringId) ?: return@withTransaction
            val dueDate = runCatching { LocalDate.parse(recurring.nextDate) }.getOrNull() ?: today
            if (dueDate.isAfter(today)) return@withTransaction
            val transactionId = "recurring-${recurring.id}-${dueDate}"
            val existing = dao.getTransaction(transactionId)
                ?: dao.findRecurringTransaction(recurring.id, dueDate.toString())
            if (existing != null) {
                // Also advance an old record left by a previous app version, while never
                // creating a second transaction for the same recurring cycle.
                dao.updateRecurringExpense(recurring.copy(nextDate = advanceDate(dueDate, recurring.cycle)))
                return@withTransaction
            }
            val transaction = TransactionEntity(
                    id = transactionId,
                    amountInCents = recurring.amountInCents,
                    type = TransactionType.EXPENSE,
                    categoryId = recurring.categoryId,
                    accountId = recurring.accountId,
                    date = dueDate.toString(),
                    time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                    note = if (recurring.note.isBlank()) recurring.name else recurring.note,
                    sourceRecurringExpenseId = recurring.id,
                    isDemo = recurring.isDemo,
                    createdAt = now,
                    updatedAt = now
                )
            val inserted = dao.insertTransaction(transaction)
            if (inserted <= 0L) return@withTransaction
            applyTransactionEffect(transaction, now)
            created = true
            dao.updateRecurringExpense(recurring.copy(nextDate = advanceDate(dueDate, recurring.cycle)))
        }
        return created
    }

    suspend fun setMonthlyBudget(month: String, amountInCents: Long) {
        require(amountInCents >= 0) { "预算不能为负数" }
        val old = dao.getMonthlyBudget(month)
        if (amountInCents == 0L) {
            old?.let { dao.deleteBudget(it.id) }
        } else if (old == null) {
            dao.insertBudget(BudgetEntity(month = month, amountInCents = amountInCents))
        } else {
            dao.updateBudget(old.copy(amountInCents = amountInCents, isDemo = false))
        }
    }

    suspend fun setCategoryBudget(month: String, categoryId: String, amountInCents: Long) {
        require(amountInCents >= 0) { "预算不能为负数" }
        val old = dao.getCategoryBudget(month, categoryId)
        if (amountInCents == 0L) {
            old?.let { dao.deleteBudget(it.id) }
        } else if (old == null) {
            dao.insertBudget(BudgetEntity(month = month, categoryId = categoryId, amountInCents = amountInCents))
        } else {
            dao.updateBudget(old.copy(amountInCents = amountInCents, isDemo = false))
        }
    }

    suspend fun clearDemoData() {
        database.withTransaction { clearDemoDataInternal() }
    }

    suspend fun generateDemoData() {
        database.withTransaction {
            clearDemoDataInternal()
            val today = LocalDate.now()
            val now = System.currentTimeMillis()
            val cashId = newId("demo-cash")
            val alipayId = newId("demo-alipay")
            val bankId = newId("demo-bank")
            val creditId = newId("demo-credit")
            dao.insertAccount(AccountEntity(cashId, "现金", AccountType.CASH, 80000, "💵", false, true, now, now))
            dao.insertAccount(AccountEntity(alipayId, "支付宝", AccountType.ALIPAY, 120000, "🟦", false, true, now, now))
            dao.insertAccount(AccountEntity(bankId, "银行卡", AccountType.BANK_CARD, 500000, "🏦", false, true, now, now))
            dao.insertAccount(AccountEntity(creditId, "信用卡", AccountType.CREDIT_CARD, -120000, "💳", true, true, now, now))

            val demoTransactions = listOf(
                demoTransaction(320000, TransactionType.INCOME, DefaultData.SALARY, bankId, today.withDayOfMonth(1), "月度生活费"),
                demoTransaction(1800, TransactionType.EXPENSE, DefaultData.FOOD, alipayId, today.minusDays(1), "早餐"),
                demoTransaction(2200, TransactionType.EXPENSE, DefaultData.FOOD, alipayId, today.minusDays(3), "午饭"),
                demoTransaction(3350, TransactionType.EXPENSE, DefaultData.TRANSPORT, alipayId, today.minusDays(5), "打车"),
                demoTransaction(16800, TransactionType.EXPENSE, DefaultData.SHOPPING, creditId, today.minusDays(7), "换季衣物"),
                demoTransaction(9800, TransactionType.EXPENSE, DefaultData.ENTERTAINMENT, alipayId, today.minusDays(9), "电影和聚餐"),
                demoTransaction(5800, TransactionType.EXPENSE, DefaultData.STUDY, bankId, today.minusDays(11), "课程资料"),
                demoTransaction(60000, TransactionType.EXPENSE, DefaultData.HOUSING, bankId, today.minusDays(20), "房租"),
                demoTransaction(300000, TransactionType.INCOME, DefaultData.LIVING_EXPENSE, bankId, today.minusMonths(1).withDayOfMonth(1), "上月生活费"),
                demoTransaction(6180, TransactionType.EXPENSE, DefaultData.FOOD, alipayId, today.minusMonths(1).withDayOfMonth(8), "上月聚餐"),
                demoTransaction(46300, TransactionType.EXPENSE, DefaultData.SHOPPING, bankId, today.minusMonths(1).withDayOfMonth(12), "上月购物"),
                demoTransaction(32800, TransactionType.EXPENSE, DefaultData.ENTERTAINMENT, alipayId, today.minusMonths(1).withDayOfMonth(18), "上月娱乐"),
                demoTransaction(23200, TransactionType.EXPENSE, DefaultData.TRANSPORT, alipayId, today.minusMonths(1).withDayOfMonth(22), "上月出行")
            )
            demoTransactions.forEach { transaction ->
                dao.insertTransaction(transaction)
                applyTransactionEffect(transaction, now)
            }

            val demoTransfer = TransactionEntity(
                id = "demo-transfer",
                amountInCents = 50000,
                type = TransactionType.TRANSFER,
                categoryId = null,
                accountId = bankId,
                toAccountId = alipayId,
                date = today.minusDays(2).toString(),
                time = "09:00",
                note = "银行卡转入支付宝",
                isDemo = true,
                createdAt = now,
                updatedAt = now
            )
            dao.insertTransaction(demoTransfer)
            applyTransactionEffect(demoTransfer, now)

            dao.insertRecurringExpense(
                RecurringExpenseEntity(
                    id = "demo-rent",
                    name = "房租",
                    amountInCents = 60000,
                    categoryId = DefaultData.HOUSING,
                    accountId = bankId,
                    cycle = RecurringCycle.MONTHLY,
                    nextDate = today.withDayOfMonth(1).toString(),
                    note = "每月房租",
                    isDemo = true
                )
            )
            dao.insertRecurringExpense(
                RecurringExpenseEntity(
                    id = "demo-phone",
                    name = "手机套餐",
                    amountInCents = 5900,
                    categoryId = DefaultData.OTHER_EXPENSE,
                    accountId = alipayId,
                    cycle = RecurringCycle.MONTHLY,
                    nextDate = today.plusDays(3).toString(),
                    note = "手机套餐费",
                    isDemo = true
                )
            )
            insertDemoBudgetIfAbsent(today.toString().substring(0, 7), 200000)
            insertDemoBudgetIfAbsent(today.minusMonths(1).toString().substring(0, 7), 220000)
            insertDemoCategoryBudgetIfAbsent(today.toString().substring(0, 7), DefaultData.FOOD, 70000)
        }
    }

    private suspend fun clearDemoDataInternal() {
        val now = System.currentTimeMillis()
        dao.getDemoTransactions().forEach { reverseTransactionEffect(it, now) }
        dao.deleteDemoTransactions()
        dao.deleteDemoRecurringExpenses()
        dao.deleteDemoBudgets()
        dao.getDemoAccounts().forEach { account ->
            if (dao.countTransactionsForAccount(account.id) == 0) {
                dao.deleteAccount(account.id)
            } else {
                dao.disableAccount(account.id, now)
            }
        }
    }

    private suspend fun insertDemoBudgetIfAbsent(month: String, amountInCents: Long) {
        val existing = dao.getMonthlyBudget(month)
        when {
            existing == null -> dao.insertBudget(BudgetEntity(month = month, amountInCents = amountInCents, isDemo = true))
            existing.isDemo -> dao.updateBudget(existing.copy(amountInCents = amountInCents))
        }
    }

    private suspend fun insertDemoCategoryBudgetIfAbsent(month: String, categoryId: String, amountInCents: Long) {
        if (dao.getCategoryBudget(month, categoryId) == null) {
            dao.insertBudget(BudgetEntity(month = month, categoryId = categoryId, amountInCents = amountInCents, isDemo = true))
        }
    }

    private suspend fun applyTransactionEffect(transaction: TransactionEntity, now: Long, multiplier: Long = 1L) {
        when (transaction.type) {
            TransactionType.INCOME -> transaction.accountId?.let { accountId ->
                dao.adjustAccountBalance(accountId, transaction.amountInCents * multiplier, now)
            }
            TransactionType.EXPENSE -> transaction.accountId?.let { accountId ->
                dao.adjustAccountBalance(accountId, -transaction.amountInCents * multiplier, now)
            }
            TransactionType.TRANSFER -> {
                transaction.accountId?.let { accountId ->
                    dao.adjustAccountBalance(accountId, -transaction.amountInCents * multiplier, now)
                }
                transaction.toAccountId?.let { accountId ->
                    dao.adjustAccountBalance(accountId, transaction.amountInCents * multiplier, now)
                }
            }
        }
    }

    private suspend fun reverseTransactionEffect(transaction: TransactionEntity, now: Long) {
        applyTransactionEffect(transaction, now, multiplier = -1L)
    }

    private fun advanceDate(date: LocalDate, cycle: RecurringCycle): String = when (cycle) {
        RecurringCycle.MONTHLY -> date.plusMonths(1).toString()
        RecurringCycle.YEARLY -> date.plusYears(1).toString()
    }

    private fun demoTransaction(
        amountInCents: Long,
        type: TransactionType,
        categoryId: String,
        accountId: String,
        date: LocalDate,
        note: String
    ) = TransactionEntity(
        id = newId("demo-transaction"),
        amountInCents = amountInCents,
        type = type,
        categoryId = categoryId,
        accountId = accountId,
        date = date.toString(),
        time = "12:30",
        note = note,
        isDemo = true,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    private fun newId(prefix: String): String = "$prefix-${UUID.randomUUID()}"
}
