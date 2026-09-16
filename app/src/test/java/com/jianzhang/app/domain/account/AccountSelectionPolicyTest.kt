package com.jianzhang.app.domain.account

import com.jianzhang.app.data.local.entity.AccountEntity
import com.jianzhang.app.data.local.entity.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSelectionPolicyTest {
    @Test
    fun transactionAccounts_onlyReturnAccountsThatExistInAssets() {
        val industrialBank = account("industrial", "工商银行")

        assertEquals(
            listOf(industrialBank),
            AccountSelectionPolicy.transactionAccounts(listOf(industrialBank))
        )
    }

    @Test
    fun transactionAccounts_includeDemoAssetsForFeatureTesting() {
        val real = account("real", "工商银行")
        val demo = account("demo", "支付宝", isDemo = true)

        assertEquals(
            listOf(real, demo),
            AccountSelectionPolicy.transactionAccounts(listOf(real, demo))
        )
        assertTrue(AccountSelectionPolicy.isSelectable(demo))
    }

    @Test
    fun transactionAccounts_excludeDisabledAndNonTransactionAccounts() {
        val disabled = account("disabled", "现金", enabled = false)
        val notForTransactions = account("readonly", "银行卡", canUseForTransactions = false)

        assertEquals(
            emptyList<AccountEntity>(),
            AccountSelectionPolicy.transactionAccounts(listOf(disabled, notForTransactions))
        )
        assertFalse(AccountSelectionPolicy.isSelectable(disabled))
        assertFalse(AccountSelectionPolicy.isSelectable(notForTransactions))
    }

    @Test
    fun transactionAccounts_retainExistingSelectionWithoutAddingMissingAccounts() {
        val legacy = account("legacy", "旧账户", canUseForTransactions = false)
        val current = account("current", "现金")

        assertEquals(
            listOf(legacy, current),
            AccountSelectionPolicy.transactionAccounts(
                accounts = listOf(legacy, current),
                retainedAccountIds = setOf(legacy.id, "missing")
            )
        )
    }

    @Test
    fun resolveReceiptAccount_recognizedExistingAccountWinsOverDefault() {
        val default = account("bank", "工商银行", type = AccountType.BANK_CARD)
        val wechat = account("wechat", "微信零钱", type = AccountType.WECHAT)

        assertEquals(
            wechat.id,
            AccountSelectionPolicy.resolveReceiptAccountId(
                accounts = listOf(default, wechat),
                paymentMethod = "微信支付-零钱",
                defaultAccountId = default.id
            )
        )
    }

    @Test
    fun resolveReceiptAccount_exactBankNameWinsOverDefaultAndGenericBankAsset() {
        val defaultCash = account("cash", "现金", type = AccountType.CASH)
        val demoBank = account("demo-bank", "银行卡", type = AccountType.BANK_CARD, isDemo = true)
        val industrialBank = account("icbc", "工商银行", type = AccountType.BANK_CARD)

        assertEquals(
            industrialBank.id,
            AccountSelectionPolicy.resolveReceiptAccountId(
                accounts = listOf(defaultCash, demoBank, industrialBank),
                paymentMethod = "工商银行储蓄卡(3679)",
                defaultAccountId = defaultCash.id
            )
        )
    }

    @Test
    fun resolveReceiptAccount_usesRawPaymentLineWhenStructuredMethodIsBlank() {
        val defaultCash = account("cash", "现金", type = AccountType.CASH)
        val industrialBank = account("icbc", "工商银行", type = AccountType.BANK_CARD)

        assertEquals(
            industrialBank.id,
            AccountSelectionPolicy.resolveReceiptAccountId(
                accounts = listOf(defaultCash, industrialBank),
                paymentMethod = "",
                defaultAccountId = defaultCash.id,
                recognitionText = "商户全称 长沙市望城区学森购物超市\n支付方式 工商银行储蓄卡(3679)\n交易单号 4200003159"
            )
        )
    }

    @Test
    fun resolveReceiptAccount_fallsBackToDefaultWhenRecognizedTypeIsMissing() {
        val default = account("bank", "工商银行", type = AccountType.BANK_CARD)
        val cash = account("cash", "现金", type = AccountType.CASH)

        assertEquals(
            default.id,
            AccountSelectionPolicy.resolveReceiptAccountId(
                accounts = listOf(cash, default),
                paymentMethod = "支付宝付款",
                defaultAccountId = default.id
            )
        )
    }

    @Test
    fun resolveReceiptAccount_usesFirstAssetWhenRecognitionAndDefaultAreUnavailable() {
        val cash = account("cash", "现金", type = AccountType.CASH)

        assertEquals(
            cash.id,
            AccountSelectionPolicy.resolveReceiptAccountId(
                accounts = listOf(cash),
                paymentMethod = "",
                defaultAccountId = "missing"
            )
        )
    }

    private fun account(
        id: String,
        name: String,
        type: AccountType = AccountType.CASH,
        isDemo: Boolean = false,
        enabled: Boolean = true,
        canUseForTransactions: Boolean = true
    ) = AccountEntity(
        id = id,
        name = name,
        type = type,
        balanceInCents = 0L,
        icon = "💵",
        isDemo = isDemo,
        createdAt = 1L,
        updatedAt = 1L,
        enabled = enabled,
        canUseForTransactions = canUseForTransactions
    )
}
