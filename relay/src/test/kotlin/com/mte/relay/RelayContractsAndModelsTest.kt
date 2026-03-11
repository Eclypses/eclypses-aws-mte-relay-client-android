package com.mte.relay

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RelayContractsAndModelsTest {

    @Test
    fun relayHeader_roundTrip_forMteOptions_isStable() {
        val original = RelayOptions(
            "client-1",
            "pair-1",
            "MTE",
            true,
            false,
            true,
            false
        )

        val header = RelayOptions.formatMteRelayHeader(original)
        val parsed = RelayOptions.parseMteRelayHeader(header)

        assertNotNull(parsed)
        assertEquals("client-1", parsed?.clientId)
        assertEquals("pair-1", parsed?.pairId)
        assertEquals("MTE", parsed?.encodeType)
        assertTrue(parsed?.urlIsEncoded == true)
        assertFalse(parsed?.headersAreEncoded == true)
        assertTrue(parsed?.bodyIsEncoded == true)
        assertFalse(parsed?.preventStreaming == true)
    }

    @Test
    fun relayHeader_roundTrip_forMkeOptions_isStable() {
        val original = RelayOptions(
            "client-2",
            "pair-2",
            "MKE",
            false,
            true,
            false,
            false
        )

        val parsed = RelayOptions.parseMteRelayHeader(RelayOptions.formatMteRelayHeader(original))

        assertNotNull(parsed)
        assertEquals("MKE", parsed?.encodeType)
        assertFalse(parsed?.urlIsEncoded == true)
        assertTrue(parsed?.headersAreEncoded == true)
        assertFalse(parsed?.bodyIsEncoded == true)
        assertFalse(parsed?.preventStreaming == true)
    }

    @Test
    fun relayHeader_parse_withOnlyClientId_usesFallbackDefaults() {
        val parsed = RelayOptions.parseMteRelayHeader("client-only")

        assertNotNull(parsed)
        assertEquals("client-only", parsed?.clientId)
        assertEquals("", parsed?.pairId)
        assertEquals("", parsed?.encodeType)
        assertFalse(parsed?.urlIsEncoded == true)
        assertFalse(parsed?.preventStreaming == true)
    }

    @Test
    fun relayHeader_parse_nullHeader_returnsNull() {
        val parsed = RelayOptions.parseMteRelayHeader("")

        assertNotNull(parsed)
        assertEquals("", parsed?.clientId)
    }

    @Test
    fun relayHeader_formatForRoute_pairingEndpoints_useClientIdOnly() {
        val options = RelayOptions(
            "client-3",
            "pair-3",
            "MKE",
            true,
            true,
            true,
            false
        )

        val relayCheckHeader = RelayOptions.formatMteRelayHeaderForRoute(options, "/api/mte-relay")
        val pairingHeader = RelayOptions.formatMteRelayHeaderForRoute(options, "/api/mte-pair")

        assertEquals("client-3", relayCheckHeader)
        assertEquals("client-3", pairingHeader)
    }

    @Test
    fun relayHeader_formatForRoute_nonPairingEndpoint_usesLegacyCsv() {
        val options = RelayOptions(
            "client-4",
            "pair-4",
            "MKE",
            true,
            false,
            true,
            false
        )

        val header = RelayOptions.formatMteRelayHeaderForRoute(options, "/api/customer-endpoint")

        assertEquals("client-4,pair-4,1,1,0,1,0", header)
    }

    @Test
    fun relayHeader_roundTrip_preventStreamingFlag_isSupported() {
        val original = RelayOptions(
            "client-ps",
            "pair-ps",
            "MKE",
            true,
            true,
            true,
            true
        )

        val header = RelayOptions.formatMteRelayHeader(original)
        val parsed = RelayOptions.parseMteRelayHeader(header)

        assertEquals("client-ps,pair-ps,1,1,1,1,1", header)
        assertTrue(parsed?.preventStreaming == true)
    }

    @Test
    fun relayConnectionModel_holdsPayloadAndHeadersReferences() {
        val jsonPayload = JSONObject("{\"hello\":\"world\"}")
        val jsonArrayPayload = JSONArray("[1,2,3]")
        val bytesPayload = TestFixtures.SMALL_BYTES
        val headers = linkedMapOf("Authorization" to "Bearer abc")
        val relayHeaders = RelayHeaders("client", "pair", "MTE", "enc", emptyList())
        val relayOptions = RelayOptions("client", "pair", "MTE", true, true, true, false)

        val model = RelayConnectionModel(
            TestFixtures.RELAY_ENDPOINT,
            1,
            TestFixtures.ROUTE,
            jsonPayload,
            jsonArrayPayload,
            bytesPayload,
            headers,
            relayHeaders,
            relayOptions
        )

        assertEquals(TestFixtures.RELAY_ENDPOINT, model.url)
        assertEquals(TestFixtures.ROUTE, model.route)
        assertEquals(jsonPayload.toString(), model.jsonPayload.toString())
        assertEquals(jsonArrayPayload.toString(), model.jsonArrayPayload.toString())
        assertTrue(model.bytesPayload.contentEquals(bytesPayload))
        assertEquals("Bearer abc", model.origHeaders["Authorization"])
        assertEquals("pair", model.relayOptions.pairId)
    }

    @Test
    fun networkUtils_rePairContract_matchesCodeRangeAndRequestPresence() {
        val retryable = RetryableRequestData { }

        assertTrue(NetworkUtils.shouldRePairWithHost(559, retryable))
        assertTrue(NetworkUtils.shouldRePairWithHost(569, retryable))
        assertFalse(NetworkUtils.shouldRePairWithHost(558, retryable))
        assertFalse(NetworkUtils.shouldRePairWithHost(570, retryable))
        assertFalse(NetworkUtils.shouldRePairWithHost(560, null))
    }
}
