package com.jianzhang.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jianzhang.app.domain.ai.AiProvider
import com.jianzhang.app.domain.ai.AiReceiptClient
import com.jianzhang.app.domain.ai.AiReceiptSettings
import com.jianzhang.app.domain.ai.AiRecognitionMode
import com.jianzhang.app.domain.ai.AiServiceProfile
import com.jianzhang.app.ui.components.AppCard
import com.jianzhang.app.ui.components.AppDropdownField
import com.jianzhang.app.ui.components.AppDropdownItem
import com.jianzhang.app.ui.components.BackTitleBar
import kotlinx.coroutines.launch

@Composable
fun AiSettingsScreen(
    settings: AiReceiptSettings,
    onBack: () -> Unit,
    onSaveSettings: (AiReceiptSettings) -> Unit,
    onClearSettings: () -> Unit
) {
    var editingProfile by remember { mutableStateOf<AiServiceProfile?>(null) }
    var deleteProfile by remember { mutableStateOf<AiServiceProfile?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    editingProfile?.let { profile ->
        AiProfileEditor(
            initialProfile = profile,
            recognitionMode = settings.mode,
            onBack = { editingProfile = null },
            onSave = { saved ->
                val exists = settings.profiles.any { it.id == saved.id }
                val profiles = if (exists) {
                    settings.profiles.map { if (it.id == saved.id) saved else it }
                } else {
                    settings.profiles + saved
                }
                onSaveSettings(
                    settings.copy(
                        profiles = profiles,
                        activeProfileId = settings.activeProfileId ?: saved.id
                    )
                )
                editingProfile = null
            }
        )
        return
    }

    deleteProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteProfile = null },
            title = { Text("删除 ${profile.name}？") },
            text = { Text("只会删除这个服务的地址、模型和加密保存的 API Key。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val remaining = settings.profiles.filterNot { it.id == profile.id }
                        onSaveSettings(
                            settings.copy(
                                profiles = remaining,
                                activeProfileId = if (settings.activeProfileId == profile.id) {
                                    remaining.firstOrNull()?.id
                                } else {
                                    settings.activeProfileId
                                },
                                enabled = settings.enabled && remaining.isNotEmpty()
                            )
                        )
                        deleteProfile = null
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteProfile = null }) { Text("取消") } }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清除全部 AI 服务？") },
            text = { Text("所有已保存服务和 API Key 都会从本机删除，且无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearSettings()
                        showClearConfirm = false
                    }
                ) { Text("全部清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTitleBar(title = "AI 识票设置", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AppCard(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(50.dp),
                            shape = RoundedCornerShape(17.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(26.dp))
                            }
                        }
                        val activeProfile = settings.activeProfile
                        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                            Text("自定义 AI 识票", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (activeProfile?.isConfigured == true) "当前连接：${activeProfile.name}" else "添加服务后即可启用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.enabled,
                            onCheckedChange = { onSaveSettings(settings.copy(enabled = it)) }
                        )
                    }
                }
            }

            item {
                Text("识别方式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    AiRecognitionMode.entries.forEach { mode ->
                        ModeChoice(
                            mode = mode,
                            selected = settings.mode == mode,
                            onClick = { onSaveSettings(settings.copy(mode = mode)) }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("已保存服务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "可保存多个，切换时不用重新填写",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = { editingProfile = AiServiceProfile.create() }, shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(" 添加")
                    }
                }
            }

            if (settings.profiles.isEmpty()) {
                item {
                    AppCard(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            Text("还没有 AI 服务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "添加千问、豆包、智谱、混元、DeepSeek 或兼容接口",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(settings.profiles, key = { it.id }) { profile ->
                    AiProfileCard(
                        profile = profile,
                        selected = settings.activeProfileId == profile.id,
                        onSelect = {
                            onSaveSettings(settings.copy(activeProfileId = profile.id, enabled = true))
                        },
                        onEdit = { editingProfile = profile },
                        onDelete = { deleteProfile = profile }
                    )
                }
                item {
                    TextButton(
                        onClick = { showClearConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("清除全部 AI 服务", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Column(modifier = Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("你的密钥只留在本机", fontWeight = FontWeight.SemiBold)
                            Text(
                                "API Key 经系统密钥加密保存。只有你主动选择的图片或文字会按所选模式发送，结果仍需确认后入账。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChoice(mode: AiRecognitionMode, selected: Boolean, onClick: () -> Unit) {
    val explanation = when (mode) {
        AiRecognitionMode.VISION -> "适合支持看图的多模态模型，通常识别更完整"
        AiRecognitionMode.OCR_TEXT -> "适合文本模型，也能减少上传内容"
        AiRecognitionMode.LOCAL -> "不调用 API，准确率受本地 OCR 和规则影响"
    }
    val icon = when (mode) {
        AiRecognitionMode.VISION -> Icons.Default.ImageSearch
        AiRecognitionMode.OCR_TEXT -> Icons.Default.DocumentScanner
        AiRecognitionMode.LOCAL -> Icons.Default.Security
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(15.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(23.dp))
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(mode.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun AiProfileCard(
    profile: AiServiceProfile,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f))
        }
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onSelect)
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.name, fontWeight = FontWeight.SemiBold)
                        if (selected) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                onClick = onSelect,
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text(
                                    "使用中",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Text(
                        "${profile.provider.displayName} · ${profile.model.ifBlank { "未填模型" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (profile.isConfigured) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = if (profile.isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Text(
                        if (profile.isConfigured) "配置完整" else "配置不完整",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                Row {
                    TextButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Text(" 编辑")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "删除 ${profile.name}")
                    }
                }
            }
        }
    }
}

@Composable
private fun AiProfileEditor(
    initialProfile: AiServiceProfile,
    recognitionMode: AiRecognitionMode,
    onBack: () -> Unit,
    onSave: (AiServiceProfile) -> Unit
) {
    var draft by remember(initialProfile.id) { mutableStateOf(initialProfile) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val scope = rememberCoroutineScope()
    val valid = draft.name.isNotBlank() && draft.baseUrl.trim().startsWith("https://") &&
        draft.apiKey.isNotBlank() && draft.model.isNotBlank()

    Column(modifier = Modifier.fillMaxSize()) {
        BackTitleBar(title = "配置 AI 服务", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AppCard(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(15.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(23.dp))
                        }
                    }
                    Column(modifier = Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("连接你的 AI 服务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("信息仅保存在当前设备，可随时修改", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            AppCard {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("服务信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it); testResult = null },
                        label = { Text("配置名称") },
                        placeholder = { Text("例如：我的千问") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppDropdownField(
                        label = "服务商",
                        value = draft.provider.displayName,
                        modifier = Modifier.fillMaxWidth()
                    ) { dismiss ->
                        AiProvider.entries.forEach { provider ->
                            AppDropdownItem(
                                text = provider.displayName,
                                selected = draft.provider == provider,
                                onClick = {
                                    val oldProvider = draft.provider
                                    draft = draft.copy(
                                        provider = provider,
                                        name = if (draft.name.isBlank() || draft.name == oldProvider.displayName) {
                                            provider.displayName
                                        } else {
                                            draft.name
                                        },
                                        baseUrl = provider.defaultBaseUrl,
                                        model = provider.defaultModel
                                    )
                                    testResult = null
                                    dismiss()
                                }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = draft.baseUrl,
                        onValueChange = { draft = draft.copy(baseUrl = it); testResult = null },
                        label = { Text("API 地址（OpenAI 兼容）") },
                        supportingText = { Text("可填 Base URL，也可填完整 /chat/completions 地址") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = draft.model,
                        onValueChange = { draft = draft.copy(model = it); testResult = null },
                        label = { Text("模型名称或接入点 ID") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = draft.apiKey,
                        onValueChange = { draft = draft.copy(apiKey = it); testResult = null },
                        label = { Text("API Key") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Icon(
                                    if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (apiKeyVisible) "隐藏 Key" else "显示 Key"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            ProviderHelp(draft)

            testResult?.let { (success, message) ->
                AppCard(
                    colors = CardDefaults.cardColors(
                        containerColor = if (success) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                        }
                    )
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            if (success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null
                        )
                        Text(message, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            val testMode = recognitionMode.takeUnless { it == AiRecognitionMode.LOCAL }
                                ?: AiRecognitionMode.OCR_TEXT
                            val result = AiReceiptClient.testConnection(draft.asConfig(testMode))
                            testResult = result.fold(
                                onSuccess = { true to "连接成功：地址、Key 和模型均可响应当前测试。" },
                                onFailure = { false to (it.message ?: "连接失败，请检查配置") }
                            )
                            testing = false
                        }
                    },
                    enabled = valid && !testing,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text(if (testing) "正在测试…" else "测试连接") }
                Button(
                    onClick = { onSave(draft.copy(name = draft.name.trim(), baseUrl = draft.baseUrl.trim(), apiKey = draft.apiKey.trim(), model = draft.model.trim())) },
                    enabled = valid && !testing,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("保存服务") }
            }
            Text(
                "保存不要求先测试成功，避免因服务商临时限流或网络波动丢失配置。视觉模式测试会发送一张空白小图，可能产生极少量调用费用；API Key 不会明文写入设置文件。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProviderHelp(profile: AiServiceProfile) {
    val message = when (profile.provider) {
        AiProvider.QWEN -> if (profile.apiKey.trim().startsWith("sk-ws-")) {
            "检测到业务空间 Key：请从百炼控制台复制带 Workspace ID 和地域的完整 Base URL；它通常不能与通用 DashScope 地址混用。"
        } else {
            "千问的 Key、地域和 Base URL 必须配套。业务空间 Key 请使用控制台提供的 Workspace 专属地址。"
        }
        AiProvider.DOUBAO -> "豆包请按火山方舟控制台填写模型 ID 或推理接入点 ID，并确认该接入点已开通。"
        AiProvider.ZHIPU -> "请选择支持图片的 GLM 视觉模型；若使用本地 OCR 后交给 AI，也可配置文本模型。"
        AiProvider.HUNYUAN -> "请确认混元接口支持 OpenAI 兼容的 Chat Completions，并使用控制台显示的模型名称。"
        AiProvider.DEEPSEEK -> "直接看图片需使用支持视觉输入的模型；文本模型请把识别方式改为“本地 OCR 后交给 AI”。"
        AiProvider.CUSTOM -> "仅支持 Bearer 鉴权、OpenAI Chat Completions 兼容且使用 HTTPS 的接口。"
    }
    AppCard(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f)
        )
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.TipsAndUpdates, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Text(
                message,
                modifier = Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
