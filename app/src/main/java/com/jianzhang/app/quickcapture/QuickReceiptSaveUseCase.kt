package com.jianzhang.app.quickcapture

import android.content.Context
import com.jianzhang.app.data.local.AppDatabase
import com.jianzhang.app.data.local.entity.TransactionType
import com.jianzhang.app.data.repository.FinanceRepository
import com.jianzhang.app.domain.account.AccountSelectionPolicy
import kotlinx.coroutines.flow.first

data class QuickReceiptSaveResult(
    val transactionId: String,
    val accountName: String
)

class QuickReceiptSaveUseCase internal constructor(
    private val database: AppDatabase
) {
    constructor(context: Context) : this(AppDatabase.getInstance(context))

    private val repository = FinanceRepository(database)

    suspend fun save(draft: QuickCaptureDraft): QuickReceiptSaveResult {
        val receipt = draft.receipt
        // Resolve again at the actual write boundary. The editor already does
        // this when it opens; direct confirmation must use the same rule even
        // if the overlay draft cached the configured default account.
        val accounts = database.financeDao().observeAccounts().first()
        val accountId = requireNotNull(
            AccountSelectionPolicy.resolveReceiptAccountId(
                accounts = accounts,
                paymentMethod = receipt.paymentMethod,
                defaultAccountId = draft.resolvedAccountId,
                recognitionText = receipt.rawText
            )
        ) { "请先设置可用的记账账户" }
        val transactionId = repository.saveTransaction(
            transactionId = "quick-${draft.sessionId}",
            amountInCents = requireNotNull(receipt.amountInCents) { "请核对金额" },
            type = TransactionType.EXPENSE,
            categoryId = receipt.categoryId,
            accountId = accountId,
            date = receipt.date,
            time = receipt.time,
            note = receipt.merchant,
            merchant = receipt.merchant.ifBlank { null },
            paymentMethod = receipt.paymentMethod.ifBlank { null },
            importSource = "quick_capture"
        )
        return QuickReceiptSaveResult(
            transactionId = transactionId,
            accountName = accounts.first { it.id == accountId }.name
        )
    }
}
