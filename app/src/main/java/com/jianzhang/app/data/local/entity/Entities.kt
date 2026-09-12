package com.jianzhang.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER
}

enum class CategoryType {
    INCOME,
    EXPENSE
}

enum class AccountType {
    CASH,
    BANK_CARD,
    ALIPAY,
    WECHAT,
    CREDIT_CARD,
    OTHER
}

enum class RecurringCycle {
    MONTHLY,
    YEARLY
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val amountInCents: Long,
    val type: TransactionType,
    val categoryId: String?,
    val accountId: String?,
    /** Destination account for transfers; null for income and expense records. */
    val toAccountId: String? = null,
    val date: String,
    val time: String,
    val note: String,
    val sourceRecurringExpenseId: String? = null,
    val isDemo: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val type: CategoryType,
    val sortOrder: Int,
    val enabled: Boolean = true
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: AccountType,
    val balanceInCents: Long,
    val icon: String,
    val isLiability: Boolean = false,
    val isDemo: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val enabled: Boolean = true
)

@Entity(tableName = "recurring_expenses")
data class RecurringExpenseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val amountInCents: Long,
    val categoryId: String?,
    val accountId: String?,
    val cycle: RecurringCycle,
    val nextDate: String,
    val note: String,
    val enabled: Boolean = true,
    val isDemo: Boolean = false
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val month: String,
    val categoryId: String? = null,
    val amountInCents: Long,
    val isDemo: Boolean = false
)
