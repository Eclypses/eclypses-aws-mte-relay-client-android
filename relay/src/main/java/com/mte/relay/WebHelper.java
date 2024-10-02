// The MIT License (MIT)
//
// Copyright (c) Eclypses, Inc.
//
// All rights reserved.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package com.mte.relay;

import android.content.Context;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.BuildConfig;
import com.android.volley.Header;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class WebHelper {
    private final String CONTENT_TYPE_KEY = "content-type";
    public static WebHelper instance;
    private static Context ctx;
    private RequestQueue requestQueue;

    public static WebHelper getInstance(Context ctx) {
        if (instance == null) {
            instance = new WebHelper(ctx);
        }
        return instance;
    }

    public WebHelper(Context ctx) {
        WebHelper.ctx = ctx;
        requestQueue = getRequestQueue();
    }

    public <T> void addToRequestQueue(Request<T> req) {
        getRequestQueue().add(req);
    }

    public RequestQueue getRequestQueue() {
        if (requestQueue == null) {
            // getApplicationContext() is key, it keeps you from leaking the
            // Activity or BroadcastReceiver if someone passes one in.
            requestQueue = Volley.newRequestQueue(ctx.getApplicationContext());
        }
        return requestQueue;
    }

    public void sendJson(RelayConnectionModel connectionModel, Request origRequest, RWHResponseListener listener) {

        RelayHeaders responseHeaders = new RelayHeaders();
        JsonObjectRequest request = new JsonObjectRequest(
                connectionModel.method.equals("HEAD") ? Request.Method.HEAD : Request.Method.POST,
                connectionModel.url + connectionModel.route,
                connectionModel.jsonPayload,
                response -> listener.onJsonResponse(response,
                        createNewRelayResponseHeaders(responseHeaders)
                ), error -> {
                    processResponseError(error, responseHeaders, listener);
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                String contentType = "application/json; charset=utf-8";
                return processRequestHeaders(connectionModel, origRequest, contentType);
            }

            @Override
            protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
                parseResponseHeaders(response, responseHeaders);
                if (response.data == null || response.data.length == 0) {
                    return Response.success(null, HttpHeaderParser.parseCacheHeaders(response));
                } else {
                    return super.parseNetworkResponse(response);
                }
            }
        };
        addToRequestQueue(request);
    }

    public void sendJsonArray(RelayConnectionModel connectionModel, Request origRequest, RWHResponseListener listener) {
        RelayHeaders responseHeaders = new RelayHeaders();
        JsonArrayRequest request = new JsonArrayRequest(
                connectionModel.method.equals("HEAD") ? Request.Method.HEAD : Request.Method.POST,
                connectionModel.url + connectionModel.route,
                connectionModel.jsonArrayPayload,
                response -> listener.onJsonArrayResponse(response,
                        createNewRelayResponseHeaders(responseHeaders)
                ), error -> {
            processResponseError(error, responseHeaders, listener);
        }) {

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                String contentType = "application/json; charset=utf-8";
                return processRequestHeaders(connectionModel, origRequest, contentType);
            }

            @Override
            protected Response<JSONArray> parseNetworkResponse(NetworkResponse response) {
                parseResponseHeaders(response, responseHeaders);
                if (response.data == null || response.data.length == 0) {
                    return Response.success(null, HttpHeaderParser.parseCacheHeaders(response));
                } else {
                    return super.parseNetworkResponse(response);
                }
            }
        };
        addToRequestQueue(request);
    }

    public void sendBytes(RelayConnectionModel connectionModel, Request origRequest, RWHResponseListener listener) {
        RelayHeaders responseHeaders = new RelayHeaders();
        Request<byte[]> relayRequest = new Request<byte[]>(
                Request.Method.POST,
                connectionModel.url + connectionModel.route,
                error -> {
                    processResponseError(error, responseHeaders, listener);
                }) {
            @Override
            protected void deliverResponse(byte[] responseBytes) {
                listener.onByteArrayResponse(responseBytes, responseHeaders);
            }

            @Override
            public byte[] getBody() {
                return connectionModel.bytesPayload;
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                String contentType = "application/octet-stream";
                return processRequestHeaders(connectionModel, origRequest, contentType);
            }

            @Override
            protected Response<byte[]> parseNetworkResponse(NetworkResponse response) {
                parseResponseHeaders(response, responseHeaders);
                if (response.data == null || response.data.length == 0) {
                    return Response.success(null, HttpHeaderParser.parseCacheHeaders(response));
                } else {
                    return Response.success(response.data, null);
                }
            }
        };
        addToRequestQueue(relayRequest);
    }

    private void processResponseError(VolleyError error, RelayHeaders responseHeaders, RWHResponseListener listener) {
        if (error == null) {
           return;
        }
        if (error.networkResponse != null && error.networkResponse.data != null) {
            parseResponseHeaders(error.networkResponse, responseHeaders);
            listener.onError(error.networkResponse.statusCode,
                    error.networkResponse.data,
                    responseHeaders);
        } else if (error.networkResponse != null) {
            parseResponseHeaders(error.networkResponse, responseHeaders);
            listener.onError(error.networkResponse.statusCode,
                    new byte[0],
                    responseHeaders);
        } else {
            listener.onError(503,
                    null,
                    new RelayHeaders());
        }
    }

    private Map<String, String> processRequestHeaders(RelayConnectionModel connectionModel,
                                                      Request origRequest,
                                                      String contentType) throws AuthFailureError {
        Map<String, String> params = new HashMap<>();
        params.put("content-type", contentType);
        params.put("x-mte-relay", RelayOptions.formatMteRelayHeader(connectionModel.relayOptions));
        params.put("x-mte-relay-eh", connectionModel.relayHeaders.encryptedDecryptedHeaders);
        // Add the rest of the headers from the original request if it's not null
        if (origRequest != null) {
            Map<String, String> headers = origRequest.getHeaders();
            for (Map.Entry<String, String> header : headers.entrySet())
                if (!Objects.equals(header.getKey(), CONTENT_TYPE_KEY)) {
                    params.put(header.getKey(), header.getValue());
                }
        }
        return params;
    }

    private static void parseResponseHeaders(NetworkResponse response,
                                             RelayHeaders responseHeaders) {
        if (response == null || response.allHeaders == null) {
            return;
        }
        for (Header header : response.allHeaders) {
            if (header.getName().equals("x-mte-relay")) {
                RelayOptions relayOptions = RelayOptions.parseMteRelayHeader(header.getValue());
                if (relayOptions == null) {
                    continue;
                }
                responseHeaders.clientId = relayOptions.clientId;
                responseHeaders.pairId = relayOptions.pairId;
                responseHeaders.encoderType = relayOptions.encodeType;
                continue;
            }
            if (header.getName().equals("x-mte-relay-eh")) {
                responseHeaders.encryptedDecryptedHeaders = header.getValue();
            }
        }
        responseHeaders.responseHeaderList = response.allHeaders;
    }

    private static RelayHeaders createNewRelayResponseHeaders(RelayHeaders responseHeaders) {
        return new RelayHeaders(
                responseHeaders.clientId,
                responseHeaders.pairId,
                responseHeaders.encoderType,
                responseHeaders.encryptedDecryptedHeaders,
                responseHeaders.responseHeaderList);
    }

}
