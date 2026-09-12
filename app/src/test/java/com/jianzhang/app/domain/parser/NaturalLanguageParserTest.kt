package com.jianzhang.app.domain.parser

import com.jianzhang.app.data.local.entity.TransactionType
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
}
