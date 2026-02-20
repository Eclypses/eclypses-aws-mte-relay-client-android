package com.mte.relay

import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OkHttpToVolleyConverterFacadeTest {

    @Test
    fun convert_jsonObjectRequest_mapsMethodUrlAndHeaders() {
        val okHttpRequest = okhttp3.Request.Builder()
            .url(TestFixtures.RELAY_ENDPOINT)
            .post("{\"name\":\"relay\"}".toRequestBody("application/json; charset=utf-8".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer z")
            .build()

        val converted = OkHttpToVolleyConverter.convert<Any>(okHttpRequest)

        assertInstanceOf(JsonObjectRequest::class.java, converted)
        assertEquals(Request.Method.POST, converted.method)
        assertEquals(TestFixtures.RELAY_ENDPOINT, converted.url)
        assertEquals("Bearer z", converted.headers["Authorization"])
    }

    @Test
    fun convert_jsonArrayRequest_detectsArrayBody() {
        val okHttpRequest = okhttp3.Request.Builder()
            .url(TestFixtures.RELAY_ENDPOINT)
            .put("[1,2,3]".toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .build()

        val converted = OkHttpToVolleyConverter.convert<Any>(okHttpRequest)

        assertInstanceOf(JsonArrayRequest::class.java, converted)
        assertEquals(Request.Method.PUT, converted.method)
    }

    @Test
    fun convert_plainTextBody_returnsStringRequestWithBodyBytes() {
        val body = "plain-body"
        val okHttpRequest = okhttp3.Request.Builder()
            .url(TestFixtures.RELAY_ENDPOINT)
            .method("PATCH", body.toRequestBody("text/plain".toMediaType()))
            .build()

        val converted = OkHttpToVolleyConverter.convert<Any>(okHttpRequest)

        val stringRequest = assertInstanceOf(StringRequest::class.java, converted)
        assertEquals(Request.Method.PATCH, stringRequest.method)
        assertArrayEquals(body.toByteArray(Charsets.UTF_8), stringRequest.body)
    }

    @Test
    fun convert_unsupportedMethod_throwsIllegalArgumentException() {
        val okHttpRequest = okhttp3.Request.Builder()
            .url(TestFixtures.RELAY_ENDPOINT)
            .method("PROPFIND", null)
            .build()

        assertThrows(IllegalArgumentException::class.java) {
            OkHttpToVolleyConverter.convert<Any>(okHttpRequest)
        }
    }

    @Test
    fun convertVolleyToOkHttpResponse_buildsResponseWithHeadersAndMediaType() {
        val originalRequest = okhttp3.Request.Builder()
            .url(TestFixtures.RELAY_ENDPOINT)
            .get()
            .build()

        val volleyResponse = NetworkResponse(
            202,
            "{\"status\":\"ok\"}".toByteArray(Charsets.UTF_8),
            mapOf("Content-Type" to "application/json"),
            false
        )

        val converted = OkHttpToVolleyConverter.convertVolleyToOkHttpResponse(
            volleyResponse,
            "{\"status\":\"ok\"}".toByteArray(Charsets.UTF_8),
            mapOf("x-trace" to listOf("abc123")),
            originalRequest,
            "application/json"
        )

        assertEquals(202, converted.code)
        assertEquals("abc123", converted.header("x-trace"))
        assertEquals("application/json; charset=utf-8", converted.body?.contentType().toString())
    }

    @Test
    fun convertErrorToOkHttpResponse_appliesSafeDefaults() {
        val originalRequest = okhttp3.Request.Builder()
            .url(TestFixtures.RELAY_ENDPOINT)
            .get()
            .build()

        val converted = OkHttpToVolleyConverter.convertErrorToOkHttpResponse(
            originalRequest,
            "No Response Received",
            0,
            null
        )

        assertEquals(500, converted.code)
        assertTrue((converted.body?.contentType().toString()).contains("charset=utf-8"))
        assertEquals("No Response Received", converted.body?.string())
    }
}
