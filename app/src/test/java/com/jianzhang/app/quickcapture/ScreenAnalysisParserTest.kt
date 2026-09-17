package com.jianzhang.app.quickcapture

import com.jianzhang.app.data.model.DefaultData
import com.jianzhang.app.domain.parser.ReceiptFieldConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenAnalysisParserTest {
    @Test
    fun aiResponse_supportsMultipleIndependentActions() {
        val analysis = ScreenAnalysisParser.parseAiResponse(
            """
            ```json
            {"schemaVersion":1,"summary":"麦当劳订单","actions":[
              {"type":"expense","confidence":0.97,"data":{"amount":"18.00","merchant":"麦当劳","category":"餐饮","date":"2026-09-16","time":"12:30","paymentMethod":"微信支付"}},
              {"type":"pickup","confidence":0.98,"data":{"merchant":"麦当劳","code":"A037","queueNumber":null,"tableNumber":null,"location":"五一广场店","time":null}}
            ]}
            ```
            """.trimIndent(),
            "AI屏幕识别"
        )

        assertEquals(2, analysis.actions.size)
        assertEquals("18.00", (analysis.actions[0] as ExpenseAction).amount)
        assertEquals("A037", (analysis.actions[1] as PickupAction).code)
        assertNull((analysis.actions[1] as PickupAction).time)
    }

    @Test
    fun unknownTypesAreIgnoredAndEmptyActionsRemainSafe() {
        val analysis = ScreenAnalysisParser.parseAiResponse(
            """{"schemaVersion":1,"summary":null,"actions":[{"type":"unknown","confidence":1,"data":{}}]}""",
            "AI屏幕识别"
        )
        assertTrue(analysis.actions.isEmpty())
    }

    @Test
    fun localParser_detectsExpenseAndPickupTogether() {
        val analysis = LocalSceneParser.parse(
            """
            麦当劳五一广场店
            实付 18.00元
            支付时间 2026-09-16 12:30
            取餐码 A037
            """.trimIndent()
        )

        assertEquals(2, analysis.actions.size)
        assertTrue(analysis.actions.any { it is ExpenseAction })
        val pickup = analysis.actions.filterIsInstance<PickupAction>().single()
        assertEquals("A037", pickup.code)
        assertNull(pickup.time)
        val receipt = analysis.actions.filterIsInstance<ExpenseAction>().single().toParsedReceipt()
        assertEquals(DefaultData.FOOD, receipt.categoryId)
        assertEquals(ReceiptFieldConfidence.HIGH, receipt.categoryConfidence)
    }

    @Test
    fun obviousMerchant_correctsGenericAiCategory() {
        val analysis = ScreenAnalysisParser.parseAiResponse(
            """{"schemaVersion":1,"rawText":"麦当劳 到店自取订单","actions":[{"type":"expense","confidence":0.97,"data":{"amount":"18.00","merchant":"麦当劳五一广场店","category":"其他","date":"2026-09-16","time":"12:30","paymentMethod":"微信支付"}}]}""",
            "AI屏幕识别"
        )

        val receipt = analysis.actions.filterIsInstance<ExpenseAction>().single().toParsedReceipt()
        assertEquals(DefaultData.FOOD, receipt.categoryId)
        assertEquals(ReceiptFieldConfidence.HIGH, receipt.categoryConfidence)
    }

    @Test
    fun pickupLanguage_classifiesExpenseAsFoodWhenOcrMissesMerchantTitle() {
        val analysis = LocalSceneParser.parse(
            """
            到店自取订单
            实付金额 18.00 元
            取餐码 A037
            """.trimIndent()
        )

        val receipt = analysis.actions.filterIsInstance<ExpenseAction>().single().toParsedReceipt()
        assertEquals(DefaultData.FOOD, receipt.categoryId)
        assertEquals(ReceiptFieldConfidence.HIGH, receipt.categoryConfidence)
    }

    @Test
    fun localParser_detectsPackageAndTrainWithoutInventingFields() {
        val packageResult = LocalSceneParser.parse("菜乌驿站\n取件码 2-3-4567\n学校东门 2号架")
        val packageAction = packageResult.actions.filterIsInstance<PackageAction>().single()
        assertEquals("2-3-4567", packageAction.code)
        assertEquals("菜鸟驿站", packageAction.company)
        assertEquals("学校东门 2号架", packageAction.location)

        val travelResult = LocalSceneParser.parse("G1375\n长沙南 → 广州南\n18:32发车\nA12检票口\n05车 08A")
        val travel = travelResult.actions.filterIsInstance<TravelAction>().single()
        assertEquals("train", travel.travelType)
        assertEquals("G1375", travel.number)
        assertEquals("长沙南", travel.from)
        assertEquals("广州南", travel.to)
    }

    @Test
    fun localParser_detectsFlightFieldsFromVisibleText() {
        val result = LocalSceneParser.parse(
            "CZ3123\n长沙黄花 → 上海浦东\n18:20起飞\n开始登机 17:40\nT2 23号登机口 18A"
        )

        val travel = result.actions.filterIsInstance<TravelAction>().single()
        assertEquals("flight", travel.travelType)
        assertEquals("CZ3123", travel.number)
        assertEquals("长沙黄花", travel.from)
        assertEquals("上海浦东", travel.to)
        assertEquals("17:40", travel.boardingTime)
        assertEquals("T2", travel.terminal)
        assertEquals("23号登机口", travel.gate)
        assertEquals("18A", travel.seat)
    }

    @Test
    fun localParser_doesNotCreateSceneFromUnrelatedText() {
        val analysis = LocalSceneParser.parse("今天阳光很好，记得带伞")
        assertTrue(analysis.actions.isEmpty())
    }
}
