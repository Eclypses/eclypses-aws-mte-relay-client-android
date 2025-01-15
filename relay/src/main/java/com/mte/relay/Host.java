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
import com.android.volley.Request;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class Host {

    boolean hostPaired = false;
    Context ctx;
    String hostUrl, hostUrlB64;
    private HostStorageHelper hostStorageHelper;
    private final MteHelper mteHelper;
    private final WebHelper webHelper;
    private final Object lock = new Object();
    private int rePairAttempts = 1;
    private String hostClientId;

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

    public <T> void sendRequest(Request<T> req, String[] headersToEncrypt, String pathnamePrefix, RelayDataTaskListener listener) {
        Thread sendingTread = new Thread(() -> {
            try {
                sendUpdatedRequest(req, headersToEncrypt, pathnamePrefix, listener);
            } catch (InterruptedException | UnsupportedEncodingException e) {
                listener.onError(e.getMessage(), null);
            }
        });
        sendingTread.start();
    }

    public <T> void reSendRequest(Request<T> req, String[] headersToEncrypt, String pathnamePrefix, RelayDataTaskListener listener) {
        Thread sendingTread = new Thread(() -> {
            try {
                sendUpdatedRequest(req, headersToEncrypt, pathnamePrefix, listener);
            } catch (InterruptedException | UnsupportedEncodingException e) {
                listener.onError(e.getMessage(), null);
            }
        });
        sendingTread.start();
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

    synchronized private void checkForRelayServer(InstantiateHostCallback callback) {
        RelayConnectionModel connectionModel = new RelayConnectionModel(
                hostUrl,
                Request.Method.HEAD,
                "/api/mte-relay",
                null,
                null,
                null,
                "",
                new RelayHeaders(),
                setRelayOptions(true, null)
        );
        webHelper.sendJson(connectionModel, null, new RWHResponseListener() {

            @Override
            public void onError(int code, byte[] data, RelayHeaders relayHeaders) {
                callback.onError("Code: " + code + " Message: Unable to locate Relay Server at " + hostUrl);
            }

            @Override
            public void onJsonResponse(JSONObject response, RelayHeaders relayHeaders) {
                hostClientId = relayHeaders.clientId;
                makePairingCall(hostUrl, callback);
            }

            @Override
            public void onJsonArrayResponse(JSONArray jsonArrayResponse, RelayHeaders relayHeaders) {
                callback.onError("Unexpected Volley jsonArrayResponse. Response: " + jsonArrayResponse.toString());
            }

            @Override
            public void onByteArrayResponse(byte[] byteArrayResponse, RelayHeaders relayHeaders) {
                callback.onError("Unexpected Volley byteArrayResponse. Response: " + byteArrayResponse.toString());
            }
        });
    }

    synchronized private void makePairingCall(String hostUrl, InstantiateHostCallback callback) {
        Map<String, Pair> pairMap = mteHelper.createPairMap(RelaySettings.pairPoolSize);
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
        webHelper.sendJsonArray(connectionModel, null, new RWHResponseListener() {

            @Override
            public void onError(int code, byte[] data, RelayHeaders relayHeaders) {
                callback.onError("Code: " + code + " Message: Unable to pair with relay Server " + hostUrl);
            }

            @Override
            public void onJsonResponse(JSONObject jsonResponse, RelayHeaders relayHeaders) {
                callback.onError("Unexpected Volley jsonArrayResponse. Response: " + jsonResponse.toString());
            }

            @Override
            public void onJsonArrayResponse(JSONArray response, RelayHeaders relayHeaders) {
                hostClientId = relayHeaders.clientId;

                boolean pairingComplete = true;
                String errorMessage = "";
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
                        pairMap.get(pairId).createEncoderAndDecoder();
                    } catch (JSONException | MteException e) {;
                        callback.onError(e.getMessage());
                        return;
                    }
                }
                try {
                    notifyPaired();
                    callback.hostInstantiated(hostUrl, Host.this);
                    return;
                } catch (JSONException e) {
                    errorMessage = "Error: " + e.getMessage();
                }
                callback.onError(errorMessage);
            }

            @Override
            public void onByteArrayResponse(byte[] byteArrayResponse, RelayHeaders relayHeaders) {
                callback.onError("Unexpected Volley jsonArrayResponse. Response: " + byteArrayResponse.toString());
            }
        });
    }

    synchronized private void notifyPaired() throws JSONException {
        hostPaired = true;
        conditionallyStoreStates();
        notify();
    }

    synchronized private void sendUpdatedRequest(Request origRequest,
                                                 String[] headersToEncrypt,
                                                 String pathnamePrefix,
                                                 RelayDataTaskListener listener) throws InterruptedException, UnsupportedEncodingException {
        while (!hostPaired) {
            wait();
        }

        // Get the original route to put in the new route
        String origRoute = null;
        String origUrlStr = origRequest.getUrl();
        try {
            URL origUrl = new URL(origUrlStr);
            origRoute = origUrl.getPath();
        } catch (MalformedURLException e) {
            listener.onError(e.getMessage(), null);
        }

        // Encrypt the route and inject the pathnamePrefix if it exists
        EncodeResult encryptedRouteResult = encryptRoute(origRoute, pathnamePrefix);
        EncodeResult encryptHeadersResult = encryptHeaders(encryptedRouteResult.pairId, origRequest, headersToEncrypt, listener);
        EncodeResult encryptBodyBytesResult = encryptBodyBytes(encryptHeadersResult.pairId, origRequest, listener);
        byte[] encryptedBodyBytes = encryptBodyBytesResult.encodedBytes != null ? encryptBodyBytesResult.encodedBytes : null;

        RelayConnectionModel relayConnectionModel = new RelayConnectionModel(
                hostUrl,
                origRequest.getMethod(),
                encryptedRouteResult.encodedStr,
                null,
                null,
                encryptedBodyBytes,
                "",
                new RelayHeaders(hostClientId,
                        encryptBodyBytesResult.pairId,
                        "MKE",
                        encryptHeadersResult.encodedStr,
                        null),
                setRelayOptions(encryptedBodyBytes != null,
                        encryptedRouteResult.pairId));
        webHelper.sendBytes(relayConnectionModel, origRequest, new RWHResponseListener() {
            @Override
            public void onError(int code, byte[] data, RelayHeaders relayHeaders) {
                rePairCheck(code, new InstantiateHostCallback() {
                    @Override
                    public void onError(String message) {
                        listener.onError(message, null);
                    }

                    @Override
                    public void hostInstantiated(String hostUrl, Host host) {
                        reSendRequest(origRequest, headersToEncrypt, pathnamePrefix, listener);
                    }
                });
                Map<String, List<String>> responseHeaders = null;
                String responseString = "Status Code: " + code + " ";
                try {
                    responseHeaders = NetworkHeaderHelper.processVolleyResponseHeaders(relayHeaders, mteHelper);
                    DecodeResult bodyDecodeResult;
                    if (data != null &&
                            data.length != 0 &&
                            data.length != mteHelper.getEncryptFinishBytes()) {
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
                } catch (IOException | MteException e) {
                    responseString = responseString + e.getMessage();
                }
                listener.onError(responseString, responseHeaders);
            }

            @Override
            public void onJsonResponse(JSONObject jsonResponse, RelayHeaders relayHeaders) {
                listener.onError("Unexpected Volley jsonResponse. Response: " + jsonResponse.toString(), null);
            }

            @Override
            public void onJsonArrayResponse(JSONArray jsonArrayResponse, RelayHeaders relayHeaders) {
                listener.onError("Unexpected Volley jsonArrayResponse. Response: " + jsonArrayResponse.toString(), null);
            }

            @Override
            public void onByteArrayResponse(byte[] byteArrayResponse, RelayHeaders relayHeaders) {
                Map<String, List<String>> responseHeaders = null;
                try {
                    responseHeaders = NetworkHeaderHelper.processVolleyResponseHeaders(relayHeaders, mteHelper);
                } catch (IOException | MteException e) {
                    listener.onError(e.getMessage(), responseHeaders);
                }
                if (byteArrayResponse != null) {
                    DecodeResult bodyDecodeResult = mteHelper.decode(relayHeaders.pairId, byteArrayResponse);
                    try {
                        conditionallyStoreStates();
                    } catch (JSONException e) {
                        listener.onError(e.getMessage(), null);
                    }
                    rePairAttempts = 1;
                    listener.onResponse(bodyDecodeResult.decodedBytes, responseHeaders);
                }
            }
        });
    }

    private void rePairCheck(int code, InstantiateHostCallback callback) {
        if (559 <= code && code <= 569) {
            if (rePairAttempts < RelaySettings.pairPoolSize) {
                rePairAttempts ++;
                rePairWithHost(callback);
            }
        }
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

    private EncodeResult encryptRoute(String route, String pathnamePrefix) throws UnsupportedEncodingException {
        EncodeResult encryptedRouteResult = null;
        route = route.substring(1); // remove the preceding '/'
        encryptedRouteResult = mteHelper.encode(null, route);

        // Inject the pathnamePrefix if it exists
        if (pathnamePrefix != null) {

            // Remove the preceding "/" if it exists
            if (!pathnamePrefix.startsWith("/")) {
                pathnamePrefix = pathnamePrefix.substring(1);
            }

            // Inject pathnamePrefix
            encryptedRouteResult.encodedStr = pathnamePrefix + "/" + encryptedRouteResult.encodedStr;
        }

        // UrlEncode the route
        String urlEncodedRoute = URLEncoder.encode(encryptedRouteResult.encodedStr, StandardCharsets.UTF_8.toString());

        // Add the "/" back onto the UrlEncodedRoute
        encryptedRouteResult.encodedStr = "/" + urlEncodedRoute;
        return encryptedRouteResult;
    }

    private EncodeResult encryptHeaders(String pairId, Request origRequest, String[] headersToEncrypt, RelayDataTaskListener listener) {
        EncodeResult encodeResult = null;
        try {
            encodeResult = NetworkHeaderHelper.processRequestHeaders(mteHelper, pairId, headersToEncrypt, origRequest.getHeaders());
        } catch (AuthFailureError e) {
            listener.onError(e.getMessage(),null);
        }
        return encodeResult;
    }

    private EncodeResult encryptBodyBytes(String pairId, Request origRequest, RelayDataTaskListener listener) {
        byte[] origBody = new byte[0];
        try {
            origBody = origRequest.getBody();
        } catch (AuthFailureError e) {
            listener.onError(e.getMessage(), null);
        }
        if (origBody != null && origBody.length > 0) {
            return mteHelper.encode(pairId, origBody);
        } else {
            return new EncodeResult(pairId, origBody);
        }
    }

    synchronized public void uploadFile(RelayFileRequestProperties reqProperties,
                                        String route,
                                        String pathnamePrefix,
                                        RelayDataTaskListener listener,
                                        RelayStreamCompletionCallback completionCallback) {
        while (!hostPaired) {
            try {
                wait();
            } catch (InterruptedException e) {
                listener.onError(e.getMessage(), null);
            }
        }
        Thread sendingTread = new Thread(() -> {
            try {
                String pairId = mteHelper.getNextPairId();
                RelayFileUploadProperties properties = new RelayFileUploadProperties(
                        reqProperties.serverPath,
                        route,
                        reqProperties.file,
                        mteHelper,
                        reqProperties.headersToEncrypt,
                        reqProperties.origHeaders,
                        setRelayOptions(true, pairId),
                        reqProperties.relayStreamCallback);

                // Encrypt route and inject pathnamePrefix if it exists
                EncodeResult encryptRouteResult = encryptRoute(route, pathnamePrefix);
                properties.route = encryptRouteResult.encodedStr;
                properties.relayOptions.pairId = encryptRouteResult.pairId;

                FileUploadHelper fileUploadHelper = new FileUploadHelper(properties, listener, completionCallback);
                fileUploadHelper.encryptAndSend(() -> {
                    try {
                        conditionallyStoreStates();
                    } catch (JSONException e) {
                        listener.onError("Error: " + e.getMessage(), null);
                    }
                });
            } catch (IOException  | MteException e) {
                listener.onError(getClass().getSimpleName() + " Exception. Error: " +e.getMessage(), null);
            }
        });
        sendingTread.start();
    }

    synchronized public void downloadFile(RelayFileRequestProperties reqProperties, String pathnamePrefix, RelayDataTaskListener listener) throws IOException {
        while (!hostPaired) {
            try {
                wait();
            } catch (InterruptedException e) {
                listener.onError(getClass().getSimpleName() + " Exception. Error: " +e.getMessage(), null);
            }
        }
        // Get PairId to do this download
        String pairId = mteHelper.getNextPairId();
        FileDownloadProperties properties = new FileDownloadProperties(
                reqProperties.serverPath,
                reqProperties.route,
                reqProperties.downloadPath,
                mteHelper,
                reqProperties.headersToEncrypt,
                reqProperties.origHeaders,
                setRelayOptions(false, pairId));

        // Encrypt route and inject pathnamePrefix if it exists
        EncodeResult encryptRouteResult = encryptRoute(reqProperties.route, pathnamePrefix);
        properties.route = encryptRouteResult.encodedStr;
        properties.relayOptions.pairId = encryptRouteResult.pairId;

        FileDownloadHelper connectionHelper = new FileDownloadHelper(properties, listener);
        connectionHelper.downloadFile(() -> {
            try {
                conditionallyStoreStates();
            } catch (JSONException e) {
                listener.onError("Error: " + e.getMessage(), null);
            }
        });
    }



    private void retryUploadFile(RelayFileRequestProperties reqProperties,
                                 String route,
                                 String pathnamePrefix,
                                 RelayDataTaskListener listener,
                                 RelayStreamCompletionCallback completionCallback) {
        Thread sendingTread = new Thread(() -> {
            uploadFile(reqProperties, route, pathnamePrefix, listener, completionCallback);
        });
        sendingTread.start();
    }

    private void retryDownloadFile(RelayFileRequestProperties reqProperties, String pathnamePrefix, RelayDataTaskListener listener) {
        Thread sendingTread = new Thread(() -> {
            try {
                downloadFile(reqProperties, pathnamePrefix, listener);
            } catch (IOException e) {
                listener.onError("Error: " + e.getMessage(), null);
            }
        });
        sendingTread.start();
    }

    public void rePairWithHost(InstantiateHostCallback callback) {
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

    private byte[] convertB64ToBytes(String value) {
        return Base64.getDecoder().decode(value);
    }

    String bytesToB64Str(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

}
