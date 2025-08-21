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
import android.net.Uri;

import com.android.volley.AuthFailureError;
import com.android.volley.Header;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Host {

    // region Class Variables
    boolean hostPaired = false;
    final Context ctx;
    final String hostUrl;
    final String hostUrlB64;
    private HostStorageHelper hostStorageHelper;
    private final MteHelper mteHelper;
    private final WebHelper webHelper;
    private final Object lock = new Object();
    private String hostClientId;
    private PrevRequestData prevRequestData;
    private PrevUploadData prevUploadData;
    private PrevDownloadData prevDownloadData;
    // endregion

    // region Constructors
    public Host(Context ctx, String hostUrl, InstantiateHostCallback callback) {
        this.ctx = ctx;
        this.hostUrl = hostUrl;
        this.hostUrlB64 = Base64.getUrlEncoder().encodeToString(hostUrl.getBytes());
        webHelper = WebHelper.getInstance(ctx);
        mteHelper = new MteHelper();
        Thread storageThread = new Thread(() -> {
            try {
                synchronized (lock) {
                    hostStorageHelper = new HostStorageHelper(ctx, hostUrlB64, new HostStorageHelperCallback() {

                        @Override
                        public void onError(String message) {
                            callback.onError(message);
                        }

                        @Override
                        public void noStoredPairs() {
                            pairWithHost(callback);
                        }

                        @Override
                        public void foundClientId(String clientId) {
                            hostClientId = clientId;
                            pairWithHost(callback);
                        }

                        @Override
                        public void foundStoredPairs(String storedHostsStr) {
                            getStoredStates(callback);
                        }
                    });
                }
            } catch (IOException e) {
                callback.onError(e.getMessage());
            }
        });
        storageThread.start();
    }
    // endregion

    // region Retry Callbacks
    final RetryUploadCallback retryUploadCallback = (code, listener) -> {
        if (code == 200) {
            prevUploadData = null;
        }
        else if (NetworkUtils.shouldRePairWithHost(code, prevUploadData)) {
            rePairWithHost(new InstantiateHostCallback() {
                @Override
                public void onError(String message) {
                  listener.relayStreamResponse(code, false, "", message, null);
                }

                @Override
                public void hostInstantiated(String hostUrl, Host host) {
                    prevUploadData.retry();
                }
            });
        }
    };

    final RetryDownloadCallback retryDownloadCallback = (code, listener) -> {
        if (code == 200) {
            prevDownloadData = null;
        }
        else if (NetworkUtils.shouldRePairWithHost(code, prevDownloadData)) {
            rePairWithHost(new InstantiateHostCallback() {
                @Override
                public void onError(String message) {
                    listener.relayStreamResponse(code, false, "", message, null);
                }

                @Override
                public void hostInstantiated(String hostUrl, Host host) {
                    prevDownloadData.retry();
                }
            });
        }
    };
    // endregion

    // region Public Methods
    public <T> void sendRequest(Request<T> req, String[] headersToEncrypt, RelayVolleyRequestListener listener) {
        Thread sendingTread = new Thread(() -> {
            try {
                sendUpdatedRequest(req, headersToEncrypt, listener);
            } catch (InterruptedException |
                     UnsupportedEncodingException |
                     AuthFailureError |
                     MalformedURLException e) {
                listener.onError(null, e.getMessage(), null);
            }
        });
        sendingTread.start();
    }

    public <T> void sendOkHttpRequest(okhttp3.Request req, String[] headersToEncrypt, RelayOkHttpRequestListener listener) throws IOException {

        Request<T> volleyReq = OkHttpToVolleyConverter.convert(req);
        try {
            sendUpdatedRequest(volleyReq, headersToEncrypt, new RelayVolleyRequestListener() {
                @Override
                public void onError(NetworkResponse networkResponse, String errorMessage, Map<String, List<String>> responseHeaders) {
                    LogHelper.error("Host",errorMessage);
                    okhttp3.Response okHttpResponse = OkHttpToVolleyConverter.convertVolleyToOkHttpResponse(
                            networkResponse,
                            errorMessage.getBytes(StandardCharsets.UTF_8),
                            responseHeaders,
                            req,
                            "application/json"
                    );
                    listener.onError(okHttpResponse);
                }

                @Override
                public void onResponse(NetworkResponse networkResponse, byte[] responseBytes, Map<String, List<String>> responseHeaders) {
                    okhttp3.Response okHttpResponse = OkHttpToVolleyConverter.convertVolleyToOkHttpResponse(
                            networkResponse,
                            responseBytes,
                            responseHeaders,
                            req,
                            "application/json"
                    );
                    listener.onResponse(okHttpResponse);
                }
            });
        } catch (InterruptedException | AuthFailureError | MalformedURLException | UnsupportedEncodingException e) {
            LogHelper.error("Host",e.getMessage());
            okhttp3.Response errorResponse = OkHttpToVolleyConverter.convertErrorToOkHttpResponse(
                    req,
                    e.getMessage(),
                    500, // synthetic status code for internal errors
                    "application/json"
            );
            listener.onError(errorResponse);
        }
    }

    synchronized public void uploadFile(RelayFileRequestProperties reqProperties,
                                        String route,
                                        RelayStreamResponseListener listener,
                                        RelayStreamCompletionCallback completionCallback) {

        prevUploadData = storePrevRequest(prevUploadData,
                new PrevUploadData(
                        this,
                        reqProperties,
                        route,
                        listener,
                        completionCallback));

        while (!hostPaired) {
            try {
                wait();
            } catch (InterruptedException e) {
                listener.relayStreamResponse(
                        -1,
                        false,
                        null,
                        e.getMessage(),
                        null);
            }
        }
        Thread sendingTread = new Thread(() -> {
            try {
                String pairId = mteHelper.getNextPairId();

                // make a COPY of the original headers to prevent modifying the original request.
                Map<String, String> origHeaders = new HashMap<>(reqProperties.origHeaders);

                RelayFileUploadProperties properties = new RelayFileUploadProperties(
                        reqProperties.serverPath,
                        route,
                        mteHelper,
                        reqProperties.headersToEncrypt,
                        origHeaders,
                        setRelayOptions(true, pairId),
                        reqProperties.relayStreamCallback);

                // Encrypt route
                EncodeResult encryptRouteResult = encryptRoute(route);
                properties.route = encryptRouteResult.encodedStr;
                properties.relayOptions.pairId = encryptRouteResult.pairId;

                FileUploadHelper fileUploadHelper = new FileUploadHelper(properties, listener, completionCallback, retryUploadCallback);
                fileUploadHelper.encryptAndSend(() -> {
                    try {
                        conditionallyStoreStates();
                    } catch (JSONException e) {
                        listener.relayStreamResponse(
                                -1,
                                false,
                                null,
                                e.getMessage(),
                                null);
                    }
                });
            } catch (IOException |
                     MteException |
                    RelayException e) {
                listener.relayStreamResponse(
                        -1,
                        false,
                        null,
                        e.getMessage(),
                        null);
            }
        });
        sendingTread.start();
    }

    synchronized public void downloadFile(RelayFileRequestProperties reqProperties, RelayStreamResponseListener listener) throws IOException {

        prevDownloadData = storePrevRequest(prevDownloadData,
                new PrevDownloadData(
                        this,
                        reqProperties,
                        listener));

        while (!hostPaired) {
            try {
                wait();
            } catch (InterruptedException e) {
                listener.relayStreamResponse(
                        -1,
                        false,
                        null,
                        " Exception: " +e.getMessage(),
                        null);
            }
        }

        // Get PairId to do this download
        String pairId = mteHelper.getNextPairId();

        // make a COPY of the original headers to prevent modifying the original request.
        Map<String, String> origHeaders = new HashMap<>(reqProperties.origHeaders);

        FileDownloadProperties properties = new FileDownloadProperties(
                reqProperties.serverPath,
                reqProperties.route,
                reqProperties.downloadPath,
                mteHelper,
                reqProperties.headersToEncrypt,
                origHeaders,
                setRelayOptions(false, pairId));

        // Encrypt route and inject pathnamePrefix if it exists
        EncodeResult encryptRouteResult = encryptRoute(reqProperties.route);
        properties.route = encryptRouteResult.encodedStr;
        properties.relayOptions.pairId = encryptRouteResult.pairId;

        FileDownloadHelper connectionHelper = new FileDownloadHelper(properties, listener, retryDownloadCallback);
        connectionHelper.downloadFile(() -> {
            try {
                conditionallyStoreStates();
            } catch (JSONException e) {
                listener.relayStreamResponse(
                        -1,
                        false,
                        null,
                        e.getMessage(),
                        null);
            }
        });
    }

    synchronized public <T> void sendUpdatedRequest(Request<T> origRequest,
                                                    String[] headersToEncrypt,
                                                    RelayVolleyRequestListener listener)
            throws InterruptedException,
            UnsupportedEncodingException,
            MalformedURLException,
            AuthFailureError {

        prevRequestData = storePrevRequest(prevRequestData, new PrevRequestData(this, origRequest, headersToEncrypt, listener));

        while (!hostPaired) {
            wait();
        }

        String origRoute = Uri.parse(origRequest.getUrl()).getPath();

        // make a COPY of the original headers to prevent modifying the original request.
        Map<String, String> origHeaders = new HashMap<>(origRequest.getHeaders());

        // Encrypt the route, headers and body
        EncodeResult encryptedRouteResult = encryptRoute(origRoute);
        EncodeResult encryptHeadersResult = NetworkHeaderHelper.processRequestHeaders(mteHelper, encryptedRouteResult.pairId, headersToEncrypt, origHeaders);
        EncodeResult encryptBodyBytesResult = encryptBodyBytes(encryptHeadersResult.pairId, origRequest, listener);
        byte[] encryptedBodyBytes = encryptBodyBytesResult.encodedBytes != null ? encryptBodyBytesResult.encodedBytes : null;

        RelayConnectionModel relayConnectionModel = new RelayConnectionModel(
                hostUrl,
                origRequest.getMethod(),
                encryptedRouteResult.encodedStr,
                null,
                null,
                encryptedBodyBytes,
                origHeaders,
                new RelayHeaders(hostClientId,
                        encryptBodyBytesResult.pairId,
                        "MKE",
                        encryptHeadersResult.encodedStr,
                        null),
                setRelayOptions(encryptedBodyBytes != null,
                        encryptedRouteResult.pairId));
        webHelper.sendBytes(relayConnectionModel, origRequest, new NetworkResponseListener() {
            @Override
            public void onError(NetworkResponse networkResponse, byte[] data, RelayHeaders relayHeaders) {
                if (NetworkUtils.shouldRePairWithHost(networkResponse.statusCode, prevRequestData)) {
                    rePairWithHost(createRePairCallback(prevRequestData, listener));
                } else {
                    Map<String, List<String>> processedHeaders = new HashMap<>();
                    String responseString = "Status Code: " + networkResponse + " ";
                    LogHelper.error("HOST", responseString);
                    try {
                        for (Header header : relayHeaders.responseHeaderList) {
                            processedHeaders.put(header.getName(), Collections.singletonList(header.getValue()));
                        }
                        NetworkHeaderHelper.processResponseHeaders(mteHelper, relayHeaders.pairId, processedHeaders, relayHeaders.encryptedDecryptedHeaders);
                        DecodeResult bodyDecodeResult;
                        if (data != null &&
                                data.length != 0) {
                            bodyDecodeResult = mteHelper.decode(relayHeaders.pairId, data);
                            if (bodyDecodeResult.decodedBytes != null) {
                                responseString = responseString + new String(bodyDecodeResult.decodedBytes, StandardCharsets.UTF_8);
                            }
                            try {
                                conditionallyStoreStates();
                            } catch (JSONException e) {
                                responseString = responseString + e.getMessage();
                            }
                        }
                    } catch (MteException e) {
                        responseString = responseString + e.getMessage();
                    }
                    listener.onError(networkResponse, responseString, processedHeaders);
                }
            }

            @Override
            public void onJsonResponse(NetworkResponse networkResponse, JSONObject jsonResponse, RelayHeaders relayHeaders) {
                listener.onError(networkResponse, "Unexpected Volley jsonResponse. Response: " + jsonResponse.toString(), null);
            }

            @Override
            public void onJsonArrayResponse(NetworkResponse networkResponse, JSONArray jsonArrayResponse, RelayHeaders relayHeaders) {
                listener.onError(networkResponse, "Unexpected Volley jsonArrayResponse. Response: " + jsonArrayResponse.toString(), null);
            }

            @Override
            public void onByteArrayResponse(NetworkResponse networkResponse, byte[] byteArrayResponse, RelayHeaders relayHeaders) {
                Map<String, List<String>> processedHeaders = new HashMap<>();
                try {
                    for (Header header : relayHeaders.responseHeaderList) {
                        processedHeaders.put(header.getName(), Collections.singletonList(header.getValue()));
                    }
                    NetworkHeaderHelper.processResponseHeaders(mteHelper, relayHeaders.pairId, processedHeaders, relayHeaders.encryptedDecryptedHeaders);
                } catch (MteException e) {
                    listener.onError(networkResponse, e.getMessage(), processedHeaders);
                }
                if (byteArrayResponse != null) {
                    DecodeResult bodyDecodeResult = mteHelper.decode(relayHeaders.pairId, byteArrayResponse);
                    try {
                        conditionallyStoreStates();
                    } catch (JSONException e) {
                        listener.onError(networkResponse, e.getMessage(), null);
                    }
                    listener.onResponse(networkResponse, bodyDecodeResult.decodedBytes, processedHeaders);
                    prevRequestData = null;
                }
            }
        });
    }

    public void rePairWithHost(InstantiateHostCallback callback) {
        LogHelper.info("HOST", "RePairing with " + hostUrl);
        try {
            hostStorageHelper.removeStoredHost();
            hostPaired = false;
            if (mteHelper.pairMap != null) {
                mteHelper.pairMap.clear();
            }
            Thread pairingTread = new Thread(() -> checkForRelayServer(callback));
            pairingTread.start();
        } catch (JSONException e) {
            callback.onError(e.getMessage());
        }
    }
    // endregion

    // region Pairing Private Methods
    private void pairWithHost(InstantiateHostCallback callback) {
        Thread pairingThread = new Thread(() -> {
            synchronized (lock) {
                while (hostStorageHelper == null) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        callback.onError(e.getMessage());
                    }
                }
                checkForRelayServer(callback);
            }
        });
        pairingThread.start();
    }

    private void getStoredStates(InstantiateHostCallback callback) {
        JSONObject storedPairs;
        try {
            storedPairs = new JSONObject(hostStorageHelper.getStoredPairsForHost(hostUrlB64));
            hostClientId = storedPairs.getString("clientId");
            boolean paired = mteHelper.refillPairMap(storedPairs.getString("pairMapStates"));
            if (paired) {
                notifyPaired();
                callback.hostInstantiated(hostUrl, Host.this);
            }
        } catch (JSONException e) {
            callback.onError(e.getMessage());
        }
    }

    synchronized private void checkForRelayServer(InstantiateHostCallback callback) {
        LogHelper.info("Host", "Checking for Host " + hostUrl);
        RelayConnectionModel connectionModel = new RelayConnectionModel(
                hostUrl,
                Request.Method.HEAD,
                "/api/mte-relay",
                null,
                null,
                null,
                null,
                new RelayHeaders(),
                setRelayOptions(true, null)
        );
        webHelper.sendJson(connectionModel, null, new NetworkResponseListener() {

            @Override
            public void onError(NetworkResponse networkResponse, byte[] data, RelayHeaders relayHeaders) {
                int statusCode;
                if (networkResponse != null) {
                    statusCode =  networkResponse.statusCode;
                } else {
                    statusCode = 503;
                }
                String errorMessage = "Code: " + statusCode + " Message: Unable to locate Relay Server at " + hostUrl;
                LogHelper.error("HOST", errorMessage);
                callback.onError(errorMessage);
            }

            @Override
            public void onJsonResponse(NetworkResponse networkResponse, JSONObject response, RelayHeaders relayHeaders) {
                LogHelper.info("HOST", "Host " + hostUrl + " found. Making Pairing call");
                hostClientId = relayHeaders.clientId;
                makePairingCall(hostUrl, callback);
            }

            @Override
            public void onJsonArrayResponse(NetworkResponse networkResponse, JSONArray jsonArrayResponse, RelayHeaders relayHeaders) {
                callback.onError("Unexpected Volley jsonArrayResponse. Response: " + jsonArrayResponse.toString());
            }

            @Override
            public void onByteArrayResponse(NetworkResponse networkResponse, byte[] byteArrayResponse, RelayHeaders relayHeaders) {
                callback.onError("Unexpected Volley byteArrayResponse. Response: " + Arrays.toString(byteArrayResponse));
            }
        });
    }

    synchronized private void makePairingCall(String hostUrl, InstantiateHostCallback callback) {
        Map<String, Pair> pairMap = mteHelper.createPairMap(RelaySettings.pairPoolSize);
        LogHelper.info("Host", "Pairing " + pairMap.size() + " pairs with " + hostUrl);
        JSONArray pairMapArray = new JSONArray();
        pairMap.forEach((pairId, pair) -> {
            JSONObject pairJson = new JSONObject();
            try {
                pairJson.put("pairId", pairId)
                        .put("encoderPublicKey", bytesToB64Str(pair.encMyPublicKey))
                        .put("encoderPersonalizationStr", pair.encPersStr)
                        .put("decoderPublicKey", bytesToB64Str(pair.decMyPublicKey))
                        .put("decoderPersonalizationStr", pair.decPersStr);
            } catch (JSONException e) {
                callback.onError("Error: " + e.getMessage());
            }
            pairMapArray.put(pairJson);
        });
        RelayConnectionModel connectionModel = new RelayConnectionModel(
                hostUrl,
                Request.Method.POST,
                "/api/mte-pair",
                null,
                pairMapArray,
                null,
                null,
                new RelayHeaders(hostClientId,
                        null,
                        "MKE",
                        "",
                        null),
                setRelayOptions(true,null)
        );
        webHelper.sendJsonArray(connectionModel, null, new NetworkResponseListener() {

            @Override
            public void onError(NetworkResponse networkResponse, byte[] data, RelayHeaders relayHeaders) {
                String errorMessage = "Code: " + networkResponse.statusCode + " Message: Unable to pair with relay Server " + hostUrl;
                LogHelper.error("HOST", errorMessage);
                callback.onError(errorMessage);
            }

            @Override
            public void onJsonResponse(NetworkResponse networkResponse, JSONObject jsonResponse, RelayHeaders relayHeaders) {
                callback.onError("Unexpected Volley jsonArrayResponse. Response: " + jsonResponse.toString());
            }

            @Override
            public void onJsonArrayResponse(NetworkResponse networkResponse, JSONArray response, RelayHeaders relayHeaders) {
                hostClientId = relayHeaders.clientId;

                String errorMessage;
                for (int i = 0; i < response.length(); i++) {
                    JSONObject pair;
                    String pairId;
                    try {
                        pair = response.getJSONObject(i);
                        pairId = pair.getString("pairId");
                        Pair currentPair = pairMap.get(pairId);
                        if (currentPair == null) {
                            callback.onError("Response Pair not found in pairMap");
                            return;
                        }
                        currentPair.encResponderEncryptedSecret = convertB64ToBytes(pair.getString("decoderSecret"));
                        currentPair.encNonce = Long.parseLong(pair.getString("decoderNonce"));
                        currentPair.decResponderEncryptedSecret = convertB64ToBytes(pair.getString("encoderSecret"));
                        currentPair.decNonce = Long.parseLong(pair.getString("encoderNonce"));
                        currentPair.createEncoderAndDecoder();
                    } catch (JSONException | MteException e) {
                        callback.onError(e.getMessage());
                        return;
                    }
                }
                try {
                    notifyPaired();
                    LogHelper.info("HOST", "Successfully paired with " + hostUrl);
                    callback.hostInstantiated(hostUrl, Host.this);
                    return;
                } catch (JSONException e) {
                    errorMessage = "Error: " + e.getMessage();
                }
                callback.onError(errorMessage);
            }

            @Override
            public void onByteArrayResponse(NetworkResponse networkResponse, byte[] byteArrayResponse, RelayHeaders relayHeaders) {
                callback.onError("Unexpected Volley jsonArrayResponse. Response: " + Arrays.toString(byteArrayResponse));
            }
        });
    }

    synchronized private void notifyPaired() throws JSONException {
        hostPaired = true;
        conditionallyStoreStates();
        notify();
    }

    private void conditionallyStoreStates() throws JSONException {
        JSONObject stateToStore = new JSONObject();
        String pairMapStates = "";
        stateToStore.put("clientId", hostClientId);
        stateToStore.put("pairMapStates", pairMapStates);

        // If we are persisting pairs, get the pair states and overwrite that element of the JSONObject.
        if (RelaySettings.persistPairs) {
            pairMapStates = mteHelper.getPairMapStates();
            stateToStore.put("pairMapStates", pairMapStates);
        }
        hostStorageHelper.saveHostToFile(stateToStore.toString());
    }
    // endregion

    // region Proxy Private Methods
    private InstantiateHostCallback createRePairCallback(RetryableRequestData requestData, RelayVolleyRequestListener listener) {
        return new InstantiateHostCallback() {
            @Override
            public void onError(String message) {
                listener.onError(null, message, null);
            }

            @Override
            public void hostInstantiated(String hostUrl, Host host) {
                if (requestData != null) {
                    requestData.retry(); // Calls the appropriate retry logic
                }
            }
        };
    }

    private <T> T storePrevRequest(T prevRequest, T newRequest) {
        return (prevRequest == null) ? newRequest : null;
    }

    RelayOptions setRelayOptions(boolean bodyIsEncoded, String pairId) {
        String clientId = hostClientId == null ? "" : hostClientId;
        return new RelayOptions(
                clientId,
                pairId,
                "MKE",
                true,
                true,
                bodyIsEncoded);
    }

    private EncodeResult encryptRoute(String route) throws UnsupportedEncodingException {
        route = route.substring(1); // remove the preceding '/'
        EncodeResult encryptedRouteResult = mteHelper.encode(null, route);

        // UrlEncode the route
        String urlEncodedRoute = URLEncoder.encode(encryptedRouteResult.encodedStr, StandardCharsets.UTF_8.toString());

        // Add the "/" back onto the UrlEncodedRoute
        encryptedRouteResult.encodedStr = "/" + urlEncodedRoute;
        LogHelper.info("HOST", "Encrypted Request Route");
        return encryptedRouteResult;
    }

    private <T> EncodeResult encryptBodyBytes(String pairId, Request<T> origRequest, RelayVolleyRequestListener listener) {
        byte[] origBody = new byte[0];
        try {
            origBody = origRequest.getBody();
        } catch (AuthFailureError e) {
            listener.onError(null, e.getMessage(), null);
        }
        if (origBody != null && origBody.length > 0) {
            LogHelper.info("HOST", "Encrypted Request Body");
            return mteHelper.encode(pairId, origBody);
        } else {
            LogHelper.info("HOST", "No Request Body to encrypt.");
            return new EncodeResult(pairId, origBody);
        }
    }

    private byte[] convertB64ToBytes(String value) {
        return Base64.getDecoder().decode(value);
    }

    String bytesToB64Str(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
    // endregion

}
