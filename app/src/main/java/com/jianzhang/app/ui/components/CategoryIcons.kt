package com.jianzhang.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ElectricalServices
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.jianzhang.app.data.local.entity.AccountType
import com.jianzhang.app.data.model.DefaultData
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The single charcoal used by the selected monoline icon family. */
val LedgerIconColor = Color(0xFF4F4B59)

private const val MONO_STROKE_WIDTH = 2.6f

/**
 * Category icons are derived from stable IDs instead of legacy emoji stored in
 * the database. Incoming income categories and account-to-account transfers
 * use the same custom monoline family while keeping their meanings separate.
 */
fun categoryIcon(categoryId: String?, categoryName: String? = null): ImageVector = when (categoryId) {
    DefaultData.FOOD -> Icons.Outlined.Restaurant
    DefaultData.TRANSPORT -> Icons.Outlined.DirectionsBus
    DefaultData.SHOPPING -> Icons.Outlined.ShoppingBag
    DefaultData.ENTERTAINMENT -> Icons.Outlined.Movie
    DefaultData.STUDY -> Icons.AutoMirrored.Outlined.MenuBook
    DefaultData.HOUSING -> Icons.Outlined.Home
    DefaultData.MEDICAL -> Icons.Outlined.LocalHospital
    DefaultData.COMMUNICATION -> Icons.Outlined.Phone
    DefaultData.CLOTHING -> Icons.Outlined.Checkroom
    DefaultData.DAILY -> Icons.Outlined.CleaningServices
    DefaultData.FAMILY -> Icons.Outlined.People
    DefaultData.SPORTS -> Icons.Outlined.FitnessCenter
    DefaultData.PET -> Icons.Outlined.Pets
    DefaultData.TRAVEL -> Icons.Outlined.Flight
    DefaultData.CAR -> Icons.Outlined.DirectionsCar
    DefaultData.UTILITIES -> Icons.Outlined.ElectricalServices
    DefaultData.SUBSCRIPTION -> Icons.Outlined.Subscriptions
    DefaultData.SOCIAL -> Icons.Outlined.CardGiftcard
    DefaultData.INSURANCE -> Icons.Outlined.Security
    DefaultData.OTHER_EXPENSE -> Icons.Outlined.MoreHoriz
    DefaultData.SALARY -> IncomeSalaryIcon
    DefaultData.PART_TIME -> IncomePartTimeIcon
    DefaultData.LIVING_EXPENSE -> IncomeLivingExpenseIcon
    DefaultData.BONUS -> IncomeBonusIcon
    DefaultData.INVESTMENT_INCOME -> IncomeInvestmentIcon
    DefaultData.RED_PACKET -> IncomeRedPacketIcon
    DefaultData.REIMBURSEMENT -> IncomeReimbursementIcon
    DefaultData.REFUND -> IncomeRefundIcon
    DefaultData.RENT -> IncomeRentIcon
    DefaultData.TRANSFER_INCOME -> IncomeTransferIcon
    DefaultData.OTHER_INCOME -> IncomeOtherIcon
    else -> iconForCategoryName(categoryName)
}

private fun iconForCategoryName(categoryName: String?): ImageVector = when (categoryName) {
    "餐饮" -> Icons.Outlined.Restaurant
    "交通" -> Icons.Outlined.DirectionsBus
    "购物" -> Icons.Outlined.ShoppingBag
    "娱乐" -> Icons.Outlined.Movie
    "学习" -> Icons.AutoMirrored.Outlined.MenuBook
    "居住" -> Icons.Outlined.Home
    "医疗" -> Icons.Outlined.LocalHospital
    "通讯" -> Icons.Outlined.Phone
    "服饰" -> Icons.Outlined.Checkroom
    "日用" -> Icons.Outlined.CleaningServices
    "家庭" -> Icons.Outlined.People
    "运动" -> Icons.Outlined.FitnessCenter
    "宠物" -> Icons.Outlined.Pets
    "旅行" -> Icons.Outlined.Flight
    "汽车" -> Icons.Outlined.DirectionsCar
    "水电燃气" -> Icons.Outlined.ElectricalServices
    "订阅" -> Icons.Outlined.Subscriptions
    "人情" -> Icons.Outlined.CardGiftcard
    "保险" -> Icons.Outlined.Security
    "工资" -> IncomeSalaryIcon
    "兼职" -> IncomePartTimeIcon
    "生活费" -> IncomeLivingExpenseIcon
    "奖金" -> IncomeBonusIcon
    "投资收益" -> IncomeInvestmentIcon
    "红包" -> IncomeRedPacketIcon
    "报销" -> IncomeReimbursementIcon
    "退款" -> IncomeRefundIcon
    "租金" -> IncomeRentIcon
    "转账" -> IncomeTransferIcon
    "微信转账" -> IncomeTransferIcon
    else -> Icons.Outlined.MoreHoriz
}

