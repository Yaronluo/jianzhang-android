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
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quick_capture_tile)
            .setContentTitle(card.title)
            .setContentText(card.content.replace('\n', ' '))
            .setStyle(Notification.BigTextStyle().bigText(card.content))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .addAction(Notification.Action.Builder(null, actionLabel, completeIntent).build())
            .build()
        manager.notify(notificationId(card.id), notification)
        return true
    }

    fun cancel(cardId: String) = manager.cancel(notificationId(cardId))

    fun restoreAll() = SmartCardStore(context).activeCards().forEach(::post)

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
