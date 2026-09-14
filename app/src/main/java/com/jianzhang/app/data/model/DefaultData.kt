package com.jianzhang.app.data.model

import com.jianzhang.app.data.local.entity.CategoryEntity
import com.jianzhang.app.data.local.entity.CategoryType

object DefaultData {
    const val FOOD = "expense_food"
    const val TRANSPORT = "expense_transport"
    const val SHOPPING = "expense_shopping"
    const val ENTERTAINMENT = "expense_entertainment"
    const val STUDY = "expense_study"
    const val HOUSING = "expense_housing"
    const val MEDICAL = "expense_medical"
    const val COMMUNICATION = "expense_communication"
    const val CLOTHING = "expense_clothing"
    const val DAILY = "expense_daily"
    const val FAMILY = "expense_family"
    const val SPORTS = "expense_sports"
    const val PET = "expense_pet"
    const val TRAVEL = "expense_travel"
    const val CAR = "expense_car"
    const val UTILITIES = "expense_utilities"
    const val SUBSCRIPTION = "expense_subscription"
    const val SOCIAL = "expense_social"
    const val INSURANCE = "expense_insurance"
    const val OTHER_EXPENSE = "expense_other"
    const val SALARY = "income_salary"
    const val PART_TIME = "income_part_time"
    const val LIVING_EXPENSE = "income_living_expense"
    const val BONUS = "income_bonus"
    const val INVESTMENT_INCOME = "income_investment"
    const val RED_PACKET = "income_red_packet"
    const val REIMBURSEMENT = "income_reimbursement"
    const val REFUND = "income_refund"
    const val RENT = "income_rent"
    const val TRANSFER_INCOME = "income_transfer"
    const val OTHER_INCOME = "income_other"

    val categories = listOf(
        CategoryEntity(FOOD, "餐饮", "food", CategoryType.EXPENSE, 1),
        CategoryEntity(TRANSPORT, "交通", "transport", CategoryType.EXPENSE, 2),
        CategoryEntity(SHOPPING, "购物", "shopping", CategoryType.EXPENSE, 3),
        CategoryEntity(ENTERTAINMENT, "娱乐", "entertainment", CategoryType.EXPENSE, 4),
        CategoryEntity(STUDY, "学习", "study", CategoryType.EXPENSE, 5),
        CategoryEntity(HOUSING, "居住", "housing", CategoryType.EXPENSE, 6),
        CategoryEntity(MEDICAL, "医疗", "medical", CategoryType.EXPENSE, 7),
        CategoryEntity(COMMUNICATION, "通讯", "communication", CategoryType.EXPENSE, 8),
        CategoryEntity(CLOTHING, "服饰", "clothing", CategoryType.EXPENSE, 9),
        CategoryEntity(DAILY, "日用", "daily", CategoryType.EXPENSE, 10),
        CategoryEntity(FAMILY, "家庭", "family", CategoryType.EXPENSE, 11),
        CategoryEntity(SPORTS, "运动", "sports", CategoryType.EXPENSE, 12),
        CategoryEntity(PET, "宠物", "pet", CategoryType.EXPENSE, 13),
        CategoryEntity(TRAVEL, "旅行", "travel", CategoryType.EXPENSE, 14),
        CategoryEntity(CAR, "汽车", "car", CategoryType.EXPENSE, 15),
        CategoryEntity(UTILITIES, "水电燃气", "utilities", CategoryType.EXPENSE, 16),
        CategoryEntity(SUBSCRIPTION, "订阅", "subscription", CategoryType.EXPENSE, 17),
        CategoryEntity(SOCIAL, "人情", "social", CategoryType.EXPENSE, 18),
        CategoryEntity(INSURANCE, "保险", "insurance", CategoryType.EXPENSE, 19),
        CategoryEntity(OTHER_EXPENSE, "其他", "other_expense", CategoryType.EXPENSE, 20),
        CategoryEntity(SALARY, "工资", "salary", CategoryType.INCOME, 101),
        CategoryEntity(PART_TIME, "兼职", "part_time", CategoryType.INCOME, 102),
        CategoryEntity(LIVING_EXPENSE, "生活费", "living_expense", CategoryType.INCOME, 103),
        CategoryEntity(BONUS, "奖金", "bonus", CategoryType.INCOME, 104),
        CategoryEntity(INVESTMENT_INCOME, "投资收益", "investment", CategoryType.INCOME, 105),
        CategoryEntity(RED_PACKET, "红包", "red_packet", CategoryType.INCOME, 106),
        CategoryEntity(REIMBURSEMENT, "报销", "reimbursement", CategoryType.INCOME, 107),
        CategoryEntity(REFUND, "退款", "refund", CategoryType.INCOME, 108),
        CategoryEntity(RENT, "租金", "rent", CategoryType.INCOME, 109),
        CategoryEntity(TRANSFER_INCOME, "转账", "transfer_income", CategoryType.INCOME, 110),
        CategoryEntity(OTHER_INCOME, "其他", "other_income", CategoryType.INCOME, 111)
    )
}
