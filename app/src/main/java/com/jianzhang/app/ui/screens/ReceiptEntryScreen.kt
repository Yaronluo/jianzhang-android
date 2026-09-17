package com.jianzhang.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.jianzhang.app.data.local.entity.AccountEntity
import com.jianzhang.app.data.local.entity.CategoryType
import com.jianzhang.app.domain.account.AccountSelectionPolicy
import com.jianzhang.app.domain.ai.AiReceiptConfig
import com.jianzhang.app.domain.ai.AiReceiptSettings
import com.jianzhang.app.domain.ai.AiRecognitionMode
import com.jianzhang.app.domain.parser.ParsedReceipt
import com.jianzhang.app.domain.parser.ReceiptParser
import com.jianzhang.app.quickcapture.ActionRouter
import com.jianzhang.app.quickcapture.ExpenseAction
import com.jianzhang.app.quickcapture.PackageAction
import com.jianzhang.app.quickcapture.PickupAction
import com.jianzhang.app.quickcapture.QuickCaptureDraft
import com.jianzhang.app.quickcapture.QuickCaptureRecognizer
import com.jianzhang.app.quickcapture.ScreenAction
import com.jianzhang.app.quickcapture.ScreenAnalysis
import com.jianzhang.app.quickcapture.TravelAction
import com.jianzhang.app.quickcapture.toParsedReceipt
import com.jianzhang.app.ui.FinanceUiState
import com.jianzhang.app.ui.components.AppDropdownField
import com.jianzhang.app.ui.components.AppDropdownItem
import com.jianzhang.app.ui.components.AccountTypeIcon
import com.jianzhang.app.ui.components.BackTitleBar
import com.jianzhang.app.util.formatInputMoney
import com.jianzhang.app.util.parseMoneyToCents
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

private sealed interface ReceiptEntryPhase {
    data object ChooseSource : ReceiptEntryPhase
    data object Recognizing : ReceiptEntryPhase
    data class Review(val analysis: ScreenAnalysis) : ReceiptEntryPhase
    data class Confirm(val receipt: ParsedReceipt) : ReceiptEntryPhase
    data class Completed(val title: String, val message: String) : ReceiptEntryPhase
    data class Failed(val message: String) : ReceiptEntryPhase
}

