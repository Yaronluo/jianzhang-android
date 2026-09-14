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
            listOf("工资", "薪资").any(text::contains) -> DefaultData.SALARY
            listOf("兼职", "接单", "稿费").any(text::contains) -> DefaultData.PART_TIME
            listOf("生活费", "家里给").any(text::contains) -> DefaultData.LIVING_EXPENSE
            listOf("奖金", "年终奖").any(text::contains) -> DefaultData.BONUS
            listOf("投资收益", "理财收益", "分红", "投资赚").any(text::contains) -> DefaultData.INVESTMENT_INCOME
            listOf("红包", "收红包", "红包收入").any(text::contains) -> DefaultData.RED_PACKET
            listOf("报销", "报销款").any(text::contains) -> DefaultData.REIMBURSEMENT
            listOf("退款", "退货退款", "退回").any(text::contains) -> DefaultData.REFUND
            listOf("租金", "房租收入").any(text::contains) -> DefaultData.RENT
            listOf("收到转账", "别人转账", "朋友转账", "转账给我", "转账到账", "转钱给我", "对方转钱", "微信转账", "支付宝转账", "转账收入").any(text::contains) -> DefaultData.TRANSFER_INCOME
            else -> DefaultData.OTHER_INCOME
        }
    } else {
        when {
            listOf("饭", "吃", "餐", "奶茶", "咖啡", "外卖").any(text::contains) -> DefaultData.FOOD
            listOf("打车", "地铁", "公交", "出行", "车费").any(text::contains) -> DefaultData.TRANSPORT
            listOf("电影", "游戏", "娱乐", "聚会").any(text::contains) -> DefaultData.ENTERTAINMENT
            listOf("书", "课程", "学习", "资料").any(text::contains) -> DefaultData.STUDY
            listOf("房租", "住宿", "宿舍").any(text::contains) -> DefaultData.HOUSING
            listOf("药", "医院", "看病").any(text::contains) -> DefaultData.MEDICAL
            listOf("手机", "话费", "流量", "宽带", "通讯").any(text::contains) -> DefaultData.COMMUNICATION
            listOf("衣服", "鞋", "服饰", "穿搭").any(text::contains) -> DefaultData.CLOTHING
            listOf("日用品", "日用", "清洁用品", "洗护").any(text::contains) -> DefaultData.DAILY
            listOf("家庭", "家用", "家人").any(text::contains) -> DefaultData.FAMILY
            listOf("运动", "健身", "球场", "器材").any(text::contains) -> DefaultData.SPORTS
            listOf("宠物", "猫粮", "狗粮", "宠物粮").any(text::contains) -> DefaultData.PET
            listOf("旅行", "旅游", "酒店", "机票").any(text::contains) -> DefaultData.TRAVEL
            listOf("汽车", "加油", "停车", "保养").any(text::contains) -> DefaultData.CAR
            listOf("水费", "电费", "燃气", "水电").any(text::contains) -> DefaultData.UTILITIES
            listOf("订阅", "会员", "会员费", "软件").any(text::contains) -> DefaultData.SUBSCRIPTION
            listOf("人情", "随礼", "礼金", "红包").any(text::contains) -> DefaultData.SOCIAL
            listOf("保险", "保费").any(text::contains) -> DefaultData.INSURANCE
            listOf("买", "购物", "网购", "商场", "淘宝").any(text::contains) -> DefaultData.SHOPPING
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

    private val incomeWords = listOf(
        "赚", "收入", "工资", "兼职", "奖金", "生活费", "到账", "收到",
        "投资收益", "理财收益", "分红", "报销", "退款", "退货退款", "租金", "房租收入",
        "收到转账", "别人转账", "朋友转账", "转账给我", "转账到账", "转钱给我", "对方转钱", "微信转账", "支付宝转账", "转账收入"
    )
}
