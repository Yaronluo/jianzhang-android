package com.jianzhang.app.quickcapture

import com.jianzhang.app.data.local.entity.AccountEntity

/**
 * Accounts that are safe to use as the one-tap capture default.
 *
 * Demo accounts are kept in the assets screen so the demo data remains
 * inspectable, but they must not become an implicit destination for a real
 * receipt captured from another app.
 */
object QuickCaptureAccountPolicy {
    fun defaultCandidates(accounts: List<AccountEntity>): List<AccountEntity> =
        accounts.filter(::isEligibleDefaultAccount)

    fun isEligibleDefaultAccount(account: AccountEntity): Boolean =
        account.enabled && account.canUseForTransactions && !account.isDemo
}
