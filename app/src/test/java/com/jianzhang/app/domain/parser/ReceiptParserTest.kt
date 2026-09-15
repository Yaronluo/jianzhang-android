package com.jianzhang.app.domain.parser

import com.jianzhang.app.data.model.DefaultData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ReceiptParserTest {
    private val today = LocalDate.of(2026, 9, 15)
    private val now = LocalTime.of(14, 30)

    @Test
    fun parsesNormalShoppingReceipt() {
        val result = ReceiptParser.parse(
            """
            永辉超市大学城店
            销售小票
            日期：2026-09-14 18:25
            商品小计 36.80
            优惠 5.00
            实付：￥31.80
            """.trimIndent(), today, now
        )

        assertEquals(3180L, result.amountInCents)
        assertEquals(ReceiptFieldConfidence.HIGH, result.amountConfidence)
        assertEquals("2026-09-14", result.date)
        assertEquals("18:25", result.time)
        assertEquals("永辉超市大学城店", result.merchant)
        assertEquals(DefaultData.SHOPPING, result.categoryId)
    }

    @Test
    fun parsesPaymentScreenshot() {
        val result = ReceiptParser.parse(
            """
            微信支付
            支付成功
            收款方：幸福面馆
            付款金额 ¥45.60
            支付时间 2026年9月15日 12:08
            交易号 420000123456789
            """.trimIndent(), today, now
        )

        assertEquals(4560L, result.amountInCents)
        assertEquals("幸福面馆", result.merchant)
        assertEquals(DefaultData.FOOD, result.categoryId)
        assertEquals("12:08", result.time)
    }

    @Test
    fun discountAndOriginalPriceDoNotOverridePaidAmount() {
        val result = ReceiptParser.parse(
            "原价 128.00\n会员折扣 28.00\n应付 100.00\n实付金额 88.00",
            today,
            now
        )

        assertEquals(8800L, result.amountInCents)
        assertEquals("实付金额", result.amountKeyword)
    }

    @Test
    fun picksKeywordAmountFromManyNumbers() {
        val result = ReceiptParser.parse(
            "订单号 202609150001\n商品 19.90\n配送费 3.00\n优惠 2.00\n订单金额：20.90\n09-15 09:06",
            today,
            now
        )

        assertEquals(2090L, result.amountInCents)
        assertEquals(ReceiptFieldConfidence.HIGH, result.amountConfidence)
    }

    @Test
    fun missingRecognitionRequiresManualAmount() {
        val result = ReceiptParser.parse("图片模糊\n未识别到有效内容", today, now)

        assertNull(result.amountInCents)
        assertEquals(ReceiptFieldConfidence.MISSING, result.amountConfidence)
        assertTrue(result.amountNeedsReview)
        assertTrue(result.warnings.any { it.contains("手动填写") })
    }

    @Test
    fun wrongOrUnlabelledTextIsOnlyLowConfidence() {
        val result = ReceiptParser.parse("便利店\n疑似金额 ￥66.6O\n另一个数字 ￥12.00", today, now)

        assertEquals(1200L, result.amountInCents)
        assertEquals(ReceiptFieldConfidence.LOW, result.amountConfidence)
        assertTrue(result.amountNeedsReview)
    }

    @Test
    fun fullWidthDigitsAndChineseDateAreNormalized() {
        val result = ReceiptParser.parse("同仁药房\n实付：￥１２．５０\n２０２６年９月１４日 ０９：０５", today, now)

        assertEquals(1250L, result.amountInCents)
        assertEquals("2026-09-14", result.date)
        assertEquals("09:05", result.time)
        assertEquals(DefaultData.MEDICAL, result.categoryId)
    }

    @Test
    fun parsesWechatPaymentDetailWithColumnOrderedOcr() {
        val result = ReceiptParser.parse(
            """
            15:37
            当前状态
            支付时间
            商品
            商戶全称
            收单机构
            支付万式
            交易单号
            商戶单号
            账单服务
            学森购物超市
            支付成功
            账单
            一7.30
            2026年9月13日 18:40:37
            长沙市学森购物超市
            长沙市望城区学森购物超市
            拉卡拉支付服务有限公司
            工商銀行储蓄卡(3679)
            对订单有疑惑
            4200003159202609130169653261
            可在支持的商户扫码退款
            7895215528896329
            在此商户的交易
            """.trimIndent(), today, now
        )

        assertEquals(730L, result.amountInCents)
        assertEquals(ReceiptFieldConfidence.HIGH, result.amountConfidence)
        assertEquals("2026-09-13", result.date)
        assertEquals("18:40", result.time)
        assertEquals("长沙市望城区学森购物超市", result.merchant)
        assertEquals("工商银行储蓄卡(3679)", result.paymentMethod)
        assertEquals(DefaultData.SHOPPING, result.categoryId)
    }

    @Test
    fun parsesStructuredAiReceiptResponse() {
        val result = ReceiptParser.parseAiResponse(
            responseText = """
                ```json
                {
                  "amount": "7.30",
                  "date": "2026-09-13",
                  "time": "18:40",
                  "merchant": "长沙市望城区学森购物超市",
                  "category": "购物",
                  "payment_method": "工商银行储蓄卡(3679)",
                  "confidence": "high",
                  "raw_text": "支付成功"
                }
                ```
            """.trimIndent(),
            sourceName = "千问 AI",
            today = today,
            nowTime = now
        )

        assertEquals(730L, result.amountInCents)
        assertEquals(ReceiptFieldConfidence.HIGH, result.amountConfidence)
        assertEquals("2026-09-13", result.date)
        assertEquals("18:40", result.time)
        assertEquals("长沙市望城区学森购物超市", result.merchant)
        assertEquals(DefaultData.SHOPPING, result.categoryId)
        assertEquals("千问 AI", result.recognitionSource)
    }

    @Test
    fun aiMissingFieldsRemainReviewable() {
        val result = ReceiptParser.parseAiResponse(
            responseText = """{"amount":null,"date":null,"time":null,"merchant":null,"category":"其他","payment_method":null,"confidence":"missing","raw_text":""}""",
            sourceName = "自定义 AI",
            today = today,
            nowTime = now
        )

        assertNull(result.amountInCents)
        assertTrue(result.amountNeedsReview)
        assertEquals(today.toString(), result.date)
        assertEquals("14:30", result.time)
        assertTrue(result.warnings.any { it.contains("手动填写") })
    }
}