@androidx.compose.runtime.Composable
fun CategoryIcon(
    categoryId: String?,
    categoryName: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color? = null,
    contentDescription: String? = categoryName
) {
    Icon(
        imageVector = categoryIcon(categoryId, categoryName),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint ?: LedgerIconColor
    )
}

/** Account icons use the same single-color monoline language as categories. */
@androidx.compose.runtime.Composable
fun AccountTypeIcon(
    type: AccountType,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color? = null,
    contentDescription: String? = null
) {
    Icon(
        imageVector = accountTypeIcon(type),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint ?: LedgerIconColor
    )
}

fun accountTypeIcon(type: AccountType): ImageVector = when (type) {
    AccountType.CASH -> AccountCashIcon
    AccountType.BANK_CARD -> AccountBankCardIcon
    AccountType.ALIPAY -> AccountAlipayIcon
    AccountType.WECHAT -> AccountWechatIcon
    AccountType.CREDIT_CARD -> AccountCreditCardIcon
    AccountType.OTHER -> AccountInvestmentIcon
}

private fun monolineIcon(
    name: String,
    content: ImageVector.Builder.() -> Unit
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 48f,
    viewportHeight = 48f
).apply(content).build()

private fun ImageVector.Builder.outlinePath(block: PathBuilder.() -> Unit) {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = MONO_STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        block()
    }
}

private fun PathBuilder.roundedRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
    val r = minOf(radius, (right - left) / 2f, (bottom - top) / 2f)
    val k = 0.5522848f
    moveTo(left + r, top)
    horizontalLineTo(right - r)
    curveTo(right - r + r * k, top, right, top + r - r * k, right, top + r)
    verticalLineTo(bottom - r)
    curveTo(right, bottom - r + r * k, right - r + r * k, bottom, right - r, bottom)
    horizontalLineTo(left + r)
    curveTo(left + r - r * k, bottom, left, bottom - r + r * k, left, bottom - r)
    verticalLineTo(top + r)
    curveTo(left, top + r - r * k, left + r - r * k, top, left + r, top)
    close()
}

private fun PathBuilder.circle(centerX: Float, centerY: Float, radius: Float) {
    val k = 0.5522848f
    moveTo(centerX + radius, centerY)
    curveTo(centerX + radius, centerY + radius * k, centerX + radius * k, centerY + radius, centerX, centerY + radius)
    curveTo(centerX - radius * k, centerY + radius, centerX - radius, centerY + radius * k, centerX - radius, centerY)
    curveTo(centerX - radius, centerY - radius * k, centerX - radius * k, centerY - radius, centerX, centerY - radius)
    curveTo(centerX + radius * k, centerY - radius, centerX + radius, centerY - radius * k, centerX + radius, centerY)
    close()
}

private fun PathBuilder.ellipse(centerX: Float, centerY: Float, radiusX: Float, radiusY: Float) {
    val k = 0.5522848f
    moveTo(centerX + radiusX, centerY)
    curveTo(centerX + radiusX, centerY + radiusY * k, centerX + radiusX * k, centerY + radiusY, centerX, centerY + radiusY)
    curveTo(centerX - radiusX * k, centerY + radiusY, centerX - radiusX, centerY + radiusY * k, centerX - radiusX, centerY)
    curveTo(centerX - radiusX, centerY - radiusY * k, centerX - radiusX * k, centerY - radiusY, centerX, centerY - radiusY)
    curveTo(centerX + radiusX * k, centerY - radiusY, centerX + radiusX, centerY - radiusY * k, centerX + radiusX, centerY)
    close()
}

private fun PathBuilder.currencySign(centerX: Float, centerY: Float, size: Float) {
    moveTo(centerX - size * 0.62f, centerY - size)
    lineTo(centerX, centerY - size * 0.22f)
    lineTo(centerX + size * 0.62f, centerY - size)
    moveTo(centerX - size * 0.62f, centerY - size * 0.12f)
    horizontalLineTo(centerX + size * 0.62f)
    moveTo(centerX - size * 0.62f, centerY + size * 0.34f)
    horizontalLineTo(centerX + size * 0.62f)
    moveTo(centerX, centerY - size * 0.22f)
    verticalLineTo(centerY + size)
}

