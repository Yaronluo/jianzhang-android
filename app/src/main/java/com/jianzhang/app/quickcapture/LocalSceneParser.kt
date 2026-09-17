package com.jianzhang.app.quickcapture

import com.jianzhang.app.domain.parser.ReceiptFieldConfidence
import com.jianzhang.app.domain.parser.ReceiptParser

/** Conservative offline scene rules. An action is emitted only when an explicit scene keyword and identifier coexist. */
object LocalSceneParser {
    private val pickupCode = Regex("(?:取餐码|取餐号|餐号|取餐号码)\\s*[:：]?\\s*([A-Z]?\\d{2,5}|[A-Z]\\d{2,5})", RegexOption.IGNORE_CASE)
    private val packageCode = Regex("(?:取件码|提货码|取货码)\\s*[:：]?\\s*([0-9A-Z][0-9A-Z-]{2,18})", RegexOption.IGNORE_CASE)
    private val trainNumber = Regex("(?<![A-Z0-9])([GDCZTKS]\\d{1,4})(?![A-Z0-9])", RegexOption.IGNORE_CASE)
    private val flightNumber = Regex("(?<![A-Z0-9])([A-Z]{2}\\d{3,4})(?![A-Z0-9])")
    private val route = Regex("([\\u4e00-\\u9fa5]{2,8})\\s*(?:→|至|—|-)\\s*([\\u4e00-\\u9fa5]{2,8})")
    private val date = Regex("(20\\d{2}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?)")
    private val time = Regex("(?<!\\d)([0-2]?\\d:[0-5]\\d)(?!\\d)")

    fun parse(text: String, source: String = "OCR识别"): ScreenAnalysis {
        val normalized = text.replace('：', ':')
            .replace("菜乌驿站", "菜鸟驿站")
            .trim()
        val lines = normalized.lines().map(String::trim).filter(String::isNotBlank)
        val receipt = ReceiptParser.parse(normalized)
        val actions = buildList<ScreenAction> {
            if (receipt.amountInCents != null && receipt.amountConfidence != ReceiptFieldConfidence.MISSING) {
                add(
                    ExpenseAction(
                        confidence = if (receipt.amountConfidence == ReceiptFieldConfidence.HIGH) 0.9 else 0.7,
                        amount = "%.2f".format(receipt.amountInCents / 100.0),
                        merchant = receipt.merchant.ifBlank { null },
                        category = null,
                        date = receipt.date.takeIf { receipt.dateConfidence == ReceiptFieldConfidence.HIGH },
                        time = receipt.time,
                        paymentMethod = receipt.paymentMethod.ifBlank { null },
                        rawText = normalized,
                        source = source
                    )
                )
            }
            pickupCode.find(normalized)?.let { match ->
                val merchant = receipt.merchant.ifBlank { lines.firstOrNull { it.contains("店") } }
                val location = lines.firstOrNull {
                    (it.contains("门店") || it.contains("店")) && it != merchant
                }
                add(
                    PickupAction(
                        confidence = 0.9,
                        merchant = merchant,
                        code = match.groupValues[1].uppercase(),
                        queueNumber = valueAfter(normalized, "排队号", "排号"),
                        tableNumber = valueAfter(normalized, "桌号", "桌台"),
                        location = location,
                        time = Regex("(?:取餐时间|预计取餐|请于)\\s*[:：]?\\s*([0-2]?\\d:[0-5]\\d)")
                            .find(normalized)?.groupValues?.get(1)
                    )
                )
            }
            packageCode.find(normalized)?.let { match ->
                val company = listOf("菜鸟驿站", "丰巢", "顺丰", "京东快递", "中通", "圆通", "申通", "韵达", "邮政")
                    .firstOrNull(normalized::contains) ?: lines.firstOrNull { it.contains("快递") || it.contains("驿站") }
                add(
                    PackageAction(
                        confidence = if (company != null) 0.94 else 0.86,
                        company = company,
                        code = match.groupValues[1].uppercase(),
                        location = lines.firstOrNull { it.contains("东门") || it.contains("西门") || it.contains("南门") || it.contains("北门") }
                            ?: lines.firstOrNull { it.contains("驿站") },
                        shelf = Regex("\\d+号架|货架\\s*[A-Z0-9-]+", RegexOption.IGNORE_CASE).find(normalized)?.value,
                        deadline = lines.firstOrNull { it.contains("前取") || it.contains("截止") }
                    )
                )
            }
            val flight = flightNumber.find(normalized)?.groupValues?.get(1)?.uppercase()
            // T2/T3 航站楼也符合单字母车次外形；已有明确航班号时不得误判为列车。
            val train = if (flight == null) trainNumber.find(normalized)?.groupValues?.get(1)?.uppercase() else null
            if (train != null || flight != null) {
                val routeMatch = route.find(normalized)
                val times = time.findAll(normalized).map { it.groupValues[1] }.toList()
                add(
                    TravelAction(
                        confidence = if (routeMatch != null) 0.9 else 0.7,
                        travelType = if (train != null) "train" else "flight",
                        number = train ?: flight,
                        from = routeMatch?.groupValues?.get(1),
                        to = routeMatch?.groupValues?.get(2),
                        date = date.find(normalized)?.value?.replace('年', '-')?.replace('月', '-')?.replace("日", "")?.replace('/', '.')?.replace('.', '-'),
                        departureTime = times.getOrNull(0),
                        arrivalTime = times.getOrNull(1),
                        terminal = Regex("T\\d+", RegexOption.IGNORE_CASE).find(normalized)?.value?.uppercase(),
                        gate = Regex("[A-Z]?\\d{1,3}(?:号)?(?:检票口|登机口)", RegexOption.IGNORE_CASE).find(normalized)?.value,
                        carriage = Regex("\\d{1,2}车").find(normalized)?.value,
                        seat = Regex("\\d{1,2}[A-F](?:座)?", RegexOption.IGNORE_CASE).find(normalized)?.value?.uppercase(),
                        boardingTime = Regex("(?:登机时间|开始登机)\\s*[:：]?\\s*([0-2]?\\d:[0-5]\\d)").find(normalized)?.groupValues?.get(1)
                    )
                )
            }
        }
        return ScreenAnalysis(
            summary = when {
                actions.size > 1 -> "检测到${actions.size}项可操作信息"
                actions.firstOrNull() is ExpenseAction -> "识别到消费信息"
                actions.firstOrNull() is PickupAction -> "识别到取餐信息"
                actions.firstOrNull() is PackageAction -> "识别到取件信息"
                actions.firstOrNull() is TravelAction -> "识别到出行信息"
                else -> null
            },
            actions = actions,
            source = source,
            rawText = normalized
        )
    }

    private fun valueAfter(text: String, vararg labels: String): String? = labels.firstNotNullOfOrNull { label ->
        Regex("$label\\s*[:：]?\\s*([A-Z0-9-]{1,12})", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)
    }
}
