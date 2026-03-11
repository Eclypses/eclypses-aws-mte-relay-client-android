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

import com.android.volley.DefaultRetryPolicy;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class WebHelper {

    // region Class Variables
    private static WebHelper instance;
    private static Context appContext;
    private RequestQueue requestQueue;
    // endregion

    // region Constructors
    public static WebHelper getInstance(Context context) {
        if (instance == null) {
            instance = new WebHelper(context.getApplicationContext());
        }
        return instance;
    }

    // Private constructor to prevent direct instantiation
    private WebHelper(Context context) {
        appContext = context;
        requestQueue = getRequestQueue();
    }
    // endregion

    // region Public Methods
    public <T> void addToRequestQueue(Request<T> req) {
        getRequestQueue().add(req);
    }

    public RequestQueue getRequestQueue() {
        if (requestQueue == null) {
            requestQueue = Volley.newRequestQueue(appContext);
        }
        return requestQueue;
    }

    public <T> void sendJson(RelayConnectionModel connectionModel, Request<T> origRequest, NetworkResponseListener listener) {
        final NetworkResponse[] networkResponse = {null};
        RelayHeaders responseHeaders = new RelayHeaders();
        JsonObjectRequest request = new JsonObjectRequest(
                connectionModel.method,
                connectionModel.url + connectionModel.route,
                connectionModel.jsonPayload,
                response -> listener.onJsonResponse(networkResponse[0], response,
                        createNewRelayResponseHeaders(responseHeaders)
                ), error -> processResponseError(error, responseHeaders, listener)) {
            @Override
            public Map<String, String> getHeaders() {
                String contentType = "application/json; charset=utf-8";
                return processRequestHeaders(connectionModel, origRequest, contentType);
            }

            @Override
            protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
                networkResponse[0] = response;
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

    public <T> void sendJsonArray(RelayConnectionModel connectionModel, Request<T> origRequest, NetworkResponseListener listener) {
        final NetworkResponse[] networkResponse = {null};
        RelayHeaders responseHeaders = new RelayHeaders();
        JsonArrayRequest request = new JsonArrayRequest(
                connectionModel.method,
                connectionModel.url + connectionModel.route,
                connectionModel.jsonArrayPayload,
                response -> listener.onJsonArrayResponse(networkResponse[0], response,
                        createNewRelayResponseHeaders(responseHeaders)
                ), error -> processResponseError(error, responseHeaders, listener)) {

            @Override
            public Map<String, String> getHeaders() {
                String contentType = "application/json; charset=utf-8";
                return processRequestHeaders(connectionModel, origRequest, contentType);
            }

            @Override
            protected Response<JSONArray> parseNetworkResponse(NetworkResponse response) {
                networkResponse[0] = response;
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

    public <T> void sendBytes(RelayConnectionModel connectionModel, Request<T> origRequest, NetworkResponseListener listener) {
        final NetworkResponse[] networkResponse = {null};
        RelayHeaders responseHeaders = new RelayHeaders();
        Request<byte[]> relayRequest = new Request<byte[]>(
                connectionModel.method,
                connectionModel.url + connectionModel.route,
                error -> processResponseError(error, responseHeaders, listener)) {
            @Override
            protected void deliverResponse(byte[] responseBytes) {
                listener.onByteArrayResponse(networkResponse[0], responseBytes, responseHeaders);
            }

            @Override
            public byte[] getBody() {
                return connectionModel.bytesPayload;
            }

            @Override
            public Map<String, String> getHeaders() {
                String contentType = "application/octet-stream";
                return processRequestHeaders(connectionModel, origRequest, contentType);
            }

            @Override
            protected Response<byte[]> parseNetworkResponse(NetworkResponse response) {
                networkResponse[0] = response;
                parseResponseHeaders(response, responseHeaders);
                if (response.data == null || response.data.length == 0) {
                    return Response.success(null, HttpHeaderParser.parseCacheHeaders(response));
                } else {
                    return Response.success(response.data, null);
                }
            }
        };
        relayRequest.setRetryPolicy(new DefaultRetryPolicy(
                DefaultRetryPolicy.DEFAULT_TIMEOUT_MS, //After the set time elapses the request will timeout
                0,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        addToRequestQueue(relayRequest);
    }
    // endregion

    // region Private Methods
    private void processResponseError(VolleyError error, RelayHeaders responseHeaders, NetworkResponseListener listener) {
        if (error == null) {
            listener.onError(null,
                    null,
                    new RelayHeaders());
           return;
        }
        if (error.networkResponse != null && error.networkResponse.data != null) {
            parseResponseHeaders(error.networkResponse, responseHeaders);
            listener.onError(error.networkResponse,
                    error.networkResponse.data,
                    responseHeaders);
        } else if (error.networkResponse != null) {
            parseResponseHeaders(error.networkResponse, responseHeaders);
            listener.onError(error.networkResponse,
                    new byte[0],
                    responseHeaders);
        } else {
            String errorMessage;
            if (error.getCause() != null) {
                errorMessage = error.getCause().toString();
            } else if (error.getMessage() != null) {
                errorMessage = error.getMessage();
            } else {
                errorMessage = error.getClass().getSimpleName();
            }

            byte[] errorBytes = errorMessage.getBytes(StandardCharsets.UTF_8);
            listener.onError(null,
                    errorBytes,
                    new RelayHeaders());
        }
    }

    private <T> Map<String, String> processRequestHeaders(RelayConnectionModel connectionModel,
                                                      Request<T> origRequest,
                                                      String contentType) {
        Map<String, String> params = new HashMap<>();
        params.put(Constants.CONTENT_TYPE_KEY, contentType);
        params.put(Constants.X_MTE_RELAY_KEY,
            RelayOptions.formatMteRelayHeaderForRoute(connectionModel.relayOptions, connectionModel.route));
        params.put(Constants.X_MTE_RELAY_EH_KEY, connectionModel.relayHeaders.encryptedDecryptedHeaders);

        // Add the rest of the headers from the original request if it's not null
        if (origRequest != null) {
            for (Map.Entry<String, String> header : connectionModel.origHeaders.entrySet())
                if (!Objects.equals(header.getKey(), Constants.CONTENT_TYPE_KEY)) {
                    params.put(header.getKey(), header.getValue());
                }
        }
        LogHelper.debug("WebHelper", params.toString());
        return params;
    }

    private static void parseResponseHeaders(NetworkResponse response,
                                             RelayHeaders responseHeaders) {
        if (response == null || response.allHeaders == null) {
            return;
        }

        List<Header> filteredHeaders = new ArrayList<>();

        for (Header header : response.allHeaders) {
            String headerName = header.getName();
            String headerValue = header.getValue();
            if (headerName != null && headerName.equalsIgnoreCase(Constants.X_MTE_RELAY_KEY)) {
                RelayOptions relayOptions = RelayOptions.parseMteRelayHeader(headerValue == null ? "" : headerValue.trim());
                if (relayOptions != null) {
                    responseHeaders.clientId = relayOptions.clientId;
                    responseHeaders.pairId = relayOptions.pairId;
                    responseHeaders.encoderType = relayOptions.encodeType;
                }
            } else if (headerName != null && headerName.equalsIgnoreCase(Constants.X_MTE_RELAY_EH_KEY)) {
                responseHeaders.encryptedDecryptedHeaders = headerValue;
            } else {
                // Only add headers we don’t consume
                filteredHeaders.add(header);
            }
        }

        responseHeaders.responseHeaderList = filteredHeaders;
    }

    private static RelayHeaders createNewRelayResponseHeaders(RelayHeaders responseHeaders) {
        return new RelayHeaders(
                responseHeaders.clientId,
                responseHeaders.pairId,
                responseHeaders.encoderType,
                responseHeaders.encryptedDecryptedHeaders,
                responseHeaders.responseHeaderList);
    }
    // endregion
}
