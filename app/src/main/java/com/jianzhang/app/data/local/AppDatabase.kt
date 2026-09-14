package com.jianzhang.app.data.local

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jianzhang.app.data.local.entity.AccountEntity
import com.jianzhang.app.data.local.entity.BudgetEntity
import com.jianzhang.app.data.local.entity.CategoryEntity
import com.jianzhang.app.data.local.entity.RecurringExpenseEntity
import com.jianzhang.app.data.local.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
        RecurringExpenseEntity::class,
        BudgetEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "jianzhang.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }

        /** Adds transfer destination information without touching existing local financial data. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE transactions ADD COLUMN toAccountId TEXT")
            }
        }

        /** Stores the optional metadata and visibility switches from the asset editor. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE accounts ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'CNY'")
                database.execSQL("ALTER TABLE accounts ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE accounts ADD COLUMN includeInNetWorth INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE accounts ADD COLUMN canUseForTransactions INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
}
