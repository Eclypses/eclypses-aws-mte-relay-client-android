package com.mte.relay

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThroughputEdgeScenariosTest {

    @Test
    fun burstMixedTextBinaryAndEdgePayloads_remainsDeterministic() = runTest {
        val fakeMteHelper = FakeMteHelper()

        val burstCount = 120
        val jobs = (0 until burstCount).map { index ->
            async {
                val requestHeaders = LinkedHashMap(TestFixtures.BASE_HEADERS)
                val pairId = "pair-$index"

                val textPayload = when {
                    index % 5 == 0 -> ""
                    index % 2 == 0 -> TestFixtures.SMALL_TEXT
                    else -> TestFixtures.LARGE_TEXT.take(1024)
                }
                val binaryPayload = when {
                    index % 7 == 0 -> TestFixtures.EMPTY_BYTES
                    index % 2 == 0 -> TestFixtures.SMALL_BYTES
                    else -> TestFixtures.LARGE_BYTES.copyOf(2048)
                }

                fakeMteHelper.nextEncodePairId = pairId
                fakeMteHelper.nextEncodedStr = "enc-$index-${textPayload.length}-${binaryPayload.size}"
                val encodedHeaders = NetworkHeaderHelper.processRequestHeaders(
                    fakeMteHelper,
                    pairId,
                    TestFixtures.HEADERS_TO_ENCODE,
                    requestHeaders
                )

                val responseHeaders = linkedMapOf(
                    Constants.X_MTE_RELAY_KEY to mutableListOf("relay"),
                    Constants.X_MTE_RELAY_EH_KEY to mutableListOf("enc")
                )

                NetworkHeaderHelper.processResponseHeaders(
                    fakeMteHelper,
                    pairId,
                    responseHeaders,
                    ""
                )

                assertEquals(pairId, encodedHeaders.pairId)
                assertTrue(requestHeaders.containsKey("X-Passthrough"))
                assertEquals(null, responseHeaders[Constants.X_MTE_RELAY_KEY])
                assertEquals(null, responseHeaders[Constants.X_MTE_RELAY_EH_KEY])
            }
        }

        jobs.awaitAll()

        assertEquals(burstCount, fakeMteHelper.encodeInvocations.size)
        assertEquals(0, fakeMteHelper.decodeInvocations.size)
    }
}
