package com.bangdream.pet.llm

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmErrorsTest {
    @Test
    fun redactsApiKeyAndCustomHeaderBeforeTruncation() {
        val settings = LlmSettings(apiKey = "api-secret", headers = listOf(LlmHeader("X-Token", "header-secret")))
        val error = providerFailure("api-secret header-secret " + "x".repeat(4000), settings, 401)
        assertFalse(error.message!!.contains("api-secret"))
        assertFalse(error.message!!.contains("header-secret"))
        assertTrue(error.message!!.startsWith("HTTP 401: *** ***"))
        assertTrue(error.message!!.length <= 2048)
    }

    @Test
    fun networkFailuresHaveStableUiKeys() {
        assertEquals("LLM_TIMEOUT", llmFailureMessage(SocketTimeoutException(), LlmSettings()))
        assertEquals("LLM_HOST_UNREACHABLE", llmFailureMessage(UnknownHostException(), LlmSettings()))
    }
}
