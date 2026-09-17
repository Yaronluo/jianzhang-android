package com.jianzhang.app.domain.parser

import com.jianzhang.app.data.model.DefaultData

data class ExpenseCategoryMatch(
    val id: String,
    val confidence: ReceiptFieldConfidence
)

/**
 * Keeps obvious merchant/category knowledge deterministic instead of trusting
 * a single AI label. Explicit non-"other" AI categories still win; merchant
 * and receipt text are used when the model omitted the category or returned
 * the generic fallback.
 */
object ExpenseCategoryResolver {
    fun resolve(
        category: String?,
        merchant: String? = null,
        recognitionText: String = ""
    ): ExpenseCategoryMatch {
        val explicitId = explicitCategoryId(category)
        if (explicitId != null && explicitId != DefaultData.OTHER_EXPENSE) {
            return ExpenseCategoryMatch(explicitId, ReceiptFieldConfidence.HIGH)
        }

        val source = listOfNotNull(merchant, recognitionText)
            .filter(String::isNotBlank)
            .joinToString(" ")
        categoryRules.firstOrNull { (_, words) ->
            words.any { source.contains(it, ignoreCase = true) }
        }?.let { (id, _) ->
            return ExpenseCategoryMatch(id, ReceiptFieldConfidence.HIGH)
        }

        return ExpenseCategoryMatch(
            explicitId ?: DefaultData.OTHER_EXPENSE,
            ReceiptFieldConfidence.LOW
        )
    }

    private fun explicitCategoryId(value: String?): String? {
        val category = value.orEmpty().trim()
        if (category.isBlank()) return null
        DefaultData.categories.firstOrNull {
            it.id == category && it.id.startsWith("expense_")
        }?.let { return it.id }
        return when {
            category.contains("餐") || category.contains("食品") -> DefaultData.FOOD
            category.contains("交通") -> DefaultData.TRANSPORT
            category.contains("购物") -> DefaultData.SHOPPING
            category.contains("医疗") -> DefaultData.MEDICAL
            category.contains("居住") || category.contains("住房") -> DefaultData.HOUSING
            category.contains("水电") || category.contains("燃气") -> DefaultData.UTILITIES
            category.contains("通讯") || category.contains("通信") -> DefaultData.COMMUNICATION
            category.contains("服饰") || category.contains("服装") -> DefaultData.CLOTHING
            category.contains("学习") || category.contains("教育") -> DefaultData.STUDY
            category.contains("娱乐") -> DefaultData.ENTERTAINMENT
            category.contains("日用") -> DefaultData.DAILY
            category.contains("家庭") -> DefaultData.FAMILY
            category.contains("旅行") || category.contains("旅游") -> DefaultData.TRAVEL
            category.contains("汽车") -> DefaultData.CAR
            category.contains("运动") || category.contains("健身") -> DefaultData.SPORTS
            category.contains("宠物") -> DefaultData.PET
            category.contains("订阅") -> DefaultData.SUBSCRIPTION
            category.contains("人情") || category.contains("社交") -> DefaultData.SOCIAL
            category.contains("保险") -> DefaultData.INSURANCE
            category.contains("其他") -> DefaultData.OTHER_EXPENSE
            else -> null
        }
    }

    private val categoryRules = listOf(
        DefaultData.MEDICAL to listOf("医院", "诊所", "药房", "药店", "挂号", "医疗"),
        DefaultData.TRANSPORT to listOf("滴滴", "打车", "出租车", "地铁", "公交", "停车", "加油", "车票", "火车票", "机票", "高速"),
        DefaultData.FOOD to listOf(
            "麦当劳", "肯德基", "星巴克", "必胜客", "汉堡王", "瑞幸", "喜茶", "奈雪",
            "蜜雪冰城", "茶颜悦色", "海底捞", "德克士", "华莱士", "餐厅", "饭店",
            "饭馆", "面馆", "咖啡", "奶茶", "外卖", "餐饮", "美食", "火锅", "烧烤",
            "小吃", "早餐", "午餐", "晚餐", "取餐码", "到店自取订单", "堂食", "外带"
        ),
        DefaultData.SHOPPING to listOf("超市", "商场", "便利店", "购物", "淘宝", "京东", "拼多多"),
        DefaultData.UTILITIES to listOf("电费", "水费", "燃气", "天然气", "物业费"),
        DefaultData.COMMUNICATION to listOf("话费", "流量", "宽带", "通信", "中国移动", "中国联通", "中国电信"),
        DefaultData.CLOTHING to listOf("服装", "衣服", "鞋", "帽", "优衣库"),
        DefaultData.STUDY to listOf("书店", "图书", "教材", "文具", "课程", "学费"),
        DefaultData.ENTERTAINMENT to listOf("电影", "影院", "游戏", "KTV", "娱乐"),
        DefaultData.DAILY to listOf("日用品", "百货", "家居", "清洁用品"),
        DefaultData.HOUSING to listOf("房租", "租房", "公寓", "住房"),
        DefaultData.FAMILY to listOf("家庭", "育儿", "母婴"),
        DefaultData.TRAVEL to listOf("酒店", "宾馆", "民宿", "旅行", "景区"),
        DefaultData.CAR to listOf("汽车", "维修", "保养", "洗车"),
        DefaultData.SPORTS to listOf("健身", "运动", "球馆"),
        DefaultData.PET to listOf("宠物", "猫粮", "狗粮"),
        DefaultData.SUBSCRIPTION to listOf("会员续费", "自动续费", "订阅"),
        DefaultData.SOCIAL to listOf("红包", "礼金", "份子钱", "人情"),
        DefaultData.INSURANCE to listOf("保险", "保费")
    )
}
