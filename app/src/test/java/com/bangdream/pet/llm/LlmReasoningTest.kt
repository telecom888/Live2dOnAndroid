package com.bangdream.pet.llm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmReasoningTest {
    @Test
    fun prefersCanonicalFieldWithoutDuplicatingAliases() {
        val delta = JSONObject("""{"reasoning_content":"primary","reasoning":"duplicate","reasoning_details":[{"type":"reasoning.text","text":"duplicate"}]}""")
        assertEquals("primary", extractReasoningText(delta))
    }

    @Test
    fun fallsBackFromEmptyNullAndUnsupportedFields() {
        for (canonical in listOf("null", "\"\"", "42", "{}")) {
            val delta = JSONObject("""{"reasoning_content":$canonical,"reasoning":"fallback"}""")
            assertEquals("fallback", extractReasoningText(delta))
        }
        assertEquals("details", extractReasoningText(JSONObject("""{"reasoning":"","reasoning_details":[{"type":"reasoning.text","text":"details"}]}""")))
    }

    @Test
    fun acceptsTextBlocksAndSummariesWithoutChangingFragmentWhitespace() {
        val delta = JSONObject("""{"reasoning_details":[{"type":"reasoning.text","text":"first "},{"type":"reasoning.summary","summary":"second"},{"text":"\nthird"}]}""")
        assertEquals("first second\nthird", extractReasoningText(delta))
        assertEquals(" ", extractReasoningText(JSONObject("""{"reasoning":" "}""")))
        assertEquals("object", extractReasoningText(JSONObject("""{"reasoning":{"text":"object"}}""")))
        assertEquals("ab", extractReasoningText(JSONObject("""{"reasoning":["a",{"type":"text","text":"b"}]}""")))
    }

    @Test
    fun neverDisplaysEncryptedBlocksSignaturesOrMetadata() {
        val delta = JSONObject("""{"reasoning_content":null,"reasoning_details":[{"type":"reasoning.encrypted","data":"opaque","text":"not displayable"},{"type":"signature","text":"signature"},{"id":"id","index":0,"signature":"secret"},true,42,null]}""")
        assertNull(extractReasoningText(delta))
        assertNull(extractReasoningText(JSONObject("""{"content":"reply","usage":{"reasoning_tokens":20}}""")))
    }
}
