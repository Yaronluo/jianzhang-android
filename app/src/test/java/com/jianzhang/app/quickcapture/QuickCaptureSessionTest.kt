package com.jianzhang.app.quickcapture

import com.jianzhang.app.domain.parser.ParsedReceipt
import com.jianzhang.app.domain.parser.ReceiptFieldConfidence
import com.jianzhang.app.data.model.DefaultData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test

class QuickCaptureSessionTest {
    @After
    fun tearDown() {
        QuickCaptureSessionRegistry.resetAll()
    }

    @Test
    fun duplicateStart_isRejectedUntilSessionEnds() {
        val first = QuickCaptureSessionRegistry.tryStart()
        assertNotNull(first)
        assertNull(QuickCaptureSessionRegistry.tryStart())
        QuickCaptureSessionRegistry.finish(first!!, QuickCaptureState.CANCELLED)
        QuickCaptureSessionRegistry.reset(first)
        assertNotNull(QuickCaptureSessionRegistry.tryStart()?.also {
            QuickCaptureSessionRegistry.finish(it, QuickCaptureState.CANCELLED)
            QuickCaptureSessionRegistry.reset(it)
        })
    }

    @Test
    fun saving_canBeginOnlyOnce() {
        val session = requireNotNull(QuickCaptureSessionRegistry.tryStart())
        val draft = QuickCaptureDraft(session, receipt(), "cash")
        assertTrue(QuickCaptureSessionRegistry.publishDraft(draft))
        assertTrue(QuickCaptureSessionRegistry.tryBeginSaving(session))
        assertFalse(QuickCaptureSessionRegistry.tryBeginSaving(session))
        QuickCaptureSessionRegistry.finish(session, QuickCaptureState.SUCCESS)
        QuickCaptureSessionRegistry.reset(session)
    }

    @Test
    fun directSave_requiresHighConfidenceAndResolvedAccount() {
        assertTrue(QuickCaptureEligibility.canDirectSave(QuickCaptureDraft("one", receipt(), "cash")))
        assertFalse(QuickCaptureEligibility.canDirectSave(QuickCaptureDraft("two", receipt(), null)))
        assertFalse(
            QuickCaptureEligibility.canDirectSave(
                QuickCaptureDraft("three", receipt().copy(amountConfidence = ReceiptFieldConfidence.LOW), "cash")
            )
        )
    }

    @Test
    fun directSave_rejectsMissingOrInvalidRequiredFields() {
        val valid = receipt()
        assertFalse(QuickCaptureEligibility.canDirectSave(QuickCaptureDraft("amount", valid.copy(amountInCents = null), "cash")))
        assertFalse(QuickCaptureEligibility.canDirectSave(QuickCaptureDraft("zero", valid.copy(amountInCents = 0), "cash")))
        assertFalse(QuickCaptureEligibility.canDirectSave(QuickCaptureDraft("date", valid.copy(date = "2026-02-30"), "cash")))
        assertFalse(QuickCaptureEligibility.canDirectSave(QuickCaptureDraft("time", valid.copy(time = "25:61"), "cash")))
        assertFalse(QuickCaptureEligibility.canDirectSave(QuickCaptureDraft("category", valid.copy(categoryId = ""), "cash")))
        assertFalse(
            QuickCaptureEligibility.canDirectSave(
                QuickCaptureDraft("date-confidence", valid.copy(dateConfidence = ReceiptFieldConfidence.LOW), "cash")
            )
        )
        assertFalse(
            QuickCaptureEligibility.canDirectSave(
                QuickCaptureDraft("category-confidence", valid.copy(categoryConfidence = ReceiptFieldConfidence.LOW), "cash")
            )
        )
    }

