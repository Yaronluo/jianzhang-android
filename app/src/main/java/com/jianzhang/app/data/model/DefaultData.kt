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
    const val OTHER_EXPENSE = "expense_other"
    const val SALARY = "income_salary"
    const val PART_TIME = "income_part_time"
    const val LIVING_EXPENSE = "income_living_expense"
    const val BONUS = "income_bonus"
    const val OTHER_INCOME = "income_other"

    val categories = listOf(
        CategoryEntity(FOOD, "餐饮", "🍜", CategoryType.EXPENSE, 1),
        CategoryEntity(TRANSPORT, "交通", "🚕", CategoryType.EXPENSE, 2),
        CategoryEntity(SHOPPING, "购物", "🛍️", CategoryType.EXPENSE, 3),
        CategoryEntity(ENTERTAINMENT, "娱乐", "🎮", CategoryType.EXPENSE, 4),
        CategoryEntity(STUDY, "学习", "📚", CategoryType.EXPENSE, 5),
        CategoryEntity(HOUSING, "居住", "🏠", CategoryType.EXPENSE, 6),
        CategoryEntity(MEDICAL, "医疗", "💊", CategoryType.EXPENSE, 7),
        CategoryEntity(OTHER_EXPENSE, "其他", "📦", CategoryType.EXPENSE, 8),
        CategoryEntity(SALARY, "工资", "💼", CategoryType.INCOME, 101),
        CategoryEntity(PART_TIME, "兼职", "🧑‍💻", CategoryType.INCOME, 102),
        CategoryEntity(LIVING_EXPENSE, "生活费", "💰", CategoryType.INCOME, 103),
        CategoryEntity(BONUS, "奖金", "🎁", CategoryType.INCOME, 104),
        CategoryEntity(OTHER_INCOME, "其他", "✨", CategoryType.INCOME, 105)
    )
}