@Composable
fun ReceiptEntryScreen(
    state: FinanceUiState,
    aiSettings: AiReceiptSettings,
    initialReceipt: ParsedReceipt? = null,
    preferredAccountId: String? = null,
    requireAccount: Boolean = false,
    onBack: () -> Unit,
    onSave: (
        amount: String,
        categoryId: String,
        accountId: String?,
        date: String,
        time: String,
        merchant: String,
        paymentMethod: String,
        note: String
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aiConfig = aiSettings.activeConfig
    var phase by remember(initialReceipt) {
        mutableStateOf<ReceiptEntryPhase>(
            initialReceipt?.let(ReceiptEntryPhase::Confirm) ?: ReceiptEntryPhase.ChooseSource
        )
    }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var recognitionMessage by rememberSaveable { mutableStateOf("正在读取图片……") }

    fun handleBack() {
        if (
            initialReceipt != null ||
            phase is ReceiptEntryPhase.ChooseSource ||
            phase is ReceiptEntryPhase.Recognizing
        ) {
            onBack()
        } else {
            phase = ReceiptEntryPhase.ChooseSource
        }
    }

    BackHandler(onBack = ::handleBack)

    fun processImage(uri: Uri) {
        recognitionMessage = "正在读取图片……"
        phase = ReceiptEntryPhase.Recognizing
        scope.launch {
            runCatching {
                QuickCaptureRecognizer(context).recognize(uri) { _, message ->
                    recognitionMessage = message
                }
            }.onSuccess { analysis ->
                val actions = analysis.actions
                phase = when {
                    actions.isEmpty() -> ReceiptEntryPhase.Failed(
                        "没有识别到可执行的信息。请换一张内容更完整、文字更清晰的图片重试。"
                    )
                    actions.size == 1 && actions.single() is ExpenseAction ->
                        ReceiptEntryPhase.Confirm((actions.single() as ExpenseAction).toParsedReceipt())
                    else -> ReceiptEntryPhase.Review(analysis)
                }
            }.onFailure { error ->
                phase = ReceiptEntryPhase.Failed(
                    error.message?.take(160)?.ifBlank { null }
                        ?: "识别失败。请换一张清晰图片重试，也可以改为手动记账。"
                )
            }
        }
    }

    fun continueWithAnalysis(analysis: ScreenAnalysis) {
        val expense = analysis.actions.filterIsInstance<ExpenseAction>().firstOrNull()
        val otherActions = analysis.actions.filterNot { it is ExpenseAction }
        recognitionMessage = if (otherActions.isEmpty()) "正在准备账单核对……" else "正在保存已确认事项……"
        phase = ReceiptEntryPhase.Recognizing
        scope.launch {
            val result = runCatching {
                if (otherActions.isEmpty()) null else {
                    ActionRouter(context).executeConfirmed(
                        QuickCaptureDraft(
                            sessionId = UUID.randomUUID().toString(),
                            analysis = analysis.copy(actions = otherActions),
                            resolvedAccountId = null
                        )
                    )
                }
            }
            if (result.isFailure) {
                phase = ReceiptEntryPhase.Failed(
                    result.exceptionOrNull()?.message?.take(160) ?: "保存识别结果失败，请重试。"
                )
                return@launch
            }
            if (expense != null) {
                phase = ReceiptEntryPhase.Confirm(expense.toParsedReceipt())
            } else {
                val summary = result.getOrNull()
                val message = buildString {
                    append("已保存 ${summary?.executed ?: 0} 项到“当前事项”")
                    if ((summary?.duplicateOrSkipped ?: 0) > 0) append("，跳过重复或低置信结果 ${summary?.duplicateOrSkipped} 项")
                    if (summary?.notificationPermissionMissing == true) append("。通知权限未开启，事项仍会保留在应用内")
                }
                phase = ReceiptEntryPhase.Completed("处理完成", message)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) processImage(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri?.let(Uri::parse)
        if (success && uri != null) processImage(uri)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val current = phase) {
            ReceiptEntryPhase.ChooseSource -> ReceiptSourceChooser(
                aiConfig = aiConfig,
                onBack = onBack,
                onCamera = {
                    runCatching { createCameraUri(context) }
                        .onSuccess { uri ->
                            pendingCameraUri = uri.toString()
                            cameraLauncher.launch(uri)
                        }
                        .onFailure { phase = ReceiptEntryPhase.Failed("无法打开相机，请改用相册导入。") }
                },
                onGallery = { galleryLauncher.launch("image/*") }
            )
            ReceiptEntryPhase.Recognizing -> ReceiptRecognizing(onBack = onBack, message = recognitionMessage)
            is ReceiptEntryPhase.Review -> ActionReview(
                analysis = current.analysis,
                onBack = ::handleBack,
                onContinue = { continueWithAnalysis(current.analysis) }
            )
            is ReceiptEntryPhase.Failed -> ReceiptFailure(
                message = current.message,
                onBack = ::handleBack,
                onRetry = { phase = ReceiptEntryPhase.ChooseSource },
                onManual = {
                    phase = ReceiptEntryPhase.Confirm(
                        ReceiptParser.parse("", LocalDate.now(), LocalTime.now())
                    )
                }
            )
            is ReceiptEntryPhase.Completed -> RecognitionCompleted(
                title = current.title,
                message = current.message,
                onBack = onBack,
                onAgain = { phase = ReceiptEntryPhase.ChooseSource }
            )
            is ReceiptEntryPhase.Confirm -> ReceiptConfirmation(
                state = state,
                parsed = current.receipt,
                preferredAccountId = preferredAccountId,
                requireAccount = requireAccount,
                onBack = ::handleBack,
                onSave = onSave
            )
        }
    }
}

@Composable
private fun ReceiptSourceChooser(
    aiConfig: AiReceiptConfig?,
    onBack: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 22.dp, top = top + 8.dp, end = 22.dp, bottom = bottom + 18.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        ReceiptHeader(title = "AI 识别", onBack = onBack)
        Spacer(modifier = Modifier.height(34.dp))
        Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(38.dp))
            }
        }
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            "看懂图片，直接生成行动",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "拍下或导入截图，AI 会判断内容并生成对应操作；一张图也可以同时识别多项。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 25.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "记账  ·  取餐码  ·  取件提醒  ·  出行卡片",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onCamera,
                modifier = Modifier.weight(1f).height(68.dp),
                shape = RoundedCornerShape(21.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(25.dp))
                Text("拍照", modifier = Modifier.padding(start = 9.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onGallery,
                modifier = Modifier.weight(1f).height(68.dp),
                shape = RoundedCornerShape(21.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(25.dp))
                Text("选图片", modifier = Modifier.padding(start = 9.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                aiConfig?.let { "${it.provider.displayName} · ${it.mode.displayName}" } ?: "完全本地识别",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 24.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("只处理你主动选择的图片", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        aiConfig != null && aiConfig.mode == AiRecognitionMode.VISION ->
                            "图片会发送到你配置的 ${aiConfig.provider.displayName}；应用不会读取微信、支付宝账户。"
                        aiConfig != null && aiConfig.mode == AiRecognitionMode.OCR_TEXT ->
                            "图片先在本机提取文字，仅识别文字会发送到 ${aiConfig.provider.displayName}。"
                        else -> "图片和文字都在当前设备处理，不会上传。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 21.sp
                )
            }
        }
    }
}