    @Test
    fun explicitAllExecute_allowsWeakCategoryButNotWeakAmount() {
        val genericCategory = receipt().copy(
            categoryId = DefaultData.OTHER_EXPENSE,
            categoryConfidence = ReceiptFieldConfidence.LOW
        )
        assertFalse(QuickCaptureEligibility.canDirectSave(QuickCaptureDraft("strict", genericCategory, "cash")))
        assertTrue(
            QuickCaptureEligibility.canSaveAfterUserConfirmation(
                QuickCaptureDraft("confirmed", genericCategory, "cash")
            )
        )
        assertFalse(
            QuickCaptureEligibility.canSaveAfterUserConfirmation(
                QuickCaptureDraft(
                    "amount-low",
                    genericCategory.copy(amountConfidence = ReceiptFieldConfidence.LOW),
                    "cash"
                )
            )
        )
    }

    @Test
    fun cancelledSession_rejectsLateRecognitionCallback() {
        val session = requireNotNull(QuickCaptureSessionRegistry.tryStart())
        QuickCaptureSessionRegistry.finish(session, QuickCaptureState.CANCELLED)

        assertFalse(QuickCaptureSessionRegistry.publishDraft(QuickCaptureDraft(session, receipt(), "cash")))
        assertNull(QuickCaptureSessionRegistry.getDraft(session))
        assertEquals(QuickCaptureState.CANCELLED, QuickCaptureSessionRegistry.currentState())
    }

    @Test
    fun staleSession_cannotOverwriteCurrentDraft() {
        val first = requireNotNull(QuickCaptureSessionRegistry.tryStart())
        QuickCaptureSessionRegistry.finish(first, QuickCaptureState.CANCELLED)
        val second = requireNotNull(QuickCaptureSessionRegistry.tryStart())

        assertFalse(QuickCaptureSessionRegistry.publishDraft(QuickCaptureDraft(first, receipt(), "cash")))
        assertTrue(QuickCaptureSessionRegistry.publishDraft(QuickCaptureDraft(second, receipt(), "cash")))
        assertEquals(second, QuickCaptureSessionRegistry.getDraft(second)?.sessionId)
    }

    @Test
    fun leavingEditor_cancelsOnlyAwaitingSession() {
        val session = requireNotNull(QuickCaptureSessionRegistry.tryStart())
        assertTrue(QuickCaptureSessionRegistry.publishDraft(QuickCaptureDraft(session, receipt(), "cash")))
        assertTrue(QuickCaptureSessionRegistry.cancelIfAwaiting(session))
        assertEquals(QuickCaptureState.IDLE, QuickCaptureSessionRegistry.currentState())
        assertNull(QuickCaptureSessionRegistry.getDraft(session))

        val savingSession = requireNotNull(QuickCaptureSessionRegistry.tryStart())
        assertTrue(QuickCaptureSessionRegistry.publishDraft(QuickCaptureDraft(savingSession, receipt(), "cash")))
        assertTrue(QuickCaptureSessionRegistry.tryBeginSaving(savingSession))
        assertFalse(QuickCaptureSessionRegistry.cancelIfAwaiting(savingSession))
        assertEquals(QuickCaptureState.SAVING, QuickCaptureSessionRegistry.currentState())
    }

    @Test
    fun shadeMustHaveNoSystemUiWindow() {
        assertFalse(QuickCaptureWindowGuard.isShadeClosed("com.android.systemui"))
        assertFalse(QuickCaptureWindowGuard.isShadeClosed(null))
        assertTrue(QuickCaptureWindowGuard.isShadeClosed("com.example.shop"))
    }

    private fun receipt() = ParsedReceipt(
        rawText = "实付 32.80",
        amountInCents = 3280,
        amountKeyword = "实付",
        amountConfidence = ReceiptFieldConfidence.HIGH,
        date = "2026-09-16",
        dateConfidence = ReceiptFieldConfidence.HIGH,
        time = "12:30",
        merchant = "测试商户",
        paymentMethod = "微信支付",
        categoryId = "food",
        categoryConfidence = ReceiptFieldConfidence.HIGH,
        warnings = emptyList(),
        recognitionSource = "OCR识别"
    )
}
