package com.jianzhang.app.quickcapture

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.jianzhang.app.MainActivity

class QuickCaptureTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        activeInstance = this
        render(readTileText(this))
    }

    override fun onStopListening() {
        if (activeInstance === this) activeInstance = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (QuickCaptureSessionRegistry.currentState() !in terminalStates) {
            Toast.makeText(this, "正在处理上一张截图", Toast.LENGTH_SHORT).show()
            return
        }
        val service = QuickCaptureAccessibilityService.connectedInstance
        if (service == null) {
            updateState(this, "请先开启服务")
            openOneTapSettings()
            return
        }
        launchCaptureAndCollapse()
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun launchCaptureAndCollapse() {
        val intent = Intent(this, QuickCaptureCollapseActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    42,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            startActivityAndCollapse(intent)
        }
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun openOneTapSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_QUICK_CAPTURE_SETTINGS
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    41,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            startActivityAndCollapse(intent)
        }
    }

    private fun render(text: String) {
        qsTile?.apply {
            label = text
            state = if (text == DEFAULT_TEXT) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            updateTile()
        }
    }

    companion object {
        const val DEFAULT_TEXT = "AI截图助手"
        private const val PREFS = "quick_capture_tile"
        private const val KEY_TEXT = "tile_text"
        private val terminalStates = setOf(
            QuickCaptureState.IDLE,
            QuickCaptureState.SUCCESS,
            QuickCaptureState.FAILED,
            QuickCaptureState.CANCELLED
        )
        @Volatile private var activeInstance: QuickCaptureTileService? = null

        fun updateState(context: Context, text: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TEXT, text).apply()
            activeInstance?.render(text)
            requestListeningState(context, ComponentName(context, QuickCaptureTileService::class.java))
        }

        fun reset(context: Context) = updateState(context, DEFAULT_TEXT)

        private fun readTileText(context: Context): String =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TEXT, DEFAULT_TEXT)
                ?: DEFAULT_TEXT
    }
}
