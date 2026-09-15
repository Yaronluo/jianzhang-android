package com.jianzhang.app.domain.parser

import com.jianzhang.app.data.model.DefaultData
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import org.json.JSONObject

enum class ReceiptFieldConfidence {
    HIGH,
    LOW,
    MISSING
}

data class ParsedReceipt(
    val rawText: String,
    val amountInCents: Long?,
    val amountKeyword: String?,
    val amountConfidence: ReceiptFieldConfidence,
    val date: String,
    val dateConfidence: ReceiptFieldConfidence,
    val time: String,
    val merchant: String,
    val paymentMethod: String,
    val categoryId: String,
    val categoryConfidence: ReceiptFieldConfidence,
    val warnings: List<String>,
    val recognitionSource: String = "本地识别"
) {
    val amountNeedsReview: Boolean
        get() = amountConfidence != ReceiptFieldConfidence.HIGH
}

/**
 * Pure, offline parser for OCR text from receipts and payment screenshots.
 * It deliberately returns confidence and warnings instead of silently committing guesses.
 */
object ReceiptParser {
    private data class AmountKeyword(val text: String, val score: Int)
    private data class AmountCandidate(
        val cents: Long,
        val keyword: String?,
        val score: Int
    )

    private val amountKeywords = listOf(
        AmountKeyword("实付金额", 120),
        AmountKeyword("付款金额", 118),
        AmountKeyword("支付金额", 116),
        AmountKeyword("订单金额", 112),
        AmountKeyword("实付", 110),
        AmountKeyword("应付金额", 106),
        AmountKeyword("应付", 100),
        AmountKeyword("合计金额", 98),
        AmountKeyword("合计", 94),
        AmountKeyword("总计", 92),
        AmountKeyword("总金额", 88),
        AmountKeyword("金额", 62)
    )
    private val weakAmountLineWords = listOf("优惠", "折扣", "减免", "立减", "原价", "找零", "余额", "积分")
    private val identifierWords = listOf("订单号", "交易号", "流水号", "票据号", "发票号码", "商户号", "付款码")
    private val amountRegex = Regex("""(?<![\dA-Za-z])(?:[¥￥]\s*)?(\d{1,7}(?:,\d{3})*(?:\.\d{1,2})?)(?![\dA-Za-z.])""")
    private val standaloneAmountRegex = Regex("""^[\-−—一]?\s*[¥￥]?\s*\d{1,7}(?:,\d{3})*(?:\.\d{1,2})?\s*元?$""")
    private val fullDateRegex = Regex("""(?<!\d)(20\d{2})\s*[年./-]\s*(\d{1,2})\s*[月./-]\s*(\d{1,2})\s*日?""")
    private val monthDayRegex = Regex("""(?<!\d)(\d{1,2})\s*[月./-]\s*(\d{1,2})\s*日?(?!\d)""")
    private val timeRegex = Regex("""(?<!\d)([01]?\d|2[0-3])\s*[:：时]\s*([0-5]\d)(?:\s*[:：分]\s*[0-5]\d)?""")

    fun parse(
        input: String,
        today: LocalDate = LocalDate.now(),
        nowTime: LocalTime = LocalTime.now()
    ): ParsedReceipt {
        val normalized = normalize(input)
        val lines = normalized.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        val amount = findAmount(lines)
        val parsedDate = findDate(normalized, today)
        val parsedTime = findTime(normalized)
        val merchant = findMerchant(lines)
        val paymentMethod = findPaymentMethod(lines)
        val category = findCategory(normalized, merchant)
        val warnings = buildList {
            when (amount?.score) {
                null -> add("没有可靠识别到金额，请手动填写")
                in Int.MIN_VALUE until 80 -> add("金额可能不准确，请重点核对")
            }
            if (parsedDate == null) add("没有识别到日期，已填今天，请核对")
            if (merchant.isBlank()) add("没有识别到商户，可手动填写")
            if (category.second == ReceiptFieldConfidence.LOW) add("分类为系统推测，请按实际情况选择")
        }
        return ParsedReceipt(
            rawText = normalized,
            amountInCents = amount?.cents,
            amountKeyword = amount?.keyword,
            amountConfidence = when {
                amount == null -> ReceiptFieldConfidence.MISSING
                amount.score >= 80 -> ReceiptFieldConfidence.HIGH
                else -> ReceiptFieldConfidence.LOW
            },
            date = (parsedDate ?: today).toString(),
            dateConfidence = if (parsedDate == null) ReceiptFieldConfidence.LOW else ReceiptFieldConfidence.HIGH,
            time = (parsedTime ?: nowTime).format(DateTimeFormatter.ofPattern("HH:mm")),
            merchant = merchant,
            paymentMethod = paymentMethod,
            categoryId = category.first,
            categoryConfidence = category.second,
            warnings = warnings
        )
    }