private fun PathBuilder.star(centerX: Float, centerY: Float, radius: Float) {
    val innerRadius = radius * 0.44f
    for (index in 0 until 10) {
        val angle = (-90.0 + index * 36.0) * PI / 180.0
        val currentRadius = if (index % 2 == 0) radius else innerRadius
        val x = centerX + cos(angle).toFloat() * currentRadius
        val y = centerY + sin(angle).toFloat() * currentRadius
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun PathBuilder.plus(centerX: Float, centerY: Float, size: Float) {
    moveTo(centerX - size, centerY)
    horizontalLineTo(centerX + size)
    moveTo(centerX, centerY - size)
    verticalLineTo(centerY + size)
}

private val AccountCashIcon = monolineIcon("AccountCash") {
    outlinePath {
        roundedRect(6f, 12f, 42f, 36f, 5f)
        moveTo(6f, 18f)
        horizontalLineTo(42f)
        circle(24f, 27f, 5f)
        currencySign(24f, 27f, 2.8f)
        moveTo(11f, 31f)
        horizontalLineTo(14f)
        moveTo(34f, 23f)
        horizontalLineTo(37f)
    }
}

private val AccountBankCardIcon = monolineIcon("AccountBankCard") {
    outlinePath {
        roundedRect(5f, 9f, 43f, 38f, 5f)
        moveTo(5f, 17f)
        horizontalLineTo(43f)
        roundedRect(10f, 23f, 20f, 30f, 1.5f)
        moveTo(27f, 25f)
        horizontalLineTo(37f)
        moveTo(27f, 30f)
        horizontalLineTo(34f)
    }
}

private val AccountAlipayIcon = monolineIcon("AccountAlipay") {
    outlinePath {
        // A compact 支-shaped mark plus the familiar Alipay sweep.
        moveTo(13f, 13f)
        horizontalLineTo(35f)
        moveTo(24f, 8f)
        verticalLineTo(24f)
        moveTo(16f, 20f)
        horizontalLineTo(33f)
        moveTo(19f, 21f)
        curveTo(21f, 27f, 27f, 31f, 35f, 33f)
        moveTo(31f, 20f)
        curveTo(28f, 29f, 21f, 35f, 11f, 38f)
        moveTo(8f, 32f)
        curveTo(18f, 30f, 31f, 32f, 40f, 38f)
    }
}

private val AccountWechatIcon = monolineIcon("AccountWechat") {
    outlinePath {
        // Two overlapping chat bubbles are WeChat's most recognizable cue.
        ellipse(19f, 20f, 12f, 9f)
        moveTo(10f, 26f)
        lineTo(8f, 32f)
        lineTo(15f, 29f)
        circle(15f, 19f, 1.2f)
        circle(23f, 19f, 1.2f)
        ellipse(30f, 29f, 11f, 8f)
        moveTo(37f, 35f)
        lineTo(40f, 39f)
        lineTo(34f, 37f)
        circle(27f, 28f, 1.1f)
        circle(34f, 28f, 1.1f)
    }
}

private val AccountCreditCardIcon = monolineIcon("AccountCreditCard") {
    outlinePath {
        roundedRect(5f, 11f, 40f, 36f, 5f)
        moveTo(5f, 18f)
        horizontalLineTo(40f)
        roundedRect(10f, 23f, 19f, 29f, 1.5f)
        moveTo(26f, 28f)
        horizontalLineTo(34f)
        star(39f, 11f, 5f)
    }
}

private val AccountInvestmentIcon = monolineIcon("AccountInvestment") {
    outlinePath {
        moveTo(7f, 39f)
        verticalLineTo(10f)
        moveTo(7f, 39f)
        horizontalLineTo(42f)
        roundedRect(12f, 27f, 17f, 39f, 1f)
        roundedRect(22f, 21f, 27f, 39f, 1f)
        roundedRect(32f, 14f, 37f, 39f, 1f)
        moveTo(11f, 22f)
        lineTo(21f, 17f)
        lineTo(28f, 19f)
        lineTo(39f, 8f)
        moveTo(39f, 8f)
        lineTo(34f, 9f)
        moveTo(39f, 8f)
        lineTo(38f, 13f)
    }
}

private val IncomeSalaryIcon = monolineIcon("IncomeSalary") {
    outlinePath {
        roundedRect(7f, 11f, 38f, 31f, 4f)
        circle(21f, 21f, 5.5f)
        currencySign(21f, 21f, 3.2f)
        circle(34f, 31f, 6f)
        moveTo(34f, 34f)
        verticalLineTo(28f)
        moveTo(34f, 28f)
        lineTo(31.5f, 30.5f)
        moveTo(34f, 28f)
        lineTo(36.5f, 30.5f)
    }
}

private val IncomePartTimeIcon = monolineIcon("IncomePartTime") {
    outlinePath {
        roundedRect(7f, 14f, 35f, 32f, 3.5f)
        moveTo(16f, 14f)
        verticalLineTo(11f)
        curveTo(16f, 9.9f, 16.9f, 9f, 18f, 9f)
        horizontalLineTo(24f)
        curveTo(25.1f, 9f, 26f, 9.9f, 26f, 11f)
        verticalLineTo(14f)
        moveTo(7f, 21f)
        horizontalLineTo(35f)
        roundedRect(20.5f, 19f, 24.5f, 23f, 1f)
        circle(35f, 31f, 6f)
        moveTo(35f, 31f)
        verticalLineTo(27.8f)
        moveTo(35f, 31f)
        horizontalLineTo(38f)
    }
}

private val IncomeLivingExpenseIcon = monolineIcon("IncomeLivingExpense") {
    outlinePath {
        roundedRect(8f, 13f, 36f, 31f, 4f)
        moveTo(8f, 19f)
        curveTo(15f, 21f, 27f, 21f, 36f, 19f)
        circle(34f, 29f, 6f)
        currencySign(34f, 29f, 3f)
        moveTo(11f, 16f)
        horizontalLineTo(20f)
    }
}

private val IncomeBonusIcon = monolineIcon("IncomeBonus") {
    outlinePath {
        moveTo(17f, 8f)
        lineTo(21f, 16f)
        moveTo(31f, 8f)
        lineTo(27f, 16f)
        moveTo(17f, 8f)
        horizontalLineTo(31f)
        circle(24f, 27f, 9f)
        star(24f, 27f, 4.2f)
        moveTo(37f, 13f)
        verticalLineTo(18f)
        moveTo(34.5f, 15.5f)
        horizontalLineTo(39.5f)
    }
}

private val IncomeInvestmentIcon = monolineIcon("IncomeInvestment") {
    outlinePath {
        ellipse(13f, 16f, 6f, 3f)
        moveTo(7f, 16f)
        verticalLineTo(29f)
        curveTo(7f, 30.7f, 9.7f, 32f, 13f, 32f)
        curveTo(16.3f, 32f, 19f, 30.7f, 19f, 29f)
        moveTo(7f, 22f)
        curveTo(7f, 23.7f, 9.7f, 25f, 13f, 25f)
        curveTo(16.3f, 25f, 19f, 23.7f, 19f, 22f)
        moveTo(7f, 16f)
        curveTo(7f, 17.7f, 9.7f, 19f, 13f, 19f)
        curveTo(16.3f, 19f, 19f, 17.7f, 19f, 16f)
        roundedRect(24f, 25f, 28f, 32f, 1f)
        roundedRect(30f, 20f, 34f, 32f, 1f)
        roundedRect(36f, 14f, 40f, 32f, 1f)
        moveTo(22f, 16f)
        curveTo(27f, 15f, 31f, 13f, 35f, 10f)
        lineTo(39f, 7f)
        moveTo(39f, 7f)
        lineTo(35.5f, 7.5f)
        moveTo(39f, 7f)
        lineTo(38.5f, 10.5f)
    }
}

private val IncomeRedPacketIcon = monolineIcon("IncomeRedPacket") {
    outlinePath {
        roundedRect(9f, 10f, 39f, 33f, 4f)
        moveTo(10f, 12f)
        curveTo(17f, 19f, 31f, 19f, 38f, 12f)
        circle(24f, 16.8f, 2.3f)
        moveTo(24f, 19.2f)
        verticalLineTo(24f)
        lineTo(21.5f, 27.5f)
        lineTo(24f, 30f)
        lineTo(26.5f, 27.5f)
        lineTo(24f, 24f)
        moveTo(20.5f, 30f)
        horizontalLineTo(27.5f)
    }
}

private val IncomeReimbursementIcon = monolineIcon("IncomeReimbursement") {
    outlinePath {
        moveTo(11f, 8f)
        horizontalLineTo(34f)
        verticalLineTo(28f)
        lineTo(31f, 32f)
        lineTo(28f, 29f)
        lineTo(25f, 32f)
        lineTo(22f, 29f)
        lineTo(19f, 32f)
        lineTo(16f, 29f)
        lineTo(11f, 32f)
        close()
        moveTo(16f, 14f)
        horizontalLineTo(29f)
        moveTo(16f, 19f)
        horizontalLineTo(26f)
        moveTo(16f, 24f)
        horizontalLineTo(22f)
        circle(35f, 29f, 6f)
        moveTo(32.5f, 29f)
        lineTo(34.2f, 30.7f)
        lineTo(37.8f, 27f)
    }
}

private val IncomeRefundIcon = monolineIcon("IncomeRefund") {
    outlinePath {
        circle(24f, 25f, 8.5f)
        currencySign(24f, 25f, 4.2f)
        moveTo(16f, 17f)
        lineTo(11.5f, 21f)
        lineTo(17f, 22.5f)
        moveTo(12f, 20.5f)
        curveTo(15f, 14f, 23f, 11.5f, 30f, 15f)
        curveTo(35f, 17.5f, 37f, 23f, 35f, 28f)
    }
}

private val IncomeRentIcon = monolineIcon("IncomeRent") {
    outlinePath {
        moveTo(8f, 22f)
        lineTo(24f, 9f)
        lineTo(40f, 22f)
        moveTo(12f, 19f)
        verticalLineTo(34f)
        horizontalLineTo(36f)
        verticalLineTo(19f)
        moveTo(21f, 34f)
        verticalLineTo(27f)
        curveTo(21f, 25.3f, 22.3f, 24f, 24f, 24f)
        curveTo(25.7f, 24f, 27f, 25.3f, 27f, 27f)
        verticalLineTo(34f)
        circle(27f, 18f, 3.2f)
        moveTo(27f, 21.2f)
        lineTo(20f, 28f)
        moveTo(20f, 28f)
        lineTo(18f, 26f)
        moveTo(22.3f, 25.7f)
        lineTo(24.3f, 27.7f)
    }
}

private val IncomeTransferIcon = monolineIcon("IncomeTransferIncome") {
    outlinePath {
        // A message bubble represents an outside sender; the arrow and coin
        // show money arriving as income rather than moving between own accounts.
        roundedRect(7f, 10f, 31f, 29f, 4f)
        moveTo(13f, 29f)
        lineTo(10f, 34f)
        lineTo(17f, 29f)
        circle(14f, 19.5f, 1.2f)
        circle(19f, 19.5f, 1.2f)
        circle(24f, 19.5f, 1.2f)
        circle(36f, 30f, 7f)
        currencySign(36f, 30f, 3.5f)
        moveTo(34f, 24f)
        lineTo(34f, 18f)
        moveTo(34f, 18f)
        lineTo(31.5f, 20.5f)
        moveTo(34f, 18f)
        lineTo(36.5f, 20.5f)
    }
}

/** A two-account exchange icon for internal account-to-account transfers. */
internal val AccountTransferIcon = monolineIcon("AccountTransfer") {
    outlinePath {
        // Two outlined account cards make this distinct from an incoming transfer.
        roundedRect(5f, 7f, 21f, 20f, 3f)
        moveTo(8f, 12f)
        horizontalLineTo(18f)
        circle(17f, 16f, 1.5f)
        roundedRect(27f, 28f, 43f, 41f, 3f)
        moveTo(30f, 33f)
        horizontalLineTo(40f)
        circle(39f, 37f, 1.5f)

        // Paired arrows show money moving between the user's own accounts.
        moveTo(17f, 24f)
        horizontalLineTo(31f)
        moveTo(31f, 24f)
        lineTo(28f, 21.5f)
        moveTo(31f, 24f)
        lineTo(28f, 26.5f)
        moveTo(31f, 28f)
        horizontalLineTo(17f)
        moveTo(17f, 28f)
        lineTo(20f, 25.5f)
        moveTo(17f, 28f)
        lineTo(20f, 30.5f)
    }
}

private val IncomeOtherIcon = monolineIcon("IncomeOther") {
    outlinePath {
        ellipse(15f, 16f, 7f, 3.2f)
        moveTo(8f, 16f)
        verticalLineTo(29f)
        curveTo(8f, 30.8f, 11.1f, 32f, 15f, 32f)
        curveTo(18.9f, 32f, 22f, 30.8f, 22f, 29f)
        moveTo(8f, 22f)
        curveTo(8f, 23.8f, 11.1f, 25f, 15f, 25f)
        curveTo(18.9f, 25f, 22f, 23.8f, 22f, 22f)
        moveTo(8f, 16f)
        curveTo(8f, 17.8f, 11.1f, 19f, 15f, 19f)
        curveTo(18.9f, 19f, 22f, 17.8f, 22f, 16f)
        circle(34f, 28f, 7f)
        plus(34f, 28f, 3.2f)
    }
}
