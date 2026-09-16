package com.jianzhang.app.quickcapture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.jianzhang.app.MainActivity
import com.jianzhang.app.data.local.AppDatabase
import com.jianzhang.app.data.model.DefaultData
import com.jianzhang.app.domain.account.AccountSelectionPolicy
import com.jianzhang.app.domain.parser.ReceiptFieldConfidence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class QuickCaptureAccessibilityService : AccessibilityService() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private val windowEvents = Channel<Unit>(Channel.CONFLATED)
    private var captureJob: Job? = null
    private var overlay: View? = null
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }

    override fun onServiceConnected() {
        connectedInstance = this
        QuickCaptureTileService.reset(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) windowEvents.trySend(Unit)
    }

    override fun onInterrupt() = cancelActive()

    override fun onDestroy() {
        if (connectedInstance === this) connectedInstance = null
        cancelActive()
        serviceJob.cancel()
        super.onDestroy()
    }

    fun requestQuickCapture(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            openReceiptFallback()
            return true
        }
        val sessionId = QuickCaptureSessionRegistry.tryStart() ?: return false
        captureJob = scope.launch { runCapture(sessionId) }
        Handler(Looper.getMainLooper()).postDelayed({
            if (QuickCaptureSessionRegistry.isActive(sessionId)) cancelSession(sessionId)
        }, SESSION_TIMEOUT_MILLIS)
        return true
    }

    fun showTestCard() {
        showOverlay(
            title = "即时识别卡片",
            message = "测试成功：无障碍浮层可以正常显示",
            success = true,
            buttons = listOf("关闭" to ::removeOverlay)
        )
        Handler(Looper.getMainLooper()).postDelayed(::removeOverlay, 5_000)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun runCapture(sessionId: String) {
        try {
            if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                fail(sessionId, "请先解锁手机")
                return
            }
            QuickCaptureTileService.updateState(this, "正在识别")
            QuickCaptureSessionRegistry.transition(sessionId, QuickCaptureState.CLOSING_SHADE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            if (!awaitShadeClosed()) {
                fail(sessionId, "控制中心未成功收起，请重试")
                return
            }
            QuickCaptureSessionRegistry.transition(sessionId, QuickCaptureState.CAPTURING)
            val bitmap = takeSoftwareScreenshot()
            if (!QuickCaptureSessionRegistry.isActive(sessionId)) {
                bitmap.recycle()
                return
            }
            showLoading("正在准备识别……", sessionId)
            val parsed = try {
                QuickCaptureRecognizer(this).recognize(bitmap) { state, text ->
                    QuickCaptureSessionRegistry.transition(sessionId, state)
                    scope.launch { showLoading(text, sessionId) }
                }
            } finally {
                bitmap.recycle()
            }
            val accounts = AppDatabase.getInstance(this).financeDao().observeAccounts().first()
            val resolvedAccountId = AccountSelectionPolicy.resolveReceiptAccountId(
                accounts = accounts,
                paymentMethod = parsed.paymentMethod,
                defaultAccountId = QuickCapturePreferences(this).defaultAccountId,
                recognitionText = parsed.rawText
            )
            val draft = QuickCaptureDraft(
                sessionId = sessionId,
                receipt = parsed,
                resolvedAccountId = resolvedAccountId,
                resolvedAccountName = accounts.firstOrNull { it.id == resolvedAccountId }?.name
            )
            if (!QuickCaptureSessionRegistry.publishDraft(draft)) return
            showResult(draft)
        } catch (cancelled: CancellationException) {
            QuickCaptureSessionRegistry.finish(sessionId, QuickCaptureState.CANCELLED)
        } catch (error: Throwable) {
            fail(sessionId, error.message?.take(140)?.ifBlank { null } ?: "识别失败，请重试")
        }
    }

    private suspend fun awaitShadeClosed(): Boolean {
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline) {
            val activePackage = rootInActiveWindow?.packageName?.toString()
            if (QuickCaptureWindowGuard.isShadeClosed(activePackage)) return true
            withTimeoutOrNull(100) { windowEvents.receive() }
        }
        return false
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun takeSoftwareScreenshot(): Bitmap = suspendCancellableCoroutine { continuation ->
        takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            Executor { command -> Handler(Looper.getMainLooper()).post(command) },
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    try {
                        val hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            ?: error("无法读取系统截图")
                        val software = hardware.copy(Bitmap.Config.ARGB_8888, false)
                        if (continuation.isActive) continuation.resume(software)
                        else software.recycle()
                    } catch (error: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    } finally {
                        buffer.close()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    val message = if (errorCode == ERROR_TAKE_SCREENSHOT_SECURE_WINDOW) {
                        "当前页面禁止截屏"
                    } else "系统截图失败（错误码 $errorCode）"
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message))
                }
            }
        )
    }

    private fun showResult(draft: QuickCaptureDraft) {
        val receipt = draft.receipt
        val amount = receipt.amountInCents?.let { String.format(Locale.CHINA, "¥%.2f", it / 100.0) } ?: "金额待核对"
        val confidenceHint = if (
            receipt.amountConfidence != ReceiptFieldConfidence.HIGH ||
            receipt.dateConfidence != ReceiptFieldConfidence.HIGH ||
            receipt.categoryConfidence != ReceiptFieldConfidence.HIGH
        ) "部分字段置信度较低，请核对后记账" else "识别结果置信度较高"
        val direct = QuickCaptureEligibility.canDirectSave(draft)
        val category = DefaultData.categories.firstOrNull { it.id == receipt.categoryId }?.name ?: "其他"
        val account = draft.resolvedAccountName ?: "待选择"
        showOverlay(
            title = receipt.merchant.ifBlank { "识别到一笔消费" },
            message = "$amount\n$category  ·  ${receipt.date} ${receipt.time}\n记账账户：$account\n${receipt.recognitionSource}\n$confidenceHint",
            buttons = listOf(
                "修改" to { openEditor(draft.sessionId) },
                (if (direct) "确认记账" else "核对后记账") to {
                    if (direct) saveFromOverlay(draft) else openEditor(draft.sessionId)
                },
                "关闭" to { cancelSession(draft.sessionId) }
            )
        )
    }

    private fun saveFromOverlay(draft: QuickCaptureDraft) {
        if (!QuickCaptureSessionRegistry.tryBeginSaving(draft.sessionId)) return
        showLoading("正在保存……", draft.sessionId)
        scope.launch {
            runCatching { QuickReceiptSaveUseCase(this@QuickCaptureAccessibilityService).save(draft) }
                .onSuccess { result ->
                    QuickCaptureSessionRegistry.finish(draft.sessionId, QuickCaptureState.SUCCESS)
                    val amount = draft.receipt.amountInCents ?: 0L
                    QuickCaptureTileService.updateState(
                        this@QuickCaptureAccessibilityService,
                        String.format(Locale.CHINA, "¥%.2f", amount / 100.0)
                    )
                    showOverlay("✓ 记账成功", "已记入：${result.accountName}\n账单已同步到账本、账户余额、预算与统计。", true)
                    delay(1_500)
                    removeOverlay()
                    QuickCaptureSessionRegistry.reset(draft.sessionId)
                    QuickCaptureTileService.reset(this@QuickCaptureAccessibilityService)
                }
                .onFailure { fail(draft.sessionId, it.message ?: "保存失败") }
        }
    }

    private fun openEditor(sessionId: String) {
        removeOverlay()
        startActivity(Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_EDIT_QUICK_CAPTURE
            putExtra(MainActivity.EXTRA_QUICK_SESSION_ID, sessionId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
    }

    private fun openReceiptFallback() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_RECEIPT
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
    }

    private fun showLoading(message: String, sessionId: String) {
        showOverlay(
            title = "简账·慧录",
            message = message,
            progress = true,
            buttons = listOf("取消" to { cancelSession(sessionId) })
        )
    }

    private fun fail(sessionId: String, message: String) {
        QuickCaptureSessionRegistry.finish(sessionId, QuickCaptureState.FAILED)
        QuickCaptureTileService.updateState(this, "识别失败")
        showOverlay("识别失败", message, buttons = listOf("关闭" to {
            removeOverlay()
            QuickCaptureSessionRegistry.reset(sessionId)
            QuickCaptureTileService.reset(this)
        }))
        Handler(Looper.getMainLooper()).postDelayed({
            if (QuickCaptureSessionRegistry.currentState() == QuickCaptureState.FAILED) {
                removeOverlay()
                QuickCaptureSessionRegistry.reset(sessionId)
                QuickCaptureTileService.reset(this)
            }
        }, 6_000)
    }

    private fun cancelSession(sessionId: String) {
        captureJob?.cancel()
        QuickCaptureSessionRegistry.finish(sessionId, QuickCaptureState.CANCELLED)
        removeOverlay()
        QuickCaptureSessionRegistry.reset(sessionId)
        QuickCaptureTileService.reset(this)
    }

    private fun cancelActive() {
        captureJob?.cancel()
        removeOverlay()
    }

    private fun showOverlay(
        title: String,
        message: String,
        success: Boolean = false,
        progress: Boolean = false,
        buttons: List<Pair<String, () -> Unit>> = emptyList()
    ) {
        removeOverlay()
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(17), dp(20), dp(15))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(if (dark) Color.rgb(42, 39, 34) else Color.argb(248, 255, 249, 238))
                setStroke(dp(1), if (success) Color.rgb(55, 166, 91) else Color.rgb(236, 139, 42))
            }
            elevation = dp(14).toFloat()
        }
        card.addView(TextView(this).apply {
            text = title
            textSize = 20f
            setTextColor(if (success) Color.rgb(45, 154, 79) else if (dark) Color.WHITE else Color.rgb(55, 43, 31))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        if (progress) card.addView(ProgressBar(this).apply { isIndeterminate = true }, linearParams(top = 10))
        card.addView(TextView(this).apply {
            text = message
            textSize = 15f
            setLineSpacing(0f, 1.18f)
            setTextColor(if (dark) Color.LTGRAY else Color.rgb(89, 76, 62))
        }, linearParams(top = 9))
        if (buttons.isNotEmpty()) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
            buttons.forEach { (label, action) ->
                row.addView(Button(this).apply { text = label; setOnClickListener { action() } })
            }
            card.addView(row, linearParams(top = 10))
        }
        val container = LinearLayout(this).apply {
            setPadding(dp(14), 0, dp(14), 0)
            addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP; y = dp(66) }
        overlay = container
        windowManager.addView(container, params)
    }

    private fun linearParams(top: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun removeOverlay() {
        overlay?.let { runCatching { windowManager.removeViewImmediate(it) } }
        overlay = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onUnbind(intent: Intent?): Boolean {
        captureJob?.cancel()
        removeOverlay()
        QuickCaptureSessionRegistry.resetAll()
        QuickCaptureTileService.reset(this)
        if (connectedInstance === this) connectedInstance = null
        return super.onUnbind(intent)
    }

    companion object {
        private const val SESSION_TIMEOUT_MILLIS = 90_000L
        @Volatile var connectedInstance: QuickCaptureAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val component = "${context.packageName}/${QuickCaptureAccessibilityService::class.java.name}"
            return enabled.split(':').any { it.equals(component, ignoreCase = true) }
        }
    }
}