    fun parseAiResponse(
        responseText: String,
        sourceName: String,
        today: LocalDate = LocalDate.now(),
        nowTime: LocalTime = LocalTime.now()
    ): ParsedReceipt {
        val json = JSONObject(extractJsonObject(responseText))
        val amountText = json.nullableString("amount")
        val amount = amountText?.let(::moneyTextToCents)
        val parsedDate = json.nullableString("date")
            ?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
        val parsedTime = json.nullableString("time")
            ?.let { value -> timeRegex.find(value)?.let { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) } }
        val merchant = json.nullableString("merchant").orEmpty().trim().take(80)
        val paymentMethod = json.nullableString("payment_method").orEmpty().trim().take(80)
        val rawText = json.nullableString("raw_text").orEmpty().trim()
        val reportedConfidence = when (json.nullableString("confidence")?.lowercase()) {
            "high" -> ReceiptFieldConfidence.HIGH
            "low" -> ReceiptFieldConfidence.LOW
            else -> ReceiptFieldConfidence.MISSING
        }
        val amountConfidence = when {
            amount == null -> ReceiptFieldConfidence.MISSING
            reportedConfidence == ReceiptFieldConfidence.HIGH -> ReceiptFieldConfidence.HIGH
            else -> ReceiptFieldConfidence.LOW
        }
        val category = aiCategoryId(json.nullableString("category"))
        val warnings = buildList {
            if (amount == null) add("AI 没有可靠识别到金额，请手动填写")
            else if (amountConfidence != ReceiptFieldConfidence.HIGH) add("AI 对金额把握较低，请重点核对")
            if (parsedDate == null) add("AI 没有识别到有效日期，已填今天，请核对")
            if (merchant.isBlank()) add("AI 没有识别到商户，可手动填写")
            if (category.second == ReceiptFieldConfidence.LOW) add("分类为系统推测，请按实际情况选择")
        }
        return ParsedReceipt(
            rawText = rawText,
            amountInCents = amount,
            amountKeyword = "AI 结构化识别",
            amountConfidence = amountConfidence,
            date = (parsedDate ?: today).toString(),
            dateConfidence = if (parsedDate == null) ReceiptFieldConfidence.LOW else reportedConfidence,
            time = (parsedTime ?: nowTime).format(DateTimeFormatter.ofPattern("HH:mm")),
            merchant = merchant,
            paymentMethod = paymentMethod,
            categoryId = category.first,
            categoryConfidence = category.second,
            warnings = warnings,
            recognitionSource = sourceName
        )
    }

    private fun findAmount(lines: List<String>): AmountCandidate? {
        val keywordCandidates = mutableListOf<AmountCandidate>()
        lines.forEachIndexed { lineIndex, line ->
            amountKeywords.forEach { keyword ->
                var keywordStart = line.indexOf(keyword.text)
                while (keywordStart >= 0) {
                    for (candidateLineIndex in lineIndex..minOf(lineIndex + 1, lines.lastIndex)) {
                        val candidateLine = lines[candidateLineIndex]
                        if (identifierWords.any(candidateLine::contains)) continue
                        amountMatches(candidateLine).forEach { (cents, start) ->
                            val linePenalty = (candidateLineIndex - lineIndex) * 24
                            val distance = if (candidateLineIndex == lineIndex) abs(start - keywordStart) else 0
                            val positionBonus = if (candidateLineIndex == lineIndex && start >= keywordStart) 8 else 0
                            val weakPenalty = if (
                                weakAmountLineWords.any(candidateLine::contains) &&
                                keyword.text !in setOf("实付", "实付金额", "付款金额", "支付金额", "订单金额", "应付", "应付金额", "合计", "合计金额", "总计")
                            ) 45 else 0
                            keywordCandidates += AmountCandidate(
                                cents = cents,
                                keyword = keyword.text,
                                score = keyword.score + positionBonus - linePenalty - distance.coerceAtMost(60) / 3 - weakPenalty
                            )
                        }
                    }
                    keywordStart = line.indexOf(keyword.text, keywordStart + keyword.text.length)
                }
            }
        }
        val bestKeywordMatch = keywordCandidates
            .groupBy { it.cents }
            .map { (_, matches) -> matches.maxBy { it.score } }
            .maxWithOrNull(compareBy<AmountCandidate> { it.score }.thenBy { it.cents })
        if (bestKeywordMatch != null) return bestKeywordMatch

        val isStructuredPaymentDetail = lines.any { it == "当前状态" } &&
            lines.any { it == "支付时间" || it == "付款时间" } &&
            lines.any { it.contains("支付成功") || it.contains("付款成功") }
        val standaloneCandidates = lines.mapNotNull { line ->
            if (!standaloneAmountRegex.matches(line)) return@mapNotNull null
            val cents = amountMatches(line).singleOrNull()?.first ?: return@mapNotNull null
            AmountCandidate(cents, if (isStructuredPaymentDetail) "支付详情金额" else null, if (isStructuredPaymentDetail) 90 else 52)
        }.distinctBy { it.cents }
        if (standaloneCandidates.isNotEmpty()) return standaloneCandidates.maxBy { it.cents }

        val fallback = lines.flatMap { line ->
            if (identifierWords.any(line::contains) || fullDateRegex.containsMatchIn(line)) {
                emptyList()
            } else {
                amountMatches(line).mapNotNull { (cents, _) ->
                    val looksLikeMoney = line.contains('¥') || line.contains('￥') || line.contains('元') || line.contains("金额")
                    val weak = weakAmountLineWords.any(line::contains)
                    if (looksLikeMoney && !weak) AmountCandidate(cents, null, 45) else null
                }
            }
        }.distinctBy { it.cents }
        return fallback.maxByOrNull { it.cents }
    }

    private fun amountMatches(line: String): List<Pair<Long, Int>> = amountRegex.findAll(line).mapNotNull { match ->
        val raw = match.groupValues[1].replace(",", "")
        val cents = runCatching {
            BigDecimal(raw).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        }.getOrNull()
        cents?.takeIf { it in 1..100_000_000L }?.let { it to match.range.first }
    }.toList()

    private fun moneyTextToCents(value: String): Long? {
        val cleaned = value.replace(",", "").replace("¥", "").replace("￥", "").trim()
        return runCatching {
            BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        }.getOrNull()?.takeIf { it in 1..100_000_000L }
    }

    private fun extractJsonObject(value: String): String {
        val unfenced = value.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val start = unfenced.indexOf('{')
        val end = unfenced.lastIndexOf('}')
        require(start >= 0 && end > start) { "AI 返回的不是有效 JSON" }
        return unfenced.substring(start, end + 1)
    }

    private fun JSONObject.nullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return opt(name)?.toString()?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }

    private fun aiCategoryId(value: String?): Pair<String, ReceiptFieldConfidence> {
        val category = value.orEmpty().trim()
        val id = when {
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
            else -> DefaultData.OTHER_EXPENSE
        }
        return id to if (id == DefaultData.OTHER_EXPENSE) ReceiptFieldConfidence.LOW else ReceiptFieldConfidence.HIGH
    }

    private fun findDate(text: String, today: LocalDate): LocalDate? {
        fullDateRegex.findAll(text).forEach { match ->
            val parsed = safeDate(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt()
            )
            if (parsed != null) return parsed
        }
        monthDayRegex.findAll(text).forEach { match ->
            val month = match.groupValues[1].toInt()
            val day = match.groupValues[2].toInt()
            var parsed = safeDate(today.year, month, day)
            if (parsed != null && parsed.isAfter(today.plusMonths(1))) parsed = safeDate(today.year - 1, month, day)
            if (parsed != null) return parsed
        }
        return null
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    private fun findTime(text: String): LocalTime? {
        text.lineSequence().firstOrNull { fullDateRegex.containsMatchIn(it) }?.let { datedLine ->
            timeRegex.find(datedLine)?.let { match ->
                return runCatching { LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt()) }.getOrNull()
            }
        }
        val match = timeRegex.find(text) ?: return null
        return runCatching { LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt()) }.getOrNull()
    }

    private fun findMerchant(lines: List<String>): String {
        val explicitRegex = Regex("""^(?:商户名称|商户全称|收款方|商家|门店|商户)\s*[:：]\s*(.{2,32})$""")
        lines.forEach { line ->
            explicitRegex.find(line)?.groupValues?.get(1)?.cleanMerchant()?.takeIf { isMerchantCandidate(it) }?.let { return it }
        }
        val excluded = listOf(
            "购物小票", "销售小票", "电子发票", "支付成功", "付款成功", "交易成功", "微信支付", "支付宝",
            "欢迎光临", "谢谢惠顾", "收银员", "订单号", "交易号", "流水号", "付款方式", "支付方式", "商品名称",
            "当前状态", "支付时间", "付款时间", "商品", "商户全称", "收单机构", "账单服务", "扫码退款",
            "对订单有疑惑", "在此商户的交易", "发起群收款", "申请电子凭证"
        ) + amountKeywords.map { it.text }
        return lines.take(28)
            .map { it.cleanMerchant() }
            .filter(::isMerchantCandidate)
            .filterNot { candidate -> excluded.any(candidate::contains) }
            .maxByOrNull { candidate ->
                val businessBonus = if (listOf("店", "超市", "商场", "餐厅", "药房", "公司", "中心").any(candidate::contains)) 40 else 0
                businessBonus + candidate.length * 2 - lines.indexOfFirst { it.contains(candidate) }
            }
            .orEmpty()
    }

    private fun findPaymentMethod(lines: List<String>): String {
        val strongWords = listOf("储蓄卡", "信用卡", "银行卡", "零钱", "零钱通")
        lines.firstOrNull { line -> strongWords.any(line::contains) }?.let { return it.take(40) }
        return lines.firstOrNull { line -> line.contains("支付宝") || line.contains("微信支付") || line == "现金" }
            ?.take(40)
            .orEmpty()
    }

    private fun String.cleanMerchant(): String =
        replace(Regex("""^[*#=_\-\s]+|[*#=_\-\s]+$"""), "").trim().take(32)

    private fun isMerchantCandidate(value: String): Boolean =
        value.length in 2..32 && value.any { it in '\u4e00'..'\u9fff' } &&
            !fullDateRegex.containsMatchIn(value) && !timeRegex.containsMatchIn(value) &&
            !identifierWords.any(value::contains) && amountMatches(value).none()

    private fun findCategory(text: String, merchant: String): Pair<String, ReceiptFieldConfidence> {
        val source = "$merchant $text"
        val categoryRules = listOf(
            DefaultData.MEDICAL to listOf("医院", "诊所", "药房", "药店", "挂号", "医疗"),
            DefaultData.TRANSPORT to listOf("滴滴", "打车", "出租车", "地铁", "公交", "停车", "加油", "车票", "高速"),
            DefaultData.FOOD to listOf("餐厅", "饭店", "饭馆", "面馆", "咖啡", "奶茶", "外卖", "餐饮", "美食", "火锅", "早餐", "午餐", "晚餐"),
            DefaultData.SHOPPING to listOf("超市", "商场", "便利店", "购物", "淘宝", "京东", "拼多多"),
            DefaultData.UTILITIES to listOf("电费", "水费", "燃气", "天然气", "物业费"),
            DefaultData.COMMUNICATION to listOf("话费", "流量", "宽带", "通信", "中国移动", "中国联通", "中国电信"),
            DefaultData.CLOTHING to listOf("服装", "衣服", "鞋", "帽", "优衣库"),
            DefaultData.STUDY to listOf("书店", "图书", "教材", "文具", "课程"),
            DefaultData.ENTERTAINMENT to listOf("电影", "影院", "游戏", "KTV", "娱乐"),
            DefaultData.DAILY to listOf("日用品", "百货", "家居", "清洁用品"),
            DefaultData.TRAVEL to listOf("酒店", "宾馆", "民宿", "旅行", "景区"),
            DefaultData.CAR to listOf("汽车", "维修", "保养", "洗车"),
            DefaultData.SPORTS to listOf("健身", "运动", "球馆"),
            DefaultData.PET to listOf("宠物", "猫粮", "狗粮"),
            DefaultData.INSURANCE to listOf("保险", "保费")
        )
        categoryRules.firstOrNull { (_, words) -> words.any { source.contains(it, ignoreCase = true) } }
            ?.let { return it.first to ReceiptFieldConfidence.HIGH }
        return DefaultData.OTHER_EXPENSE to ReceiptFieldConfidence.LOW
    }

    private fun normalize(input: String): String = buildString(input.length) {
        input.forEach { character ->
            append(
                when (character) {
                    in '０'..'９' -> ('0'.code + character.code - '０'.code).toChar()
                    '，' -> ','
                    '．' -> '.'
                    '戶' -> '户'
                    '萬' -> '万'
                    '銀' -> '银'
                    '儲' -> '储'
                    '機' -> '机'
                    else -> character
                }
            )
        }
    }.replace("\r\n", "\n").replace('\r', '\n')
}
