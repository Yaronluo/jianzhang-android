package com.jianzhang.app.ui.screens

import com.jianzhang.app.data.local.entity.AccountEntity
import com.jianzhang.app.data.local.entity.AccountType
import com.jianzhang.app.data.local.entity.TransactionEntity
import com.jianzhang.app.data.local.entity.TransactionType
import com.jianzhang.app.data.model.DefaultData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatisticsCalculationTest {
    private val today = LocalDate.of(2026, 9, 14)

    @Test
    fun yearRangeStartsInJanuaryAndAllRangeIncludesToday() {
        val oldTransaction = DatedTransaction(
            transaction = transaction("old", 100L, TransactionType.EXPENSE, date = "2026-02-14"),
            date = LocalDate.of(2026, 2, 14)
        )

        val year = statisticsRange(
            period = StatisticsPeriod.YEAR,
            anchorDate = today,
            datedTransactions = listOf(oldTransaction),
            today = today
        )
        val all = statisticsRange(
            period = StatisticsPeriod.ALL,
            anchorDate = today,
            datedTransactions = listOf(oldTransaction),
            today = today
        )

        assertEquals(LocalDate.of(2026, 1, 1), year.start)
        assertEquals(LocalDate.of(2026, 12, 31), year.end)
        assertEquals(LocalDate.of(2026, 2, 14), all.start)
        assertEquals(today, all.end)

        val futureOnly = statisticsRange(
            period = StatisticsPeriod.ALL,
            anchorDate = today,
            datedTransactions = listOf(
                DatedTransaction(
                    transaction = transaction(
                        "future",
                        100L,
                        TransactionType.EXPENSE,
                        "2026-09-20"
                    ),
                    date = LocalDate.of(2026, 9, 20)
                )
            ),
            today = today
        )
        assertEquals(today, futureOnly.start)
        assertEquals(LocalDate.of(2026, 9, 20), futureOnly.end)
    }

    @Test
    fun bucketsIgnoreTransfersAndCategoryOtherIsCombined() {
        val range = statisticsRange(
            StatisticsPeriod.WEEK,
            today,
            emptyList(),
            today
        )
        val datedTransactions = listOf(
            dated("income", 1_000L, TransactionType.INCOME, "2026-09-13", DefaultData.SALARY),
            dated("expense", 400L, TransactionType.EXPENSE, "2026-09-14", DefaultData.FOOD),
            dated("uncategorized", 300L, TransactionType.EXPENSE, "2026-09-14", null),
            dated("other", 200L, TransactionType.EXPENSE, "2026-09-14", DefaultData.OTHER_EXPENSE),
            dated("transfer", 900L, TransactionType.TRANSFER, "2026-09-14", null)
        )

        val buckets = statisticsBuckets(StatisticsPeriod.WEEK, range, datedTransactions)
        val categories = categorySlices(
            transactions = datedTransactions.map { it.transaction },
            categories = DefaultData.categories,
            type = TransactionType.EXPENSE
        )

        assertEquals(7, buckets.size)
        assertEquals(1_000L, buckets[0].income)
        assertEquals(400L + 300L + 200L, buckets[1].expense)
        assertEquals(2, categories.size)
        assertEquals(500L, categories.first { it.name == "其他" }.amount)
        assertEquals(400L, categories.first { it.name == "餐饮" }.amount)
    }

    @Test
    fun assetTrendReconstructsTheBalanceBeforeEachDay() {
        val account = AccountEntity(
            id = "cash",
            name = "现金",
            type = AccountType.CASH,
            balanceInCents = 800L,
            icon = "💵",
            createdAt = 0L,
            updatedAt = 0L
        )
        val expense = dated("expense", 200L, TransactionType.EXPENSE, today.toString(), DefaultData.FOOD, "cash")
        val range = statisticsRange(StatisticsPeriod.WEEK, today, listOf(expense), today)
        val points = assetTrendPoints(
            period = StatisticsPeriod.WEEK,
            range = range,
            datedTransactions = listOf(expense),
            accounts = listOf(account)
        )

        assertTrue(points.isNotEmpty())
        assertEquals(1_000L, points[0].totalAssets)
        assertEquals(800L, points[1].totalAssets)
        assertEquals(800L, points[1].netWorth)
    }

    @Test
    fun assetTrendReclassifiesAnAccountWhenItsBalanceCrossesZero() {
        val account = AccountEntity(
            id = "cash",
            name = "现金",
            type = AccountType.CASH,
            balanceInCents = -1_000L,
            icon = "💵",
            createdAt = 0L,
            updatedAt = 0L
        )
        val expense = dated(
            "overdraft",
            2_000L,
            TransactionType.EXPENSE,
            today.toString(),
            DefaultData.FOOD,
            "cash"
        )
        val range = statisticsRange(StatisticsPeriod.WEEK, today, listOf(expense), today)
        val points = assetTrendPoints(
            period = StatisticsPeriod.WEEK,
            range = range,
            datedTransactions = listOf(expense),
            accounts = listOf(account)
        )

        assertEquals(1_000L, points[0].totalAssets)
        assertEquals(0L, points[0].totalLiabilities)
        assertEquals(0L, points[1].totalAssets)
        assertEquals(1_000L, points[1].totalLiabilities)
        assertEquals(-1_000L, points[1].netWorth)
    }

    @Test
    fun excludedAccountDoesNotContributeToNetWorthSnapshot() {
        val included = AccountEntity(
            id = "included",
            name = "银行卡",
            type = AccountType.BANK_CARD,
            balanceInCents = 2_000L,
            icon = "🏦",
            createdAt = 0L,
            updatedAt = 0L
        )
        val excluded = included.copy(
            id = "excluded",
            name = "备用账户",
            balanceInCents = 9_000L,
            includeInNetWorth = false
        )

        val snapshot = assetSnapshot(listOf(included, excluded))

        assertEquals(2_000L, snapshot.totalAssets)
        assertEquals(0L, snapshot.totalLiabilities)
    }

    @Test
    fun topCategorySlicesKeepsOnlyOneCombinedOtherEntry() {
        val slices = listOf(
            CategorySlice("餐饮", "🍜", 900L),
            CategorySlice("其他", "📦", 800L),
            CategorySlice("交通", "🚗", 700L),
            CategorySlice("购物", "🛍", 600L)
        )

        val result = topCategorySlices(slices, limit = 3)

        assertEquals(3, result.size)
        assertEquals(1, result.count { it.name == "其他" })
        assertEquals(1_400L, result.first { it.name == "其他" }.amount)
    }

    private fun dated(
        id: String,
        amount: Long,
        type: TransactionType,
        date: String,
        categoryId: String?,
        accountId: String? = null
    ) = DatedTransaction(
        transaction = transaction(id, amount, type, date, categoryId, accountId),
        date = LocalDate.parse(date)
    )

    private fun transaction(
        id: String,
        amount: Long,
        type: TransactionType,
        date: String,
        categoryId: String? = null,
        accountId: String? = null
    ) = TransactionEntity(
        id = id,
        amountInCents = amount,
        type = type,
        categoryId = categoryId,
        accountId = accountId,
        date = date,
        time = "12:00",
        note = id,
        createdAt = 0L,
        updatedAt = 0L
    )
}
