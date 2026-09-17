package com.jianzhang.app.quickcapture

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.jianzhang.app.R
import com.jianzhang.app.MainActivity

class SmartNotificationManager(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun post(card: SmartCard): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        ensureChannel()
        val completeIntent = PendingIntent.getBroadcast(
            context,
            notificationId(card.id),
            Intent(context, TemporaryInfoActionReceiver::class.java)
                .setAction(TemporaryInfoActionReceiver.ACTION_COMPLETE)
                .putExtra(TemporaryInfoActionReceiver.EXTRA_CARD_ID, card.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val actionLabel = when (card.type) {
            SmartCardType.PICKUP -> "已取餐"
            SmartCardType.PACKAGE -> "已取件"
            SmartCardType.TRAVEL -> "结束行程"
        }
        val openIntent = PendingIntent.getActivity(
            context,
            notificationId(card.id),
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_SMART_ITEMS)
                .putExtra(MainActivity.EXTRA_SMART_CARD_ID, card.id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quick_capture_tile)
            .setContentTitle(card.title)
            .setContentText(card.content.replace('\n', ' '))
            .setStyle(Notification.BigTextStyle().bigText(card.content))
            .setContentIntent(openIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setGroup(GROUP_KEY)
            .addAction(Notification.Action.Builder(null, actionLabel, completeIntent).build())
            .build()
        manager.notify(notificationId(card.id), notification)
        updateGroupSummary()
        return true
    }

    fun cancel(cardId: String) {
        manager.cancel(notificationId(cardId))
        updateGroupSummary()
    }

    fun restoreAll() {
        SmartCardStore(context).activeCards().forEach(::post)
        updateGroupSummary()
    }

    private fun updateGroupSummary() {
        val cards = SmartCardStore(context).activeCards()
        if (cards.isEmpty()) {
            manager.cancel(SUMMARY_NOTIFICATION_ID)
            return
        }
        val openIntent = PendingIntent.getActivity(
            context,
            SUMMARY_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_SMART_ITEMS)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val summary = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quick_capture_tile)
            .setContentTitle("当前事项")
            .setContentText("${cards.size} 条待处理事项")
            .setContentIntent(openIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .build()
        manager.notify(SUMMARY_NOTIFICATION_ID, summary)
    }

    private fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "AI截图助手当前事项", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "取餐码、取件码和出行卡片"
                setShowBadge(true)
            }
        )
    }

    private fun notificationId(id: String): Int = id.hashCode() and Int.MAX_VALUE

    private companion object {
        const val CHANNEL_ID = "smart_info_actions"
        const val GROUP_KEY = "com.jianzhang.app.CURRENT_SMART_ITEMS"
        const val SUMMARY_NOTIFICATION_ID = 0x534D4152
    }
}

class TemporaryInfoActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COMPLETE) return
        val id = intent.getStringExtra(EXTRA_CARD_ID) ?: return
        SmartCardStore(context).complete(id)
        SmartNotificationManager(context).cancel(id)
    }

    companion object {
        const val ACTION_COMPLETE = "com.jianzhang.app.COMPLETE_SMART_CARD"
        const val EXTRA_CARD_ID = "card_id"
    }
}
