package com.jianzhang.app.quickcapture

import android.content.Context
import com.jianzhang.app.data.local.AppDatabase
import com.jianzhang.app.data.local.entity.TransactionType
import com.jianzhang.app.data.repository.FinanceRepository

class QuickReceiptSaveUseCase(context: Context) {
    private val repository = FinanceRepository(AppDatabase.getInstance(context))

    suspend fun save(draft: QuickCaptureDraft): String {
        val receipt = draft.receipt
        val accountId = requireNotNull(draft.defaultAccountId) { "请先设置默认记账账户" }
        return repository.saveTransaction(
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
    }
}
