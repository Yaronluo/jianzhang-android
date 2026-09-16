package com.jianzhang.app.quickcapture

import com.jianzhang.app.domain.parser.ParsedReceipt
import com.jianzhang.app.domain.parser.ReceiptFieldConfidence
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class QuickCaptureState {
    IDLE,
    CLOSING_SHADE,
    CAPTURING,
    RECOGNIZING_OCR,
    RECOGNIZING_AI,
    PARSING,
    AWAITING_CONFIRMATION,
    SAVING,
    SUCCESS,
    FAILED,
    CANCELLED
}

data class QuickCaptureDraft(
    val sessionId: String,
    val receipt: ParsedReceipt,
    val defaultAccountId: String?
)

/** Process-local state shared by the tile, accessibility service and confirmation activity. */
object QuickCaptureSessionRegistry {
    private val lock = Any()
    private var activeId: String? = null
    private var state: QuickCaptureState = QuickCaptureState.IDLE
    private var draft: QuickCaptureDraft? = null

    fun tryStart(): String? = synchronized(lock) {
        if (state !in terminalStates) return null
        UUID.randomUUID().toString().also {
            activeId = it
            state = QuickCaptureState.CLOSING_SHADE
            draft = null
        }
    }

    fun isActive(sessionId: String): Boolean = synchronized(lock) {
        activeId == sessionId && state !in terminalStates
    }

    fun transition(sessionId: String, next: QuickCaptureState): Boolean = synchronized(lock) {
        if (activeId != sessionId || state in terminalStates) return false
        state = next
        true
    }

    fun publishDraft(value: QuickCaptureDraft): Boolean = synchronized(lock) {
        if (activeId != value.sessionId || state in terminalStates) return false
        draft = value
        state = QuickCaptureState.AWAITING_CONFIRMATION
        true
    }

    fun getDraft(sessionId: String): QuickCaptureDraft? = synchronized(lock) {
        draft?.takeIf { it.sessionId == sessionId }
    }

    fun tryBeginSaving(sessionId: String): Boolean = synchronized(lock) {
        if (activeId != sessionId || state != QuickCaptureState.AWAITING_CONFIRMATION) return false
        state = QuickCaptureState.SAVING
        true
    }

    fun cancelIfAwaiting(sessionId: String): Boolean = synchronized(lock) {
        if (activeId != sessionId || state != QuickCaptureState.AWAITING_CONFIRMATION) return false
        activeId = null
        draft = null
        state = QuickCaptureState.IDLE
        true
    }

    fun finish(sessionId: String, finalState: QuickCaptureState) = synchronized(lock) {
        if (activeId == sessionId) {
            state = finalState
            if (finalState != QuickCaptureState.SUCCESS) draft = null
        }
    }

    fun reset(sessionId: String) = synchronized(lock) {
        if (activeId == sessionId) {
            activeId = null
            draft = null
            state = QuickCaptureState.IDLE
        }
    }

    fun currentState(): QuickCaptureState = synchronized(lock) { state }

    fun resetAll() = synchronized(lock) {
        activeId = null
        draft = null
        state = QuickCaptureState.IDLE
    }

    private val terminalStates = setOf(
        QuickCaptureState.IDLE,
        QuickCaptureState.SUCCESS,
        QuickCaptureState.FAILED,
        QuickCaptureState.CANCELLED
    )
}

object QuickCaptureEligibility {
    fun canDirectSave(draft: QuickCaptureDraft): Boolean {
        val receipt = draft.receipt
        return receipt.amountInCents?.let { it > 0 } == true &&
            runCatching { LocalDate.parse(receipt.date) }.isSuccess &&
            runCatching { LocalTime.parse(receipt.time) }.isSuccess &&
            receipt.categoryId.isNotBlank() &&
            draft.defaultAccountId != null &&
            receipt.amountConfidence == ReceiptFieldConfidence.HIGH &&
            receipt.dateConfidence == ReceiptFieldConfidence.HIGH &&
            receipt.categoryConfidence == ReceiptFieldConfidence.HIGH
    }
}

object QuickCaptureWindowGuard {
    fun isShadeClosed(activePackage: String?): Boolean =
        !activePackage.isNullOrBlank() && activePackage != SYSTEM_UI_PACKAGE

    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
}
