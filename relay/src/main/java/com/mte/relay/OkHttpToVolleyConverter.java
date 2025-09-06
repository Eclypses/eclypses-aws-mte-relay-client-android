package com.mte.relay;

import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;

import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.ResponseBody;
import okio.Buffer;
import okhttp3.RequestBody;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Convert an okhttp3.Request into a Volley Request<T> (JSONObject, JSONArray, or String).
 */
public final class OkHttpToVolleyConverter {

    @SuppressWarnings("unchecked")
    public static <T> com.android.volley.Request<T> convert(okhttp3.Request okHttpRequest) throws IOException {

        String bodyString = null;
        RequestBody requestBody = okHttpRequest.body();
        if (requestBody != null) {
            Buffer buffer = new Buffer();
            requestBody.writeTo(buffer);
            bodyString = buffer.readUtf8();
        }

        final Map<String, String> headers = new HashMap<>();
        for (String name : okHttpRequest.headers().names()) {
            String value = okHttpRequest.header(name);
            if (value != null) headers.put(name, value);
        }

        String contentType = okHttpRequest.header("Content-Type"); // case-insensitive lookup
        if (contentType != null) {
            contentType = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        }

        final int volleyMethod = mapMethod(okHttpRequest.method());

        if (contentType != null && contentType.contains("application/json")) {

            if (bodyString != null && bodyString.trim().startsWith("[")) {
                final JSONArray jsonArray;
                try {
                    jsonArray = new JSONArray(bodyString);
                } catch (Exception e) {
                    throw new IOException("Invalid JSON array body", e);
                }

                JsonArrayRequest arrReq = new JsonArrayRequest(
                        volleyMethod,
                        okHttpRequest.url().toString(),
                        jsonArray,
                        null,
                        null
                ) {
                    @Override
                    public Map<String, String> getHeaders() {
                        return headers;
                    }
                };

                return (com.android.volley.Request<T>) arrReq;
            } else {
                final JSONObject jsonObject;
                try {
                    jsonObject = (bodyString != null && !bodyString.isEmpty()) ? new JSONObject(bodyString) : null;
                } catch (Exception e) {
                    throw new IOException("Invalid JSON object body", e);
                }

                JsonObjectRequest objReq = new JsonObjectRequest(
                        volleyMethod,
                        okHttpRequest.url().toString(),
                        jsonObject,
                        null,
                        null
                ) {
                    @Override
                    public Map<String, String> getHeaders() {
                        return headers;
                    }
                };
                return (com.android.volley.Request<T>) objReq;
            }
        } else {
            final byte[] finalBodyBytes = (bodyString != null) ? bodyString.getBytes(StandardCharsets.UTF_8) : null;

            StringRequest strReq = new StringRequest(
                    volleyMethod,
                    okHttpRequest.url().toString(),
                    null,
                    null
            ) {
                @Override
                public Map<String, String> getHeaders() {
                    return headers;
                }

                @Override
                public byte[] getBody() throws AuthFailureError {
                    if (finalBodyBytes != null) return finalBodyBytes;
                    return super.getBody();
                }
            };
            return (com.android.volley.Request<T>) strReq;
        }
    }

    private static int mapMethod(String method) {
        switch (method.toUpperCase(Locale.ROOT)) {
            case "GET": return Request.Method.GET;
            case "POST": return Request.Method.POST;
            case "PUT": return Request.Method.PUT;
            case "DELETE": return Request.Method.DELETE;
            case "HEAD": return Request.Method.HEAD;
            case "OPTIONS": return Request.Method.OPTIONS;
            case "PATCH": return Request.Method.PATCH;
            case "TRACE": return Request.Method.TRACE;
            default: throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
    }

    public static okhttp3.Response convertVolleyToOkHttpResponse(
            NetworkResponse volleyResponse,
            byte[] responseBytes,
            Map<String, List<String>> responseHeaders,
            okhttp3.Request originalRequest,
            String bodyType
    ) {
        if (volleyResponse == null) {
            return convertErrorToOkHttpResponse(originalRequest, "No Response Received", 0, null);
        }

        // Determine media type
        MediaType mediaType = null;
        if (volleyResponse.headers != null) {
            String contentType = volleyResponse.headers.get("Content-Type");
            if (contentType != null) {
                mediaType = MediaType.parse(contentType);
            } else if (bodyType != null) {
                mediaType = MediaType.parse(bodyType);
            }
        } else if (bodyType != null) {
            mediaType = MediaType.parse(bodyType);
        }

        // Default charset safety
        MediaType effectiveMediaType = mediaType;
        if (effectiveMediaType == null) {
            effectiveMediaType = MediaType.parse("application/json; charset=utf-8");
        } else if (effectiveMediaType.charset() == null) {
            effectiveMediaType = MediaType.parse(effectiveMediaType + "; charset=utf-8");
        }

        // Build headers from responseHeaders (Map<String, List<String>>)
        okhttp3.Headers.Builder headersBuilder = new okhttp3.Headers.Builder();
        if (responseHeaders != null) {
            for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
                String name = entry.getKey();
                for (String value : entry.getValue()) {
                    headersBuilder.add(name, value);
                }
            }
        }

        return new okhttp3.Response.Builder()
                .request(originalRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(volleyResponse.statusCode)
                .message("") // you could optionally map Volley’s status text here if available
                .body(ResponseBody.create(responseBytes, effectiveMediaType))
                .headers(headersBuilder.build())
                .build();
    }

    public static okhttp3.Response convertErrorToOkHttpResponse(
            okhttp3.Request originalRequest,
            String errorMessage,
            int statusCode,
            String bodyType
    ) {
        // Default error code if not supplied
        int safeStatusCode = (statusCode > 0 ? statusCode : 500);

        // Media type (fall back to JSON with utf-8)
        MediaType mediaType = bodyType != null
                ? MediaType.parse(bodyType)
                : MediaType.parse("application/json; charset=utf-8");

        // Ensure charset is present
        if (mediaType != null && mediaType.charset() == null) {
            mediaType = MediaType.parse(mediaType.toString() + "; charset=utf-8");
        }

        // Error body
        byte[] errorBytes = (errorMessage != null
                ? errorMessage.getBytes(StandardCharsets.UTF_8)
                : new byte[0]);

        ResponseBody responseBody = ResponseBody.create(errorBytes, mediaType);

        return new okhttp3.Response.Builder()
                .request(originalRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(safeStatusCode)
                .message(errorMessage != null ? errorMessage : "Unknown error")
                .body(responseBody)
                .headers(new okhttp3.Headers.Builder().build())
                .build();
    }
}