package com.mte.relay

object TestFixtures {
    const val RELAY_ENDPOINT = "https://relay.example.test/api/v1/messages"
    const val ROUTE = "/api/v1/messages"

    val HEADERS_TO_ENCODE = arrayOf("Authorization", "X-Secret")

    val BASE_HEADERS = linkedMapOf(
        "Authorization" to "Bearer test-token",
        "X-Secret" to "secret-value",
        "content-type" to "application/json",
        "X-Passthrough" to "visible"
    )

    const val SMALL_TEXT = "hello-relay"
    val LARGE_TEXT = "x".repeat(256 * 1024)
    val EMPTY_BYTES = ByteArray(0)
    val SMALL_BYTES = "binary-small".toByteArray(Charsets.UTF_8)
    val LARGE_BYTES = ByteArray(512 * 1024) { (it % 251).toByte() }

    const val ENCODED_HEADERS_FIXTURE = "encoded-headers"
    const val DECODED_HEADERS_JSON = "{\"server-header\":\"ok\",\"trace-id\":\"t-123\"}"

    const val ERROR_JSON = "{\"error\":\"relay-failure\"}"
}