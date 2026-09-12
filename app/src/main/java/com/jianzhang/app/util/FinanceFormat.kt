package com.jianzhang.app.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

fun formatMoney(cents: Long, signed: Boolean = false): String {
    val absolute = cents.toBigDecimal().abs().movePointLeft(2).setScale(2, RoundingMode.DOWN)
    val number = NumberFormat.getNumberInstance(Locale.CHINA).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(absolute)
    return when {
        signed && cents > 0 -> "+¥$number"
        signed && cents < 0 -> "-¥$number"
        cents < 0 -> "-¥$number"
        else -> "¥$number"
    }
}

fun formatInputMoney(cents: Long): String = cents.toBigDecimal()
    .movePointLeft(2)
    .setScale(2, RoundingMode.DOWN)
    .toPlainString()

fun parseMoneyToCents(value: String): Long? = runCatching {
    val normalized = value.trim()
        .replace("¥", "")
        .replace(",", "")
        .replace("元", "")
        .replace("块钱", "")
        .replace("块", "")
        .trim()
    if (normalized.isBlank()) return null
    normalized.toBigDecimal().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
}.getOrNull()?.takeIf { it > 0 }

fun parseMoneyToCentsOrZero(value: String): Long? = runCatching {
    val normalized = value.trim()
        .replace("¥", "")
        .replace(",", "")
        .replace("元", "")
        .replace("块钱", "")
        .replace("块", "")
        .trim()
    if (normalized.isBlank()) return null
    normalized.toBigDecimal().takeIf { it >= BigDecimal.ZERO }
        ?.movePointRight(2)
        ?.setScale(0, RoundingMode.HALF_UP)
        ?.longValueExact()
}.getOrNull()

fun formatDate(date: String, today: LocalDate = LocalDate.now()): String {
    val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return date
    return when (parsed) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> parsed.format(dateFormatter)
    }
}

fun formatMonth(month: String): String {
    val parts = month.split('-')
    return if (parts.size == 2) "${parts[0]} 年 ${parts[1].toIntOrNull() ?: parts[1]} 月" else month
}

fun monthOf(date: String): String = date.take(7)

fun percentText(part: Long, total: Long): String {
    if (total <= 0L) return "0%"
    val value = part.toDouble() / total.toDouble() * 100.0
    return "${"%.1f".format(Locale.CHINA, value)}%"
}
