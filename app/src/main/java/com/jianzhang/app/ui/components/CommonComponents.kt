package com.jianzhang.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.jianzhang.app.data.local.entity.AccountEntity
import com.jianzhang.app.data.local.entity.CategoryEntity
import com.jianzhang.app.data.local.entity.TransactionEntity
import com.jianzhang.app.data.local.entity.TransactionType
import com.jianzhang.app.util.formatDate
import com.jianzhang.app.util.formatMoney
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = { content() }
    )
}

@Composable
fun SectionTitle(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        action?.invoke()
    }
}

@Composable
fun EmptyState(icon: String, title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(icon, style = MaterialTheme.typography.displaySmall)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun BackTitleBar(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null, showBack: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        action?.invoke()
    }
}

@Composable
fun SummaryCard(
    income: Long,
    expense: Long,
    balance: Long,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("本月结余", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatMoney(balance),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (balance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                SummaryMetric("收入", formatMoney(income), MaterialTheme.colorScheme.primary, Icons.AutoMirrored.Filled.TrendingUp)
                SummaryMetric("支出", formatMoney(expense), MaterialTheme.colorScheme.error, Icons.AutoMirrored.Filled.TrendingDown)
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun TransactionRow(
    transaction: TransactionEntity,
    category: CategoryEntity?,
    account: AccountEntity?,
    toAccount: AccountEntity? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isTransfer = transaction.type == TransactionType.TRANSFER
    val title = transaction.note.ifBlank { if (isTransfer) "账户转账" else category?.name ?: "未分类" }
    val leftDetail = if (isTransfer) {
        listOf("账户转账", transaction.time).joinToString(" · ")
    } else {
        listOfNotNull(category?.name?.takeIf { transaction.note.isNotBlank() }, transaction.time)
            .joinToString(" · ")
    }
    val paymentMethod = if (isTransfer) {
        listOfNotNull(account?.name, toAccount?.name).joinToString(" → ").ifBlank { "账户转账" }
    } else {
        account?.name ?: "未选择账户"
    }
    val revealWidth = 112.dp
    val density = LocalDensity.current
    val revealWidthPx = with(density) { revealWidth.toPx() }
    val actionWidthPx = with(density) { 52.dp.toPx() }
    val swipeOffset = remember(transaction.id) { Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    var dragOffset by remember(transaction.id) { mutableFloatStateOf(0f) }
    var isDragging by remember(transaction.id) { mutableStateOf(false) }
    var rowWidthPx by remember(transaction.id) { mutableIntStateOf(0) }
    val displayedOffset = if (isDragging) dragOffset else swipeOffset.value
    val exposedWidthPx = -displayedOffset
    val deleteThresholdPx = rowWidthPx / 2f
    val destructiveProgress = if (deleteThresholdPx > revealWidthPx) {
        ((exposedWidthPx - revealWidthPx) / (deleteThresholdPx - revealWidthPx)).coerceIn(0f, 1f)
    } else {
        0f
    }
    // 删除按钮先从屏幕右侧由小到大出现，随后才展开编辑按钮；继续左滑时删除吞没编辑。
    val deleteRevealProgress = (exposedWidthPx / actionWidthPx).coerceIn(0f, 1f)
    val editRevealProgress = ((exposedWidthPx - actionWidthPx) / (revealWidthPx - actionWidthPx)).coerceIn(0f, 1f)
    val editActionWidth = 52.dp * editRevealProgress * (1f - destructiveProgress)
    val deleteActionWidthPx = actionWidthPx * deleteRevealProgress +
        (deleteThresholdPx - actionWidthPx).coerceAtLeast(0f) * destructiveProgress
    val deleteActionWidth = with(density) { deleteActionWidthPx.toDp() }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .onSizeChanged { rowWidthPx = it.width }
        ) {
            Row(
                modifier = Modifier.matchParentSize(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SwipeActionButton(
                    label = "编辑",
                    icon = Icons.Default.Edit,
                    color = Color(0xFF4D8FF7),
                    width = editActionWidth,
                    onClick = {
                        swipeScope.launch {
                            swipeOffset.animateTo(0f, tween(140, easing = FastOutSlowInEasing))
                        }
                        onEdit()
                    },
                    modifier = Modifier.graphicsLayer {
                        alpha = editRevealProgress * (1f - destructiveProgress)
                        scaleX = 0.76f + 0.24f * editRevealProgress
                        scaleY = 0.76f + 0.24f * editRevealProgress
                    }
                )
                Spacer(modifier = Modifier.width(4.dp * editRevealProgress * (1f - destructiveProgress)))
                SwipeActionButton(
                    label = "删除",
                    icon = Icons.Default.Delete,
                    color = Color(0xFFFF4D4F),
                    width = deleteActionWidth,
                    onClick = {
                        swipeScope.launch {
                            swipeOffset.animateTo(0f, tween(140, easing = FastOutSlowInEasing))
                        }
                        onDelete()
                    },
                    modifier = Modifier.graphicsLayer {
                        alpha = deleteRevealProgress
                        scaleX = 0.76f + 0.24f * deleteRevealProgress
                        scaleY = 0.76f + 0.24f * deleteRevealProgress
                    }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 58.dp)
                    .offset { IntOffset(displayedOffset.roundToInt(), 0) }
                    .background(MaterialTheme.colorScheme.background)
                    .pointerInput(transaction.id, revealWidthPx, rowWidthPx) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                isDragging = true
                                dragOffset = swipeOffset.value
                                swipeScope.launch { swipeOffset.stop() }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val maximumDrag = if (rowWidthPx > 0) rowWidthPx / 2f else revealWidthPx
                                dragOffset = (dragOffset + dragAmount).coerceIn(-maximumDrag, 0f)
                            },
                            onDragEnd = {
                                val shouldDelete = rowWidthPx > 0 && dragOffset <= -(rowWidthPx / 2f) * 0.97f
                                val finalOffset = if (dragOffset <= -revealWidthPx * 0.35f) -revealWidthPx else 0f
                                swipeScope.launch {
                                    swipeOffset.snapTo(dragOffset)
                                    isDragging = false
                                    swipeOffset.animateTo(
                                        targetValue = if (shouldDelete) 0f else finalOffset,
                                        animationSpec = tween(180, easing = FastOutSlowInEasing)
                                    )
                                }
                                if (shouldDelete) {
                                    onDelete()
                                }
                            },
                            onDragCancel = {
                                swipeScope.launch {
                                    swipeOffset.snapTo(dragOffset)
                                    isDragging = false
                                    swipeOffset.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
                                }
                            }
                        )
                    }
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isTransfer) "↔️" else category?.icon ?: "🧾", style = MaterialTheme.typography.bodyLarge)
                }
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp, end = 8.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        leftDetail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatMoney(
                            if (transaction.type == TransactionType.INCOME) transaction.amountInCents
                            else if (isTransfer) transaction.amountInCents
                            else -transaction.amountInCents,
                            signed = !isTransfer
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            transaction.type == TransactionType.INCOME -> MaterialTheme.colorScheme.primary
                            isTransfer -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                    Text(
                        paymentMethod,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 48.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun SwipeActionButton(
    label: String,
    icon: ImageVector,
    color: Color,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(width)
            .clipToBounds()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

@Composable
fun AccountRow(account: AccountEntity, onEdit: () -> Unit, onAdjust: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) { Text(account.icon, style = MaterialTheme.typography.titleMedium) }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(account.name, fontWeight = FontWeight.SemiBold)
            Text(if (account.isLiability) "负债账户" else "资产账户", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            formatMoney(account.balanceInCents),
            fontWeight = FontWeight.SemiBold,
            color = if (account.isLiability) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "编辑账户") }
        IconButton(onClick = onAdjust) { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "调整余额") }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "停用账户", tint = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun CheckBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Row(modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 3.dp))
        }
    }
}

@Composable
fun DateLabel(date: String) {
    Text(formatDate(date), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
fun SmallSpacer() = Spacer(modifier = Modifier.height(8.dp))
