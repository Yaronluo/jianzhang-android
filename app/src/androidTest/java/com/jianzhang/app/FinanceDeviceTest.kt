package com.jianzhang.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jianzhang.app.data.local.AppDatabase
import com.jianzhang.app.data.local.entity.AccountType
import com.jianzhang.app.data.local.entity.RecurringCycle
import com.jianzhang.app.data.local.entity.TransactionType
import com.jianzhang.app.data.model.DefaultData
import com.jianzhang.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class FinanceDeviceTest {
    @Test
    fun mainActivityIsRegisteredAsLauncher() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = instrumentation.targetContext.packageManager
            .getLaunchIntentForPackage(instrumentation.targetContext.packageName)
        assertNotNull(intent)
        assertEquals(MainActivity::class.java.name, intent?.component?.className)
    }

    @Test
    fun transactionEditDeleteKeepsAccountBalanceConsistent() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val repository = FinanceRepository(database)
        repository.ensureDefaults()
        val accountId = repository.createAccount("测试支付宝", AccountType.ALIPAY, 10_000L, "🟦")
        val firstId = repository.saveTransaction(
            transactionId = null,
            amountInCents = 1_800L,
            type = TransactionType.EXPENSE,
            categoryId = DefaultData.FOOD,
            accountId = accountId,
            date = LocalDate.now().toString(),
            time = "12:00",
            note = "午饭"
        )
        assertEquals(8_200L, database.financeDao().getAccount(accountId)?.balanceInCents)

        repository.saveTransaction(
            transactionId = firstId,
            amountInCents = 2_500L,
            type = TransactionType.INCOME,
            categoryId = DefaultData.PART_TIME,
            accountId = accountId,
            date = LocalDate.now().toString(),
            time = "13:00",
            note = "兼职"
        )
        assertEquals(12_500L, database.financeDao().getAccount(accountId)?.balanceInCents)

        repository.deleteTransaction(firstId)
        assertEquals(10_000L, database.financeDao().getAccount(accountId)?.balanceInCents)
        assertEquals(null, database.financeDao().getTransaction(firstId))
        database.close()
    }

    @Test
    fun recurringConfirmationIsDueOnlyAndDoesNotDuplicate() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val repository = FinanceRepository(database)
        repository.ensureDefaults()
        val accountId = repository.createAccount("测试现金", AccountType.CASH, 10_000L, "💵")
        val recurringId = repository.saveRecurringExpense(
            id = null,
            name = "测试订阅",
            amountInCents = 590L,
            categoryId = DefaultData.OTHER_EXPENSE,
            accountId = accountId,
            cycle = RecurringCycle.MONTHLY,
            nextDate = LocalDate.now().minusDays(1).toString(),
            note = "测试"
        )

        assertTrue(repository.confirmRecurringExpense(recurringId))
        assertFalse(repository.confirmRecurringExpense(recurringId))
        assertEquals(9_410L, database.financeDao().getAccount(accountId)?.balanceInCents)
        val transactions = database.financeDao().observeTransactions().first()
        assertEquals(1, transactions.size)
        assertNotNull(transactions.first().sourceRecurringExpenseId)
        database.close()
    }

    @Test
    fun transferMovesMoneyWithoutChangingIncomeOrExpense() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val repository = FinanceRepository(database)
        val fromId = repository.createAccount("转出账户", AccountType.BANK_CARD, 10_000L, "🏦")
        val toId = repository.createAccount("转入账户", AccountType.ALIPAY, 2_000L, "🟦")

        val transferId = repository.saveTransfer(
            transactionId = null,
            amountInCents = 3_500L,
            fromAccountId = fromId,
            toAccountId = toId,
            date = LocalDate.now().toString(),
            time = "09:00",
            note = "生活费转入"
        )
        assertEquals(6_500L, database.financeDao().getAccount(fromId)?.balanceInCents)
        assertEquals(5_500L, database.financeDao().getAccount(toId)?.balanceInCents)

        repository.saveTransfer(transferId, 1_500L, fromId, toId, LocalDate.now().toString(), "10:00", "调整转账")
        assertEquals(8_500L, database.financeDao().getAccount(fromId)?.balanceInCents)
        assertEquals(3_500L, database.financeDao().getAccount(toId)?.balanceInCents)
        assertEquals(TransactionType.TRANSFER, database.financeDao().observeTransactions().first().firstOrNull()?.type)

        repository.deleteTransaction(transferId)
        assertEquals(10_000L, database.financeDao().getAccount(fromId)?.balanceInCents)
        assertEquals(2_000L, database.financeDao().getAccount(toId)?.balanceInCents)
        assertTrue(database.financeDao().observeTransactions().first().isEmpty())
        database.close()
    }

    @Test
    fun accountUsedAsTransferDestinationIsNotDeleted() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val repository = FinanceRepository(database)
        val fromId = repository.createAccount("转出账户", AccountType.BANK_CARD, 10_000L, "🏦")
        val toId = repository.createAccount("仅转入账户", AccountType.ALIPAY, 0L, "🟦")

        repository.saveTransfer(null, 1_000L, fromId, toId, LocalDate.now().toString(), "09:00", "测试转入")
        repository.removeAccount(toId)

        val destination = database.financeDao().getAccount(toId)
        assertNotNull(destination)
        assertFalse(destination?.enabled ?: true)
        database.close()
    }

    @Test
    fun editingAccountDetailsDoesNotFlipAnOverdrawnBalance() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val repository = FinanceRepository(database)
        val accountId = repository.createAccount("现金", AccountType.CASH, 0L, "💵")

        repository.saveTransaction(
            transactionId = null,
            amountInCents = 1_000L,
            type = TransactionType.EXPENSE,
            categoryId = DefaultData.FOOD,
            accountId = accountId,
            date = LocalDate.now().toString(),
            time = "12:00",
            note = "透支测试"
        )
        val overdrawnAccount = requireNotNull(database.financeDao().getAccount(accountId))
        repository.updateAccount(overdrawnAccount.copy(name = "备用现金"))

        assertEquals(-1_000L, database.financeDao().getAccount(accountId)?.balanceInCents)
        database.close()
    }

    @Test
    fun clearingDemoDataPreservesRealTransactionBalanceEffect() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val repository = FinanceRepository(database)
        repository.ensureDefaults()
        repository.generateDemoData()
        val alipay = database.financeDao().getDemoAccounts().first { it.type == AccountType.ALIPAY }

        val realTransactionId = repository.saveTransaction(
            transactionId = null,
            amountInCents = 1_000L,
            type = TransactionType.EXPENSE,
            categoryId = DefaultData.FOOD,
            accountId = alipay.id,
            date = LocalDate.now().toString(),
            time = "18:00",
            note = "真实账单"
        )

        repository.clearDemoData()

        assertEquals(119_000L, database.financeDao().getAccount(alipay.id)?.balanceInCents)
        assertFalse(database.financeDao().getAccount(alipay.id)?.enabled ?: true)
        assertNotNull(database.financeDao().getTransaction(realTransactionId))
        assertTrue(database.financeDao().observeTransactions().first().none { it.isDemo })
        database.close()
    }

    @Test
    fun budgetsCanBeReadAcrossMonthsAndZeroRemovesThem() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val repository = FinanceRepository(database)

        repository.setMonthlyBudget("2026-08", 100_000L)
        repository.setMonthlyBudget("2026-09", 200_000L)
        assertEquals(2, repository.budgets.first().size)

        repository.setMonthlyBudget("2026-08", 0L)
        repository.setCategoryBudget("2026-09", DefaultData.FOOD, 50_000L)
        assertEquals(2, repository.budgets.first().size)

        repository.setCategoryBudget("2026-09", DefaultData.FOOD, 0L)
        val remaining = repository.budgets.first()
        assertEquals(1, remaining.size)
        assertEquals("2026-09", remaining.single().month)
        assertEquals(null, remaining.single().categoryId)
        database.close()
    }
}
