package com.jianzhang.app.ui.screens

import android.content.Context
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.jianzhang.app.data.local.entity.AccountType
import com.jianzhang.app.data.local.entity.CategoryType
import com.jianzhang.app.domain.ai.AiReceiptClient
import com.jianzhang.app.domain.ai.AiReceiptConfig
import com.jianzhang.app.domain.ai.AiReceiptSettings
import com.jianzhang.app.domain.ai.AiRecognitionMode
import com.jianzhang.app.domain.ocr.LocalReceiptOcr
import com.jianzhang.app.domain.parser.ParsedReceipt
import com.jianzhang.app.domain.parser.ReceiptParser
import com.jianzhang.app.ui.FinanceUiState
import com.jianzhang.app.util.formatInputMoney
import com.jianzhang.app.util.parseMoneyToCents
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

private sealed interface ReceiptEntryPhase {
    data object ChooseSource : ReceiptEntryPhase
    data object Recognizing : ReceiptEntryPhase
    data class Confirm(val receipt: ParsedReceipt) : ReceiptEntryPhase
    data class Failed(val message: String) : ReceiptEntryPhase
}

@Composable
fun ReceiptEntryScreen(
    state: FinanceUiState,
    aiSettings: AiReceiptSettings,
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
    var phase by remember { mutableStateOf<ReceiptEntryPhase>(ReceiptEntryPhase.ChooseSource) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    fun processImage(uri: Uri) {
        phase = ReceiptEntryPhase.Recognizing
        scope.launch {
            val cloudResult = if (aiConfig != null) {
                runCatching {
                    when (aiConfig.mode) {
                        AiRecognitionMode.VISION -> ReceiptParser.parseAiResponse(
                            responseText = AiReceiptClient.recognizeImage(context, uri, aiConfig),
                            sourceName = "${aiConfig.provider.displayName} AI"
                        )
                        AiRecognitionMode.OCR_TEXT -> {
                            val localText = LocalReceiptOcr.recognize(context, uri)
                            ReceiptParser.parseAiResponse(
                                responseText = AiReceiptClient.analyzeOcrText(aiConfig, localText),
                                sourceName = "本地 OCR + ${aiConfig.provider.displayName} AI"
                            ).let { parsed -> parsed.copy(rawText = parsed.rawText.ifBlank { localText }) }
                        }
                        AiRecognitionMode.LOCAL -> error("本地模式不调用云端")
                    }
                }
            } else null

            if (cloudResult?.isSuccess == true) {
                phase = ReceiptEntryPhase.Confirm(cloudResult.getOrThrow())
                return@launch
            }

            runCatching { LocalReceiptOcr.recognize(context, uri) }
                .onSuccess { text ->
                    phase = if (text.isBlank()) {
                        ReceiptEntryPhase.Failed("没有识别到文字。请把票据放平、对焦后重试，也可以继续手动填写。")
                    } else {
                        val parsed = ReceiptParser.parse(text)
                        ReceiptEntryPhase.Confirm(
                            if (cloudResult?.isFailure == true) {
                                parsed.copy(
                                    warnings = listOf("云端 AI 暂不可用，已自动改用本地识别，请重点核对") + parsed.warnings,
                                    recognitionSource = "本地备用识别"
                                )
                            } else parsed
                        )
                    }
                }
                .onFailure {
                    val cloudMessage = cloudResult?.exceptionOrNull()?.message
                    phase = ReceiptEntryPhase.Failed(
                        cloudMessage?.let { "AI 识别失败（${it.take(120)}），本地识别也未成功。请重试或手动填写。" }
                            ?: "识别失败。请换一张清晰图片重试，也可以继续手动填写。"
                    )
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
            ReceiptEntryPhase.Recognizing -> ReceiptRecognizing(onBack = onBack, cloudEnabled = aiConfig != null)
            is ReceiptEntryPhase.Failed -> ReceiptFailure(
                message = current.message,
                onBack = onBack,
                onRetry = { phase = ReceiptEntryPhase.ChooseSource },
                onManual = {
                    phase = ReceiptEntryPhase.Confirm(
                        ReceiptParser.parse("", LocalDate.now(), LocalTime.now())
                    )
                }
            )
            is ReceiptEntryPhase.Confirm -> ReceiptConfirmation(
                state = state,
                parsed = current.receipt,
                onBack = { phase = ReceiptEntryPhase.ChooseSource },
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
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ReceiptHeader(title = "简易记账", onBack = onBack)
        Spacer(modifier = Modifier.height(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.ReceiptLong,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text("拍一张，少写几步", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "拍摄购物小票，或导入发票、支付账单截图。识别后先核对，再保存到账本。",
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 28.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BigReceiptButton(
            text = "拍照识别",
            icon = { Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(30.dp)) },
            onClick = onCamera
        )
        BigReceiptButton(
            text = "从相册导入",
            icon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(30.dp)) },
            onClick = onGallery
        )
        Spacer(modifier = Modifier.weight(1f))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (aiConfig != null) "自定义 AI 已启用" else "本地隐私",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            aiConfig != null && aiConfig.mode == AiRecognitionMode.VISION ->
                                "你点击拍照或导入后，所选图片将发送到你配置的 ${aiConfig.provider.displayName}。应用不读取微信、支付宝账户。"
                            aiConfig != null && aiConfig.mode == AiRecognitionMode.OCR_TEXT ->
                                "图片在本机进行 OCR，只将识别文字发送到你配置的 ${aiConfig.provider.displayName}。应用不读取微信、支付宝账户。"
                            else ->
                                "只处理你主动拍摄或选择的图片；文字识别在当前设备完成，不连接微信、支付宝账户，也没有票据上传步骤。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 21.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiptRecognizing(onBack: () -> Unit, cloudEnabled: Boolean) {
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
            if (cloudEnabled) "正在调用你配置的 AI…" else "正在读取票据文字…",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text("请稍等，识别完成后还需要你核对。", style = MaterialTheme.typography.bodyLarge)
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
            Text("手动填写并确认", fontSize = 18.sp)
        }
    }
}

@Composable
private fun ReceiptConfirmation(
    state: FinanceUiState,
    parsed: ParsedReceipt,
    onBack: () -> Unit,
    onSave: (String, String, String?, String, String, String, String, String) -> Unit
) {
    val categories = state.categories.filter { it.type == CategoryType.EXPENSE && it.enabled }
    val accounts = state.accounts.filter { it.enabled && it.canUseForTransactions }
    var amount by remember(parsed) { mutableStateOf(parsed.amountInCents?.let(::formatInputMoney).orEmpty()) }
    var date by remember(parsed) { mutableStateOf(parsed.date) }
    var time by remember(parsed) { mutableStateOf(parsed.time) }
    var merchant by remember(parsed) { mutableStateOf(parsed.merchant) }
    var categoryId by remember(parsed, categories) {
        mutableStateOf(parsed.categoryId.takeIf { id -> categories.any { it.id == id } } ?: categories.firstOrNull()?.id.orEmpty())
    }
    var accountId by remember(parsed, accounts) { mutableStateOf(suggestedAccountId(accounts, parsed.paymentMethod)) }
    var note by remember(parsed) { mutableStateOf("") }
    var amountReviewed by remember(parsed) { mutableStateOf(!parsed.amountNeedsReview) }
    var showRawText by rememberSaveable(parsed.rawText) { mutableStateOf(false) }
    val amountValid = parseMoneyToCents(amount) != null
    val dateValid = runCatching { LocalDate.parse(date) }.isSuccess
    val timeValid = runCatching { LocalTime.parse(time) }.isSuccess
    val canSave = amountValid && dateValid && timeValid && categoryId.isNotBlank() && amountReviewed
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
                    else -> "请选择消费分类"
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ReceiptHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(28.dp))
        }
        Text(title, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BigReceiptButton(
    text: String,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(72.dp),
        shape = RoundedCornerShape(22.dp)
    ) {
        icon?.invoke()
        Text(text, modifier = Modifier.padding(start = if (icon == null) 0.dp else 12.dp), fontSize = 21.sp, fontWeight = FontWeight.Bold)
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
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(selectedText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "选择$label")
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemText(item), fontSize = 17.sp) },
                    onClick = {
                        onSelected(item)
                        expanded = false
                    }
                )
            }
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
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedId }
    Box {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("账户", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        selected?.let { "${it.icon} ${it.name}" } ?: "不关联账户",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (recognizedPaymentMethod.isNotBlank()) {
                        Text(
                            "识别到付款方式：$recognizedPaymentMethod",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "选择账户")
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("不关联账户", fontSize = 17.sp) },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text("${account.icon} ${account.name}", fontSize = 17.sp) },
                    onClick = {
                        onSelected(account.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun suggestedAccountId(accounts: List<AccountEntity>, paymentMethod: String): String? {
    if (accounts.isEmpty()) return null
    if (paymentMethod.isBlank()) return accounts.first().id
    accounts.firstOrNull { account ->
        paymentMethod.contains(account.name, ignoreCase = true)
    }?.let { return it.id }
    val suggestedType = when {
        paymentMethod.contains("信用卡") -> AccountType.CREDIT_CARD
        paymentMethod.contains("储蓄卡") || paymentMethod.contains("银行卡") || paymentMethod.contains("银行") -> AccountType.BANK_CARD
        paymentMethod.contains("零钱") || paymentMethod.contains("微信") -> AccountType.WECHAT
        paymentMethod.contains("支付宝") -> AccountType.ALIPAY
        paymentMethod.contains("现金") -> AccountType.CASH
        else -> null
    }
    return accounts.firstOrNull { it.type == suggestedType }?.id ?: accounts.first().id
}

private fun createCameraUri(context: Context): Uri {
    val directory = File(context.cacheDir, "receipts").apply { mkdirs() }
    val file = File.createTempFile("receipt_", ".jpg", directory)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
