package com.mte.relay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkHeaderHelperBridgeTest {

    @Test
    fun processRequestHeaders_encodesSelectedHeaders_andMutatesOriginalMapDeterministically() {
        val fakeMteHelper = FakeMteHelper()
        val originalHeaders = LinkedHashMap(TestFixtures.BASE_HEADERS)

        val result = NetworkHeaderHelper.processRequestHeaders(
            fakeMteHelper,
            "pair-req-1",
            TestFixtures.HEADERS_TO_ENCODE,
            originalHeaders
        )

        assertEquals(1, fakeMteHelper.encodeInvocations.size)
        assertEquals("pair-req-1", fakeMteHelper.encodeInvocations.single().pairId)

        assertFalse(originalHeaders.containsKey("Authorization"))
        assertFalse(originalHeaders.containsKey("X-Secret"))
        assertFalse(originalHeaders.containsKey("content-type"))
        assertEquals("visible", originalHeaders["X-Passthrough"])

        assertEquals("pair-req-1", result.pairId)
        assertEquals(TestFixtures.ENCODED_HEADERS_FIXTURE, result.encodedStr)
    }

    @Test
    fun processResponseHeaders_cleansRelayHeaders_whenEhHeaderIsMissing() {
        val fakeMteHelper = FakeMteHelper()

        val responseHeaders = linkedMapOf(
            Constants.X_MTE_RELAY_KEY to mutableListOf("relay-value"),
            Constants.X_MTE_RELAY_EH_KEY to mutableListOf("encrypted-headers"),
            "Access-Control-Allow-Headers" to mutableListOf("x-mte-relay, x-mte-relay-eh, x-custom")
        )

        NetworkHeaderHelper.processResponseHeaders(
            fakeMteHelper,
            "pair-res-1",
            responseHeaders,
            ""
        )

        assertEquals(0, fakeMteHelper.decodeInvocations.size)

        assertFalse(responseHeaders.containsKey(Constants.X_MTE_RELAY_KEY))
        assertFalse(responseHeaders.containsKey(Constants.X_MTE_RELAY_EH_KEY))
        assertEquals(listOf("x-custom"), responseHeaders["Access-Control-Allow-Headers"])
    }

    @Test
    fun processResponseHeaders_withNoEhHeader_doesNotDecode() {
        val fakeMteHelper = FakeMteHelper()

        val responseHeaders = linkedMapOf(
            Constants.X_MTE_RELAY_KEY to mutableListOf("relay-value"),
            Constants.X_MTE_RELAY_EH_KEY to mutableListOf("encrypted-headers")
        )

        NetworkHeaderHelper.processResponseHeaders(
            fakeMteHelper,
            "pair-res-2",
            responseHeaders,
            null
        )

        assertEquals(0, fakeMteHelper.decodeInvocations.size)
    }

    @Test
    fun fakeMteHelper_supportsResetAndDisposeLifecycle() {
        val fakeMteHelper = FakeMteHelper()
        fakeMteHelper.queueDecodeEvent("{\"k\":\"v\"}")
        fakeMteHelper.dispose()

        assertTrue(fakeMteHelper.disposed)
        assertTrue(fakeMteHelper.orderedHistory.contains("lifecycle:dispose"))

        fakeMteHelper.reset()

        assertEquals(0, fakeMteHelper.encodeInvocations.size)
        assertEquals(0, fakeMteHelper.decodeInvocations.size)
        assertFalse(fakeMteHelper.disposed)
    }
}
