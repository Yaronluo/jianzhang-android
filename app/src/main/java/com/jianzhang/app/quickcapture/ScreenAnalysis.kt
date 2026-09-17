package com.jianzhang.app.quickcapture

import com.jianzhang.app.data.model.DefaultData
import com.jianzhang.app.domain.parser.ParsedReceipt
import com.jianzhang.app.domain.parser.ReceiptFieldConfidence
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime

data class ScreenAnalysis(
    val schemaVersion: Int = 1,
    val summary: String? = null,
    val actions: List<ScreenAction> = emptyList(),
    val source: String,
    val rawText: String = ""
)

sealed interface ScreenAction {
    val confidence: Double
}

data class ExpenseAction(
    override val confidence: Double,
    val amount: String?,
    val merchant: String?,
    val category: String?,
    val date: String?,
    val time: String?,
    val paymentMethod: String?,
    val rawText: String = "",
    val source: String = "屏幕识别"
) : ScreenAction

data class PickupAction(
    override val confidence: Double,
    val merchant: String?,
    val code: String?,
    val queueNumber: String?,
    val tableNumber: String?,
    val location: String?,
    val time: String?
) : ScreenAction

data class PackageAction(
    override val confidence: Double,
    val company: String?,
    val code: String?,
    val location: String?,
    val shelf: String?,
    val deadline: String?
) : ScreenAction

data class TravelAction(
    override val confidence: Double,
    val travelType: String?,
    val number: String?,
    val from: String?,
    val to: String?,
    val date: String?,
    val departureTime: String?,
    val arrivalTime: String?,
    val terminal: String?,
    val gate: String?,
    val carriage: String?,
    val seat: String?,
    val boardingTime: String?
) : ScreenAction

object ScreenAnalysisParser {
    fun parseAiResponse(responseText: String, source: String): ScreenAnalysis {
        val json = JSONObject(extractJsonObject(responseText))
        val actionsJson = json.optJSONArray("actions") ?: JSONArray()
        val actions = buildList {
            repeat(actionsJson.length()) { index ->
                val item = actionsJson.optJSONObject(index) ?: return@repeat
                val data = item.optJSONObject("data") ?: JSONObject()
                val confidence = item.optDouble("confidence", 0.0).coerceIn(0.0, 1.0)
                when (item.optString("type")) {
                    "expense" -> add(
                        ExpenseAction(
                            confidence, data.stringOrNull("amount"), data.stringOrNull("merchant"),
                            data.stringOrNull("category"), data.stringOrNull("date"),
                            data.stringOrNull("time"), data.stringOrNull("paymentMethod")
                                ?: data.stringOrNull("payment_method"),
                            rawText = json.stringOrNull("rawText").orEmpty(), source = source
                        )
                    )
                    "pickup" -> add(
                        PickupAction(
                            confidence, data.stringOrNull("merchant"), data.stringOrNull("code"),
                            data.stringOrNull("queueNumber"), data.stringOrNull("tableNumber"),
                            data.stringOrNull("location"), data.stringOrNull("time")
                        )
                    )
                    "package" -> add(
                        PackageAction(
                            confidence, data.stringOrNull("company"), data.stringOrNull("code"),
                            data.stringOrNull("location"), data.stringOrNull("shelf"),
                            data.stringOrNull("deadline")
                        )
                    )
                    "travel" -> add(
                        TravelAction(
                            confidence, data.stringOrNull("travelType"), data.stringOrNull("number"),
                            data.stringOrNull("from"), data.stringOrNull("to"), data.stringOrNull("date"),
                            data.stringOrNull("departureTime"), data.stringOrNull("arrivalTime"),
                            data.stringOrNull("terminal"), data.stringOrNull("gate"),
                            data.stringOrNull("carriage"), data.stringOrNull("seat"),
                            data.stringOrNull("boardingTime")
                        )
                    )
                }
            }
        }
        return ScreenAnalysis(
            schemaVersion = json.optInt("schemaVersion", 1),
            summary = json.stringOrNull("summary")?.take(120),
            actions = actions,
            source = source,
            rawText = json.stringOrNull("rawText").orEmpty()
        )
    }

    private fun extractJsonObject(value: String): String {
        val clean = value.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = clean.indexOf('{')
        val end = clean.lastIndexOf('}')
        require(start >= 0 && end > start) { "AI 返回的不是有效 JSON" }
        return clean.substring(start, end + 1)
    }

    private fun JSONObject.stringOrNull(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return opt(name)?.toString()?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", true) }
    }
}

fun ExpenseAction.toParsedReceipt(
    today: LocalDate = LocalDate.now(),
    now: LocalTime = LocalTime.now()
): ParsedReceipt {
    val cents = amount?.replace(",", "")?.replace("¥", "")?.replace("￥", "")?.trim()?.let {
        runCatching { BigDecimal(it).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact() }.getOrNull()
    }?.takeIf { it > 0 }
    val dateValue = date?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess } ?: today.toString()
    val timeValue = time?.take(5)?.takeIf { runCatching { LocalTime.parse(it) }.isSuccess }
        ?: "%02d:%02d".format(now.hour, now.minute)
    val high = confidence >= 0.85
    val categoryId = screenCategoryId(category)
    return ParsedReceipt(
        rawText = rawText,
        amountInCents = cents,
        amountKeyword = "屏幕动作识别",
        amountConfidence = when { cents == null -> ReceiptFieldConfidence.MISSING; high -> ReceiptFieldConfidence.HIGH; else -> ReceiptFieldConfidence.LOW },
        date = dateValue,
        dateConfidence = if (date != null && high) ReceiptFieldConfidence.HIGH else ReceiptFieldConfidence.LOW,
        time = timeValue,
        merchant = merchant.orEmpty(),
        paymentMethod = paymentMethod.orEmpty(),
        categoryId = categoryId,
        categoryConfidence = if (categoryId != DefaultData.OTHER_EXPENSE && high) ReceiptFieldConfidence.HIGH else ReceiptFieldConfidence.LOW,
        warnings = buildList {
            if (cents == null) add("没有可靠识别到金额，请手动填写")
            if (!high) add("该动作置信度较低，请重点核对")
            if (date == null) add("没有识别到日期，已填今天，请核对")
        },
        recognitionSource = source
    )
}

private fun screenCategoryId(value: String?): String = when {
    value.orEmpty().contains("餐") -> DefaultData.FOOD
    value.orEmpty().contains("交通") -> DefaultData.TRANSPORT
    value.orEmpty().contains("购物") -> DefaultData.SHOPPING
    value.orEmpty().contains("医疗") -> DefaultData.MEDICAL
    value.orEmpty().contains("居住") -> DefaultData.HOUSING
    value.orEmpty().contains("水电") || value.orEmpty().contains("燃气") -> DefaultData.UTILITIES
    value.orEmpty().contains("通讯") -> DefaultData.COMMUNICATION
    value.orEmpty().contains("服") -> DefaultData.CLOTHING
    value.orEmpty().contains("学习") -> DefaultData.STUDY
    value.orEmpty().contains("娱乐") -> DefaultData.ENTERTAINMENT
    value.orEmpty().contains("日用") -> DefaultData.DAILY
    value.orEmpty().contains("旅行") -> DefaultData.TRAVEL
    else -> DefaultData.OTHER_EXPENSE
}