@Composable
private fun ReceiptRecognizing(onBack: () -> Unit, message: String) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 22.dp, top = top + 8.dp, end = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        ReceiptHeader(title = "正在识别", onBack = onBack)
        Spacer(modifier = Modifier.height(80.dp))
        CircularProgressIndicator(modifier = Modifier.size(56.dp))
        Text(
            message,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text("完成后会先展示识别到的操作，由你确认是否执行。", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ActionReview(
    analysis: ScreenAnalysis,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val hasExpense = analysis.actions.any { it is ExpenseAction }
    val hasExecutableOther = analysis.actions.any { it !is ExpenseAction && it.confidence >= 0.65 }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, top = top + 8.dp, end = 22.dp, bottom = bottom + 24.dp)
    ) {
        ReceiptHeader(title = "识别结果", onBack = onBack)
        Spacer(modifier = Modifier.height(30.dp))
        Text(
            analysis.summary ?: "AI 从图片中找到了这些可执行信息",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(9.dp))
        Text(
            "共 ${analysis.actions.size} 项，请核对后再继续。记账信息仍会进入编辑页，不会直接写入账本。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )
        Spacer(modifier = Modifier.height(28.dp))
        analysis.actions.forEachIndexed { index, action ->
            ActionResultRow(action)
            if (index != analysis.actions.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 54.dp, top = 16.dp, bottom = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
                )
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onContinue,
            enabled = hasExpense || hasExecutableOther,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                when {
                    hasExpense && hasExecutableOther -> "保存事项并核对账单"
                    hasExpense -> "核对账单"
                    else -> "保存到当前事项"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (!hasExpense && !hasExecutableOther) {
            Text(
                "识别结果置信度过低，已禁止保存。请换一张更清晰的图片。",
                modifier = Modifier.padding(top = 10.dp).align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        Text(
            "识别方式：${analysis.source}",
            modifier = Modifier.padding(top = 14.dp).align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActionResultRow(action: ScreenAction) {
    val icon = when (action) {
        is ExpenseAction -> Icons.Default.Payments
        is PickupAction -> Icons.Default.Restaurant
        is PackageAction -> Icons.Default.Inventory2
        is TravelAction -> Icons.Default.FlightTakeoff
    }
    val title = when (action) {
        is ExpenseAction -> "消费记账"
        is PickupAction -> "取餐信息"
        is PackageAction -> "取件提醒"
        is TravelAction -> "出行卡片"
    }
    val detail = when (action) {
        is ExpenseAction -> listOfNotNull(
            action.amount?.let { "¥$it" },
            action.merchant
        ).joinToString(" · ").ifBlank { "金额和商户需要核对" }
        is PickupAction -> listOfNotNull(
            action.code?.let { "取餐码 $it" },
            action.merchant,
            action.location
        ).joinToString(" · ").ifBlank { "取餐信息需要核对" }
        is PackageAction -> listOfNotNull(
            action.code?.let { "取件码 $it" },
            action.company,
            action.location
        ).joinToString(" · ").ifBlank { "取件信息需要核对" }
        is TravelAction -> listOfNotNull(
            action.number,
            action.from?.let { from -> action.to?.let { "$from → $it" } },
            action.departureTime
        ).joinToString(" · ").ifBlank { "出行信息需要核对" }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        action.confidence >= 0.85 -> "可信"
                        action.confidence >= 0.65 -> "需核对"
                        else -> "可信度低"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (action.confidence >= 0.65) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun RecognitionCompleted(
    title: String,
    message: String,
    onBack: () -> Unit,
    onAgain: () -> Unit
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 22.dp, top = top + 8.dp, end = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ReceiptHeader(title = "AI 识别", onBack = onBack)
        Spacer(modifier = Modifier.height(90.dp))
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            message,
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(30.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(19.dp)) {
            Text("返回账本", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onAgain, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("继续识别", fontSize = 17.sp)
        }
    }
}

@Composable
private fun ReceiptFailure(
    message: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onManual: () -> Unit
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 22.dp, top = top + 8.dp, end = 22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        ReceiptHeader(title = "没有识别成功", onBack = onBack)
        Spacer(modifier = Modifier.height(40.dp))
        Text(message, style = MaterialTheme.typography.titleLarge, lineHeight = 30.sp)
        BigReceiptButton(text = "重新选择图片", onClick = onRetry)
        TextButton(onClick = onManual, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("改为手动记账", fontSize = 18.sp)
        }
    }
}

@Composable
private fun ReceiptConfirmation(
    state: FinanceUiState,
    parsed: ParsedReceipt,
    preferredAccountId: String?,
    requireAccount: Boolean,
    onBack: () -> Unit,
    onSave: (String, String, String?, String, String, String, String, String) -> Unit
) {
    val categories = state.categories.filter { it.type == CategoryType.EXPENSE && it.enabled }
    val accounts = AccountSelectionPolicy.transactionAccounts(state.accounts)
    var amount by remember(parsed) { mutableStateOf(parsed.amountInCents?.let(::formatInputMoney).orEmpty()) }
    var date by remember(parsed) { mutableStateOf(parsed.date) }
    var time by remember(parsed) { mutableStateOf(parsed.time) }
    var merchant by remember(parsed) { mutableStateOf(parsed.merchant) }
    var categoryId by remember(parsed, categories) {
        mutableStateOf(parsed.categoryId.takeIf { id -> categories.any { it.id == id } } ?: categories.firstOrNull()?.id.orEmpty())
    }
    var accountId by remember(parsed, accounts, preferredAccountId) {
        mutableStateOf(
            AccountSelectionPolicy.resolveReceiptAccountId(
                accounts = accounts,
                paymentMethod = parsed.paymentMethod,
                defaultAccountId = preferredAccountId,
                recognitionText = parsed.rawText
            )
        )
    }
    var note by remember(parsed) { mutableStateOf("") }
    var amountReviewed by remember(parsed) { mutableStateOf(!parsed.amountNeedsReview) }
    var showRawText by rememberSaveable(parsed.rawText) { mutableStateOf(false) }
    val amountValid = parseMoneyToCents(amount) != null
    val dateValid = runCatching { LocalDate.parse(date) }.isSuccess
    val timeValid = runCatching { LocalTime.parse(time) }.isSuccess
    val canSave = amountValid && dateValid && timeValid && categoryId.isNotBlank() && amountReviewed &&
        (!requireAccount || accountId != null)
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = top + 8.dp, end = 20.dp, bottom = bottom + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ReceiptHeader(title = "核对后记账", onBack = onBack)
        Text(
            "请检查每一项，确认后才会写入账本。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "识别方式：${parsed.recognitionSource}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        if (parsed.warnings.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    parsed.warnings.forEach { warning -> Text("• $warning", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        OutlinedTextField(
            value = amount,
            onValueChange = {
                amount = it
                if (parseMoneyToCents(it) != null) amountReviewed = true
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("实付金额") },
            prefix = { Text("¥ ") },
            isError = amount.isNotBlank() && !amountValid,
            supportingText = if (!amountValid) ({ Text("请填写大于 0 的金额") }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall
        )
        if (parsed.amountNeedsReview && !amountReviewed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = amountReviewed, onCheckedChange = { amountReviewed = it })
                Text("我已核对这个金额", style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                modifier = Modifier.weight(1.35f),
                label = { Text("日期") },
                isError = !dateValid,
                singleLine = true
            )
            OutlinedTextField(
                value = time,
                onValueChange = { time = it },
                modifier = Modifier.weight(0.75f),
                label = { Text("时间") },
                isError = !timeValid,
                singleLine = true
            )
        }
        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("商户名称") },
            placeholder = { Text("例如：幸福超市") },
            singleLine = true
        )
        ReceiptSelector(
            label = "消费分类",
            selectedText = categories.firstOrNull { it.id == categoryId }?.name ?: "请选择分类",
            items = categories,
            itemText = { it.name },
            onSelected = { categoryId = it.id }
        )
        ReceiptAccountSelector(
            accounts = accounts,
            selectedId = accountId,
            recognizedPaymentMethod = parsed.paymentMethod,
            onSelected = { accountId = it }
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("备注") },
            placeholder = { Text("可不填") },
            minLines = 2
        )
        TextButton(onClick = { showRawText = !showRawText }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showRawText) "收起识别原文" else "查看识别原文")
        }
        if (showRawText) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    parsed.rawText.ifBlank { "没有识别原文" },
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Button(
            onClick = {
                onSave(
                    amount,
                    categoryId,
                    accountId,
                    date,
                    time,
                    merchant.trim(),
                    parsed.paymentMethod,
                    note.trim()
                )
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(26.dp))
            Text("确认并记账", modifier = Modifier.padding(start = 10.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (!canSave) {
            Text(
                when {
                    !amountReviewed -> "请先核对金额"
                    !amountValid -> "请填写正确金额"
                    !dateValid || !timeValid -> "请填写正确日期和时间"
                    categoryId.isBlank() -> "请选择消费分类"
                    else -> "请选择记账账户"
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ReceiptHeader(title: String, onBack: () -> Unit) {
    BackTitleBar(title = title, onBack = onBack)
}

@Composable
private fun BigReceiptButton(
    text: String,
    icon: (@Composable () -> Unit)? = null,
    emphasized: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(66.dp),
        shape = RoundedCornerShape(21.dp),
        color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (emphasized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = if (emphasized) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.invoke()
            Text(text, modifier = Modifier.padding(start = if (icon == null) 0.dp else 12.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun <T> ReceiptSelector(
    label: String,
    selectedText: String,
    items: List<T>,
    itemText: (T) -> String,
    onSelected: (T) -> Unit
) {
    AppDropdownField(
        label = label,
        value = selectedText,
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) { dismiss ->
        items.forEach { item ->
            AppDropdownItem(
                text = itemText(item),
                selected = itemText(item) == selectedText,
                onClick = {
                    onSelected(item)
                    dismiss()
                }
            )
        }
    }
}

@Composable
private fun ReceiptAccountSelector(
    accounts: List<AccountEntity>,
    selectedId: String?,
    recognizedPaymentMethod: String,
    onSelected: (String?) -> Unit
) {
    val selected = accounts.firstOrNull { it.id == selectedId }
    AppDropdownField(
        label = "账户",
        value = selected?.name ?: "",
        placeholder = "不关联账户",
        supportingText = recognizedPaymentMethod
            .takeIf { it.isNotBlank() }
            ?.let { "识别到付款方式：$it" },
        modifier = Modifier.fillMaxWidth(),
        leadingContent = selected?.let { account ->
            {
                AccountTypeIcon(
                    type = account.type,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) { dismiss ->
        AppDropdownItem(
            text = "不关联账户",
            selected = selectedId == null,
            onClick = {
                onSelected(null)
                dismiss()
            }
        )
        accounts.forEach { account ->
            AppDropdownItem(
                text = account.name,
                selected = account.id == selectedId,
                leadingContent = {
                    AccountTypeIcon(
                        type = account.type,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    onSelected(account.id)
                    dismiss()
                }
            )
        }
    }
}

private fun createCameraUri(context: Context): Uri {
    val directory = File(context.cacheDir, "receipts").apply { mkdirs() }
    val file = File.createTempFile("receipt_", ".jpg", directory)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
