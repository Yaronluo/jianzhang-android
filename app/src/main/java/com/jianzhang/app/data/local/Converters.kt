package com.jianzhang.app.data.local

import androidx.room.TypeConverter
import com.jianzhang.app.data.local.entity.AccountType
import com.jianzhang.app.data.local.entity.CategoryType
import com.jianzhang.app.data.local.entity.RecurringCycle
import com.jianzhang.app.data.local.entity.TransactionType

class Converters {
    @TypeConverter
    fun transactionTypeToString(value: TransactionType?): String? = value?.name

    @TypeConverter
    fun stringToTransactionType(value: String?): TransactionType? = value?.let(TransactionType::valueOf)

    @TypeConverter
    fun categoryTypeToString(value: CategoryType?): String? = value?.name

    @TypeConverter
    fun stringToCategoryType(value: String?): CategoryType? = value?.let(CategoryType::valueOf)

    @TypeConverter
    fun accountTypeToString(value: AccountType?): String? = value?.name

    @TypeConverter
    fun stringToAccountType(value: String?): AccountType? = value?.let(AccountType::valueOf)

    @TypeConverter
    fun recurringCycleToString(value: RecurringCycle?): String? = value?.name

    @TypeConverter
    fun stringToRecurringCycle(value: String?): RecurringCycle? = value?.let(RecurringCycle::valueOf)
}
