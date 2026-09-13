package com.jianzhang.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountKeypadTest {
    @Test
    fun amountInputSupportsDecimalAndDoubleZero() {
        assertEquals("0.", appendAmountToken("", "."))
        assertEquals("100", appendAmountToken("1", "00"))
        assertEquals("1", appendAmountToken("0", "1"))
    }

    @Test
    fun amountInputLimitsPrecisionAndIntegerLength() {
        assertEquals("12.34", appendAmountToken("12.34", "5"))
        assertEquals("123456789", appendAmountToken("123456789", "0"))
    }
}
