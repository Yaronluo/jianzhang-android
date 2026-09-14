package com.jianzhang.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jianzhang.app.data.local.entity.AccountEntity
import com.jianzhang.app.data.local.entity.BudgetEntity
import com.jianzhang.app.data.local.entity.CategoryEntity
import com.jianzhang.app.data.local.entity.RecurringExpenseEntity
import com.jianzhang.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, time DESC, createdAt DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransaction(id: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE isDemo = 1")
    suspend fun deleteDemoTransactions()

    @Query("SELECT * FROM transactions WHERE isDemo = 1")
    suspend fun getDemoTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM categories WHERE enabled = 1 ORDER BY sortOrder, name")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("UPDATE categories SET icon = :icon, sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateCategoryPresentation(id: String, icon: String, sortOrder: Int)

    @Query("SELECT * FROM accounts WHERE enabled = 1 ORDER BY isLiability ASC, name ASC")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccount(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE isDemo = 1")
    suspend fun getDemoAccounts(): List<AccountEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccount(account: AccountEntity)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("UPDATE accounts SET balanceInCents = balanceInCents + :delta, updatedAt = :updatedAt WHERE id = :accountId")
    suspend fun adjustAccountBalance(accountId: String, delta: Long, updatedAt: Long)

    @Query("UPDATE accounts SET balanceInCents = :balanceInCents, updatedAt = :updatedAt WHERE id = :accountId")
    suspend fun setAccountBalance(accountId: String, balanceInCents: Long, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId OR toAccountId = :accountId")
    suspend fun countTransactionsForAccount(accountId: String): Int

    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun deleteAccount(accountId: String)

    @Query("UPDATE accounts SET enabled = 0, updatedAt = :updatedAt WHERE id = :accountId")
    suspend fun disableAccount(accountId: String, updatedAt: Long)

    @Query("SELECT * FROM recurring_expenses WHERE enabled = 1 ORDER BY nextDate ASC, name ASC")
    fun observeRecurringExpenses(): Flow<List<RecurringExpenseEntity>>

    @Query("SELECT * FROM recurring_expenses WHERE id = :id LIMIT 1")
    suspend fun getRecurringExpense(id: String): RecurringExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecurringExpense(expense: RecurringExpenseEntity)

    @Update
    suspend fun updateRecurringExpense(expense: RecurringExpenseEntity)

    @Query("DELETE FROM recurring_expenses WHERE id = :id")
    suspend fun deleteRecurringExpense(id: String)

    @Query("DELETE FROM recurring_expenses WHERE isDemo = 1")
    suspend fun deleteDemoRecurringExpenses()

    @Query("SELECT * FROM transactions WHERE sourceRecurringExpenseId = :recurringId AND date = :date LIMIT 1")
    suspend fun findRecurringTransaction(recurringId: String, date: String): TransactionEntity?

    @Query("SELECT * FROM budgets WHERE month = :month AND categoryId IS NULL LIMIT 1")
    suspend fun getMonthlyBudget(month: String): BudgetEntity?

    @Query("SELECT * FROM budgets ORDER BY month DESC, id ASC")
    fun observeAllBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE month = :month AND categoryId = :categoryId LIMIT 1")
    suspend fun getCategoryBudget(month: String, categoryId: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity)

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteBudget(id: Long)

    @Query("DELETE FROM budgets WHERE isDemo = 1")
    suspend fun deleteDemoBudgets()
}
