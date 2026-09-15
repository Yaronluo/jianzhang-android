package com.jianzhang.app.domain.ai

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AiReceiptClientTest {
    @Test
    fun appendsChatCompletionsToCompatibleBaseUrl() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            AiReceiptClient.completionUrl("https://api.example.com/v1/")
        )
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            AiReceiptClient.completionUrl("https://api.example.com/v1/chat/completions")
        )
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            AiReceiptClient.completionUrl("https://api.example.com/v1/responses")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnencryptedRemoteEndpoint() {
        AiReceiptClient.completionUrl("http://api.example.com/v1")
    }

    @Test
    fun readsStringAndArrayResponseContent() {
        val stringResponse = JSONObject()
            .put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", "{\"amount\":\"1.00\"}"))))
        val arrayResponse = JSONObject()
            .put(
                "choices",
                JSONArray().put(
                    JSONObject().put(
                        "message",
                        JSONObject().put(
                            "content",
                            JSONArray().put(JSONObject().put("type", "text").put("text", "{\"amount\":\"2.00\"}"))
                        )
                    )
                )
            )

        assertEquals("{\"amount\":\"1.00\"}", AiReceiptClient.extractContent(stringResponse))
        assertEquals("{\"amount\":\"2.00\"}", AiReceiptClient.extractContent(arrayResponse))
    }
}
