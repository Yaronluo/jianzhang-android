package com.jianzhang.app.quickcapture

import com.jianzhang.app.data.local.entity.AccountEntity
import com.jianzhang.app.data.local.entity.AccountType
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickCaptureAccountPolicyTest {
    @Test
    fun defaultCandidates_onlyIncludeEnabledRealTransactionAccounts() {
        val real = account("real", "工商银行")
        val demo = account("demo", "支付宝", isDemo = true)
        val disabled = account("disabled", "现金", enabled = false)
        val notForTransactions = account("readonly", "银行卡", canUseForTransactions = false)

        assertEquals(
            listOf(real),
            QuickCaptureAccountPolicy.defaultCandidates(listOf(real, demo, disabled, notForTransactions))
        )
    }

    @Test
    fun defaultCandidates_preserveAssetAccountOrder() {
        val first = account("first", "现金")
        val second = account("second", "工商银行")

        assertEquals(
            listOf(first, second),
            QuickCaptureAccountPolicy.defaultCandidates(listOf(first, second))
        )
    }

    private fun account(
        id: String,
        name: String,
        isDemo: Boolean = false,
        enabled: Boolean = true,
        canUseForTransactions: Boolean = true
    ) = AccountEntity(
        id = id,
        name = name,
        type = AccountType.CASH,
        balanceInCents = 0L,
        icon = "💵",
        isDemo = isDemo,
        createdAt = 1L,
        updatedAt = 1L,
        enabled = enabled,
        canUseForTransactions = canUseForTransactions
    )
}
