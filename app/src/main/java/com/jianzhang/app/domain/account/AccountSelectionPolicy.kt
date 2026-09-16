package com.jianzhang.app.domain.account

import com.jianzhang.app.data.local.entity.AccountEntity
import com.jianzhang.app.data.local.entity.AccountType

/**
 * Single source of truth for every transaction-account picker.
 *
 * The input list comes from the assets table, so this policy never invents
 * generic payment methods. Demo accounts are real asset rows too and remain
 * selectable for feature testing.
 */
object AccountSelectionPolicy {
    fun transactionAccounts(
        accounts: List<AccountEntity>,
        retainedAccountIds: Set<String> = emptySet()
    ): List<AccountEntity> = accounts.filter { account ->
        isSelectable(account) || account.id in retainedAccountIds
    }

    fun isSelectable(account: AccountEntity): Boolean =
        account.enabled && account.canUseForTransactions

    /**
     * Resolves the account for a recognized receipt without creating a
     * payment account that is absent from Assets.
     *
     * A recognized existing account wins, then the configured default, then
     * the first selectable asset as the final editable fallback.
     */
    fun resolveReceiptAccountId(
        accounts: List<AccountEntity>,
        paymentMethod: String,
        defaultAccountId: String?,
        recognitionText: String = ""
    ): String? {
        val candidates = transactionAccounts(accounts)
        if (candidates.isEmpty()) return null

        findRecognizedAccount(candidates, paymentMethod, recognitionText)?.let { return it.id }
        return candidates.firstOrNull { it.id == defaultAccountId }?.id
            ?: candidates.first().id
    }

    private fun findRecognizedAccount(
        accounts: List<AccountEntity>,
        paymentMethod: String,
        recognitionText: String
    ): AccountEntity? {
        val strongWords = listOf("储蓄卡", "信用卡", "银行卡", "零钱", "零钱通", "支付宝", "微信支付", "现金")
        val rawPaymentHint = recognitionText.lineSequence().firstOrNull { line ->
            line.contains("支付方式") || strongWords.any(line::contains)
        }.orEmpty()
        val sources = listOf(paymentMethod, rawPaymentHint).filter(String::isNotBlank)
        if (sources.isEmpty()) return null
        accounts.firstOrNull { account ->
            sources.any { source -> source.contains(account.name, ignoreCase = true) }
        }?.let { return it }

        val source = sources.joinToString(" ")
        val recognizedType = when {
            source.contains("信用卡") -> AccountType.CREDIT_CARD
            source.contains("储蓄卡") || source.contains("银行卡") || source.contains("银行") -> AccountType.BANK_CARD
            source.contains("零钱") || source.contains("微信") -> AccountType.WECHAT
            source.contains("支付宝") -> AccountType.ALIPAY
            source.contains("现金") -> AccountType.CASH
            else -> null
        }
        return accounts.firstOrNull { it.type == recognizedType }
    }
}
