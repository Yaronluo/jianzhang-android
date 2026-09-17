package com.jianzhang.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.jianzhang.app.quickcapture.SmartCard
import com.jianzhang.app.quickcapture.SmartCardStore
import com.jianzhang.app.quickcapture.SmartCardType
import com.jianzhang.app.quickcapture.SmartNotificationManager
import com.jianzhang.app.ui.components.BackTitleBar
import com.jianzhang.app.ui.components.EmptyState

@Composable
fun SmartItemsScreen(
    selectedCardId: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { SmartCardStore(context) }
    val cards by remember(store) { store.observeActiveCards() }
        .collectAsState(initial = store.activeCards())
    var filter by rememberSaveable { mutableStateOf("全部") }
    var openedCardId by rememberSaveable { mutableStateOf(selectedCardId) }

    LaunchedEffect(selectedCardId, cards) {
        if (selectedCardId != null && cards.any { it.id == selectedCardId }) {
            openedCardId = selectedCardId
        }
    }

    val visibleCards = cards.filter { card ->
        filter == "全部" || card.type.matchesFilter(filter)
    }
    val openedCard = cards.firstOrNull { it.id == openedCardId }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                )
        ) {
            BackTitleBar(
                title = "当前事项",
                onBack = onBack,
                action = {
                    Text(
                        "${cards.size} 项",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("全部", "取餐", "取件", "出行").forEach { option ->
                    val selected = filter == option
                    Surface(
                        onClick = { filter = option },
                        shape = RoundedCornerShape(50),
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        contentColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(
                            option,
                            modifier = Modifier.padding(horizontal = 17.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
            if (visibleCards.isEmpty()) {
                EmptyState(
                    icon = "✨",
                    title = if (cards.isEmpty()) "暂时没有当前事项" else "这个分类没有事项",
                    message = if (cards.isEmpty()) "识别取餐码、取件码或行程后，事项会显示在这里。" else "切换分类查看其他事项。"
                )
            } else {
                Text(
                    "按最近识别排序",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                visibleCards.forEachIndexed { index, card ->
                    SmartItemRow(
                        card = card,
                        onClick = { openedCardId = card.id }
                    )
                    if (index != visibleCards.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 54.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
                        )
                    }
                }
            }
        }

        openedCard?.let { card ->
            AlertDialog(
                onDismissRequest = { openedCardId = null },
                title = { Text(card.title) },
                text = { Text(card.content, lineHeight = 23.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        store.complete(card.id)
                        SmartNotificationManager(context).cancel(card.id)
                        openedCardId = null
                    }) {
                        Text(card.type.completionLabel())
                    }
                },
                dismissButton = {
                    TextButton(onClick = { openedCardId = null }) { Text("取消") }
                }
            )
        }
    }
}

@Composable
private fun SmartItemRow(card: SmartCard, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
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
                Icon(card.type.icon(), contentDescription = card.type.displayLabel(), modifier = Modifier.size(22.dp))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    card.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    card.type.completionLabel(),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                card.content.replace("\n", " · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 21.sp
            )
        }
    }
}

private fun SmartCardType.matchesFilter(filter: String): Boolean = when (filter) {
    "取餐" -> this == SmartCardType.PICKUP
    "取件" -> this == SmartCardType.PACKAGE
    "出行" -> this == SmartCardType.TRAVEL
    else -> true
}

private fun SmartCardType.displayLabel(): String = when (this) {
    SmartCardType.PICKUP -> "取餐"
    SmartCardType.PACKAGE -> "取件"
    SmartCardType.TRAVEL -> "出行"
}

private fun SmartCardType.completionLabel(): String = when (this) {
    SmartCardType.PICKUP -> "已取餐"
    SmartCardType.PACKAGE -> "已取件"
    SmartCardType.TRAVEL -> "结束行程"
}

private fun SmartCardType.icon(): ImageVector = when (this) {
    SmartCardType.PICKUP -> Icons.Default.Restaurant
    SmartCardType.PACKAGE -> Icons.Default.Inventory2
    SmartCardType.TRAVEL -> Icons.Default.FlightTakeoff
}
