package com.jianzhang.app.quickcapture

import android.content.Context

data class ActionExecutionSummary(
    val executed: Int,
    val duplicateOrSkipped: Int,
    val notificationPermissionMissing: Boolean,
    val expenseNeedsReview: Boolean
)

class ActionRouter(private val context: Context) {
    private val store = SmartCardStore(context)
    private val notifications = SmartNotificationManager(context)

    suspend fun executeConfirmed(draft: QuickCaptureDraft): ActionExecutionSummary {
        var executed = 0
        var skipped = 0
        var notificationMissing = false
        var expenseNeedsReview = false
        var expenseHandled = false
        draft.analysis.actions.forEach { action ->
            if (action.confidence < 0.65) {
                skipped++
                return@forEach
            }
            when (action) {
                is ExpenseAction -> {
                    if (expenseHandled) {
                        skipped++
                        return@forEach
                    }
                    expenseHandled = true
                    if (QuickCaptureEligibility.canSaveAfterUserConfirmation(draft)) {
                        QuickReceiptSaveUseCase(context).save(draft)
                        executed++
                    } else {
                        expenseNeedsReview = true
                    }
                }
                else -> {
                    val candidate = action.toSmartCard()
                    if (candidate == null) {
                        skipped++
                    } else {
                        val before = store.activeCards().any { it.fingerprint == candidate.fingerprint }
                        val stored = store.upsert(candidate)
                        if (!before) executed++ else skipped++
                        if (!notifications.post(stored)) notificationMissing = true
                    }
                }
            }
        }
        return ActionExecutionSummary(executed, skipped, notificationMissing, expenseNeedsReview)
    }

    private fun ScreenAction.toSmartCard(): SmartCard? = when (this) {
        is PickupAction -> {
            val value = code?.takeIf(String::isNotBlank) ?: return null
            val title = listOfNotNull(merchant, location).filter(String::isNotBlank).distinct().joinToString(" · ").ifBlank { "取餐提醒" }
            val content = buildList {
                add("取餐码 $value")
                queueNumber?.let { add("排队号 $it") }
                tableNumber?.let { add("桌号 $it") }
                time?.let { add(it) }
            }.joinToString("\n")
            SmartCardStore.newCard(SmartCardType.PICKUP, title, content, "pickup|${merchant.orEmpty()}|$value")
        }
        is PackageAction -> {
            val value = code?.takeIf(String::isNotBlank) ?: return null
            val title = company?.ifBlank { null } ?: "快递取件"
            val content = buildList {
                add("取件码 $value")
                listOfNotNull(location, shelf).filter(String::isNotBlank).joinToString(" · ").takeIf(String::isNotBlank)?.let(::add)
                deadline?.let(::add)
            }.joinToString("\n")
            SmartCardStore.newCard(SmartCardType.PACKAGE, title, content, "package|${company.orEmpty()}|$value")
        }
        is TravelAction -> {
            val value = number?.takeIf(String::isNotBlank) ?: return null
            val title = value
            val content = buildList {
                if (!from.isNullOrBlank() || !to.isNullOrBlank()) add("${from.orEmpty()} → ${to.orEmpty()}")
                val departureLabel = when (travelType) {
                    "train" -> departureTime?.let { "${it}发车" }
                    "flight" -> departureTime?.let { "${it}起飞" }
                    else -> departureTime?.let { "${it}出发" }
                }
                listOfNotNull(departureLabel, boardingTime?.let { "${it}登机" }, gate).joinToString(" · ").takeIf(String::isNotBlank)?.let(::add)
                listOfNotNull(terminal, carriage, seat).joinToString(" · ").takeIf(String::isNotBlank)?.let(::add)
            }.joinToString("\n")
            SmartCardStore.newCard(SmartCardType.TRAVEL, title, content, "travel|$value|${date.orEmpty()}|${departureTime.orEmpty()}")
        }
        is ExpenseAction -> null
    }
}
