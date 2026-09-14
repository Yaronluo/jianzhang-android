package com.jianzhang.app.domain.parser

import com.jianzhang.app.data.local.entity.TransactionType
import com.jianzhang.app.data.model.DefaultData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

class NaturalLanguageParserTest {
    private val today = LocalDate.of(2026, 9, 12)

    @Test
    fun parsesExpenseWithCategoryAndDate() {
        val result = NaturalLanguageParser.parse("昨天打车去学校花了26", today)

        assertNotNull(result)
        assertEquals(2600L, result?.amountInCents)
        assertEquals(TransactionType.EXPENSE, result?.type)
        assertEquals("expense_transport", result?.categoryId)
        assertEquals("2026-09-11", result?.date)
    }

    @Test
    fun parsesIncomeWithoutOnlineModel() {
        val result = NaturalLanguageParser.parse("今天兼职赚了120", today)

        assertNotNull(result)
        assertEquals(12000L, result?.amountInCents)
        assertEquals(TransactionType.INCOME, result?.type)
        assertEquals("income_part_time", result?.categoryId)
        assertEquals("2026-09-12", result?.date)
    }

    @Test
    fun rejectsTextWithoutAmount() {
        assertEquals(null, NaturalLanguageParser.parse("今天吃饭", today))
    }

    @Test
    fun ignoresDateAndTimeNumbersWhenParsingAmount() {
        val result = NaturalLanguageParser.parse("9月12日 12:30 打车花26元", today)

        assertNotNull(result)
        assertEquals(2_600L, result?.amountInCents)
        assertEquals("2026-09-12", result?.date)
    }

    @Test
    fun parsesThousandsSeparator() {
        val result = NaturalLanguageParser.parse("收到工资1,200.50元", today)

        assertNotNull(result)
        assertEquals(120_050L, result?.amountInCents)
        assertEquals(TransactionType.INCOME, result?.type)
    }

    @Test
    fun parsesMonthDayFromEndOfLongerMonth() {
        val result = NaturalLanguageParser.parse("2月1日买书20元", LocalDate.of(2026, 1, 31))

        assertNotNull(result)
        assertEquals("2026-02-01", result?.date)
    }

    @Test
    fun parsesExpandedExpenseCategories() {
        val cases = listOf(
            "手机话费花了1元" to DefaultData.COMMUNICATION,
            "买衣服花了1元" to DefaultData.CLOTHING,
            "日用品花了1元" to DefaultData.DAILY,
            "家庭用品花了1元" to DefaultData.FAMILY,
            "健身花了1元" to DefaultData.SPORTS,
            "买宠物粮花了1元" to DefaultData.PET,
            "旅游花了1元" to DefaultData.TRAVEL,
            "汽车加油花了1元" to DefaultData.CAR,
            "水电费花了1元" to DefaultData.UTILITIES,
            "会员费花了1元" to DefaultData.SUBSCRIPTION,
            "随礼花了1元" to DefaultData.SOCIAL,
            "保费花了1元" to DefaultData.INSURANCE
        )

        cases.forEach { (text, categoryId) ->
            assertEquals(text, categoryId, NaturalLanguageParser.parse(text, today)?.categoryId)
        }
    }

    @Test
    fun parsesExpandedIncomeCategories() {
        val cases = listOf(
            "收到投资收益1元" to DefaultData.INVESTMENT_INCOME,
            "收到红包1元" to DefaultData.RED_PACKET,
            "收到报销款1元" to DefaultData.REIMBURSEMENT,
            "收到退款1元" to DefaultData.REFUND,
            "收到租金1元" to DefaultData.RENT,
            "别人转账给我1元" to DefaultData.TRANSFER_INCOME
        )

        cases.forEach { (text, categoryId) ->
            assertEquals(text, categoryId, NaturalLanguageParser.parse(text, today)?.categoryId)
        }
    }
}
