package com.jianzhang.app.domain.parser

import com.jianzhang.app.data.local.entity.TransactionType
import com.jianzhang.app.data.model.DefaultData
import com.jianzhang.app.util.parseMoneyToCents
import java.time.LocalDate
import java.util.regex.Pattern

data class ParsedTransaction(
    val amountInCents: Long,
    val type: TransactionType,
    val categoryId: String,
    val date: String,
    val note: String
)

object NaturalLanguageParser {
    private val amountPattern = Pattern.compile("(?:¥\\s*)?([0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:元|块钱|块|人民币)?")
    private val monthDayPattern = Pattern.compile("(\\d{1,2})月(\\d{1,2})日?")
    private val datePattern = Pattern.compile("(?:\\d{4}年)?\\d{1,2}月\\d{1,2}日?|\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}")
    private val timePattern = Pattern.compile("\\d{1,2}:\\d{2}")

    fun parse(text: String, today: LocalDate = LocalDate.now()): ParsedTransaction? {
        if (text.isBlank()) return null
        // Remove dates and times before looking for an amount, otherwise input such as
        // "9月12日打车花26元" would incorrectly treat the month as the amount.
        val amountText = timePattern.matcher(datePattern.matcher(text).replaceAll(" ")).replaceAll(" ")
        val matcher = amountPattern.matcher(amountText)
        if (!matcher.find()) return null
        val amount = parseMoneyToCents(matcher.group(1).orEmpty()) ?: return null
        val type = if (incomeWords.any { text.contains(it) }) TransactionType.INCOME else TransactionType.EXPENSE
        val categoryId = findCategory(text, type)
        val date = findDate(text, today)
        val note = text.trim().ifBlank { if (type == TransactionType.INCOME) "收入" else "支出" }
        return ParsedTransaction(amount, type, categoryId, date, note)
    }

    private fun findCategory(text: String, type: TransactionType): String = if (type == TransactionType.INCOME) {
        when {
            listOf("兼职", "接单", "稿费").any(text::contains) -> DefaultData.PART_TIME
            listOf("工资", "薪资").any(text::contains) -> DefaultData.SALARY
            listOf("奖金", "红包").any(text::contains) -> DefaultData.BONUS
            listOf("生活费", "家里给").any(text::contains) -> DefaultData.LIVING_EXPENSE
            else -> DefaultData.OTHER_INCOME
        }
    } else {
        when {
            listOf("饭", "吃", "餐", "奶茶", "咖啡", "外卖").any(text::contains) -> DefaultData.FOOD
            listOf("打车", "地铁", "公交", "出行", "车费").any(text::contains) -> DefaultData.TRANSPORT
            listOf("买", "购物", "衣服", "网购").any(text::contains) -> DefaultData.SHOPPING
            listOf("电影", "游戏", "娱乐", "聚会").any(text::contains) -> DefaultData.ENTERTAINMENT
            listOf("书", "课程", "学习", "资料").any(text::contains) -> DefaultData.STUDY
            listOf("房租", "住宿", "宿舍").any(text::contains) -> DefaultData.HOUSING
            listOf("药", "医院", "看病").any(text::contains) -> DefaultData.MEDICAL
            else -> DefaultData.OTHER_EXPENSE
        }
    }

    private fun findDate(text: String, today: LocalDate): String {
        val monthDay = monthDayPattern.matcher(text)
        if (monthDay.find()) {
            val month = monthDay.group(1).orEmpty().toIntOrNull()
            val day = monthDay.group(2).orEmpty().toIntOrNull()
            if (month != null && day != null) {
                return runCatching { LocalDate.of(today.year, month, day).toString() }.getOrDefault(today.toString())
            }
        }
        return when {
            text.contains("前天") -> today.minusDays(2).toString()
            text.contains("昨天") -> today.minusDays(1).toString()
            else -> today.toString()
        }
    }

    private val incomeWords = listOf("赚", "收入", "工资", "兼职", "奖金", "生活费", "到账", "收到")
}
