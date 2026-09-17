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
            val analysis = try {
                QuickCaptureRecognizer(this).recognize(bitmap) { state, text ->
                    QuickCaptureSessionRegistry.transition(sessionId, state)
                    scope.launch { showLoading(text, sessionId) }
                }
            } finally {
                bitmap.recycle()
            }
            val accounts = AppDatabase.getInstance(this).financeDao().observeAccounts().first()
            val expense = analysis.actions.filterIsInstance<ExpenseAction>().firstOrNull()
            val receipt = expense?.toParsedReceipt()
            val resolvedAccountId = receipt?.let {
                AccountSelectionPolicy.resolveReceiptAccountId(
                    accounts = accounts,
                    paymentMethod = it.paymentMethod,
                    defaultAccountId = QuickCapturePreferences(this).defaultAccountId,
                    recognitionText = analysis.rawText
                )
            }
            val draft = QuickCaptureDraft(
                sessionId = sessionId,
                analysis = analysis,
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
        val actions = draft.analysis.actions
        if (actions.isEmpty()) {
            showOverlay(
                title = "暂时无法理解当前页面",
                message = "没有生成任何操作。你可以关闭后换一张更清晰、信息更完整的页面重试。",
                buttons = listOf("关闭" to { cancelSession(draft.sessionId) })
            )
            return
        }
        if (actions.size > 1) {
            val lines = actions.joinToString("\n") { actionSummary(it) }
            val executable = actions.any { it.confidence >= MIN_REVIEW_CONFIDENCE }
            showOverlay(
                title = "检测到${actions.size}项信息",
                message = "${draft.analysis.source}\n$lines",
                buttons = buildList {
                    if (draft.expenseAction != null) add("查看修改" to { openEditor(draft.sessionId) })
                    if (executable) add("全部执行" to { executeActions(draft) })
                    add("关闭" to { cancelSession(draft.sessionId) })
                }
            )
            return
        }
        val only = actions.single()
        if (only !is ExpenseAction) {
            val low = only.confidence < MIN_REVIEW_CONFIDENCE
            val medium = only.confidence < AUTO_READY_CONFIDENCE
            showOverlay(
                title = draft.analysis.summary ?: actionTitle(only),
                message = "${draft.analysis.source}\n${actionSummary(only)}" + when {
                    low -> "\n可能检测到相关信息，但置信度过低，已禁止执行。"
                    medium -> "\n置信度一般，请重点核对后再固定。"
                    else -> ""
                },
                buttons = buildList {
                    add("取消" to { cancelSession(draft.sessionId) })
                    if (!low) add("固定到通知栏" to { executeActions(draft) })
                }
            )
            return
        }
        val receipt = only.toParsedReceipt()
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

    private fun actionTitle(action: ScreenAction): String = when (action) {
        is ExpenseAction -> action.merchant ?: "消费记录"
        is PickupAction -> action.merchant ?: "取餐信息"
        is PackageAction -> action.company ?: "取件信息"
        is TravelAction -> action.number ?: "出行信息"
    }

    private fun actionSummary(action: ScreenAction): String {
        val prefix = when {
            action.confidence < MIN_REVIEW_CONFIDENCE -> "可能 · "
            action.confidence < AUTO_READY_CONFIDENCE -> "需核对 · "
            else -> ""
        }
        return prefix + when (action) {
            is ExpenseAction -> "消费 ${action.amount?.let { "¥$it" } ?: "金额待核对"} ${action.merchant.orEmpty()}".trim()
            is PickupAction -> "取餐码 ${action.code ?: "待核对"} ${action.merchant.orEmpty()}".trim()
            is PackageAction -> "取件码 ${action.code ?: "待核对"} ${action.company.orEmpty()}".trim()
            is TravelAction -> listOfNotNull(action.number, action.from?.let { from -> action.to?.let { "$from → $it" } }).joinToString(" · ").ifBlank { "出行信息待核对" }
        }
    }

    private fun executeActions(draft: QuickCaptureDraft) {
        val expenseCanDirectSave = draft.expenseAction?.let { QuickCaptureEligibility.canDirectSave(draft) } == true
        val canFinishImmediately = draft.expenseAction == null || expenseCanDirectSave
        if (canFinishImmediately && !QuickCaptureSessionRegistry.tryBeginSaving(draft.sessionId)) return
        showLoading("正在执行已确认操作……", draft.sessionId)
        scope.launch {
            runCatching { ActionRouter(this@QuickCaptureAccessibilityService).executeConfirmed(draft) }
                .onSuccess { result ->
                    if (result.expenseNeedsReview) {
                        if (result.notificationPermissionMissing) {
                            android.widget.Toast.makeText(
                                this@QuickCaptureAccessibilityService,
                                "通知权限未开启，事项已保留在应用内",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        openEditor(draft.sessionId)
                        return@onSuccess
                    }
                    QuickCaptureSessionRegistry.finish(draft.sessionId, QuickCaptureState.SUCCESS)
                    val message = buildString {
                        append("已执行 ${result.executed} 项")
                        if (result.duplicateOrSkipped > 0) append("，跳过 ${result.duplicateOrSkipped} 项重复或低置信结果")
                        if (result.notificationPermissionMissing) append("\n通知权限未开启，事项已保留在应用内")
                    }
                    showOverlay("✓ 操作完成", message, success = true)
                    delay(1_800)
                    removeOverlay()
                    QuickCaptureSessionRegistry.reset(draft.sessionId)
                    QuickCaptureTileService.reset(this@QuickCaptureAccessibilityService)
                }
                .onFailure { fail(draft.sessionId, it.message ?: "操作失败") }
        }
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
            title = "简记·慧屏",
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
        QuickCaptureSessionRegistry.resetAll()
        QuickCaptureTileService.reset(this)
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
        private const val SESSION_TIMEOUT_MILLIS = 120_000L
        private const val AUTO_READY_CONFIDENCE = 0.85
        private const val MIN_REVIEW_CONFIDENCE = 0.65
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
