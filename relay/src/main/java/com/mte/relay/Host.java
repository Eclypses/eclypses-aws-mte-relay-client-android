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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Host {

    boolean hostPaired = false;
    Context ctx;
    String hostUrl, hostUrlB64;
    private HostStorageHelper hostStorageHelper;
    private final MteHelper mteHelper;
    private final WebHelper webHelper;
    private static final int pairPoolSize = 3;
    private final Object lock = new Object();
    private int rePairAttempts = 1;
    private int reInstantiateAttempts = 1;
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

                checkForRelayServer(new RelayResponseListener() {
                    @Override
                    public void onError(String message,  Map<String, List<String>> responseHeaders) {
                        callback.onError(message);
                    }

                    @Override
                    public void onResponse(byte[] responseBytes, Map<String, List<String>> responseHeaders) {
                        callback.onError("Unexpected Volley jsonArrayResponse. Response: " + responseBytes.toString());
                    }

                    @Override
                    public void onResponse(JSONObject responseJson, Map<String, List<String>> responseHeaders) {
                        callback.hostInstantiated(hostUrl, Host.this);
                    }
                });
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

    public <T> void sendRequest(Request<T> req, String[] headersToEncrypt, RelayResponseListener listener) {
        Thread sendingTread = new Thread(() -> {
            try {
                sendUpdatedRequest(req, headersToEncrypt, listener);
            } catch (InterruptedException | UnsupportedEncodingException e) {
                listener.onError(e.getMessage(), null);
            }
        });
        sendingTread.start();
    }

    public <T> void reSendRequest(Request<T> req, String[] headersToEncrypt, RelayResponseListener listener) {
        Thread sendingTread = new Thread(() -> {
            try {
                sendUpdatedRequest(req, headersToEncrypt, listener);
            } catch (InterruptedException | UnsupportedEncodingException e) {
                listener.onError(e.getMessage(), null);
            }
        });
        sendingTread.start();
    }

    RelayOptions setRelayOptions(String requestMethod, String pairId) {
        boolean bodyIsEncoded = Objects.equals(requestMethod, "POST");
        String clientId = hostClientId == null ? "" : hostClientId;
        return new RelayOptions(
                clientId,
                pairId,
                "MKE",
                true,
                true,
                bodyIsEncoded);
    }

    synchronized private void checkForRelayServer(RelayResponseListener listener) {
        RelayConnectionModel connectionModel = new RelayConnectionModel(
                hostUrl,
                RequestMethod.HEAD,
                "api/mte-relay",
                null,
                null,
                null,
                "",
                new RelayHeaders(),
                setRelayOptions(RequestMethod.HEAD, null)
        );
        webHelper.sendJson(connectionModel, null, new RWHResponseListener() {

            @Override
            public void onError(int code, byte[] data, RelayHeaders relayHeaders) {
                if (code >= 559 && code <= 569 && reInstantiateAttempts < pairPoolSize) {
                    reInstantiateAttempts += 1;
                    hostClientId = "";
                    rePairWithHost(listener);
                } else {
                    listener.onError("Code: " + code + " Message: Unable to locate Relay Server at " + hostUrl, null);
                }
            }

            @Override
            public void onJsonResponse(JSONObject response, RelayHeaders relayHeaders) {
                hostClientId = relayHeaders.clientId;
                makePairingCall(hostUrl, listener);
            }

            @Override
            public void onJsonArrayResponse(JSONArray jsonArrayResponse, RelayHeaders relayHeaders) {
                listener.onError("Unexpected Volley jsonArrayResponse. Response: " + jsonArrayResponse.toString(), null);
            }

            @Override
            public void onByteArrayResponse(byte[] byteArrayResponse, RelayHeaders relayHeaders) {
                listener.onError("Unexpected Volley byteArrayResponse. Response: " + byteArrayResponse.toString(), null);
            }
        });
    }

    synchronized private void makePairingCall(String hostUrl, RelayResponseListener listener) {
        Map<String, Pair> pairMap = mteHelper.createPairMap(pairPoolSize);
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
                listener.onError("Error: " + e.getMessage(), null);
            }
            pairMapArray.put(pairJson);
        });
        RelayConnectionModel connectionModel = new RelayConnectionModel(
                hostUrl,
                RequestMethod.POST,
                "api/mte-pair",
                null,
                pairMapArray,
                null,
                null,
                new RelayHeaders(hostClientId,
                        null,
                        "MKE",
                        "",
                        null),
                setRelayOptions(RequestMethod.POST,null)
        );
        webHelper.sendJsonArray(connectionModel, null, new RWHResponseListener() {
            Map<String, List<String>> responseHeaders = null;

            @Override
            public void onError(int code, byte[] data, RelayHeaders relayHeaders) {
                try {
                    responseHeaders = NetworkHeaderHelper.processVolleyResponseHeaders(relayHeaders, mteHelper);
                } catch (IOException e) {
                    listener.onError("Code: " + code + " Message: Unable to pair with relay Server " + hostUrl, null);
                }
                listener.onError("Code: " + code + " Message: Unable to pair with relay Server " + hostUrl, responseHeaders);
            }

            @Override
            public void onJsonResponse(JSONObject jsonResponse, RelayHeaders relayHeaders) {
                listener.onError("Unexpected Volley jsonArrayResponse. Response: " + jsonResponse.toString(), null);
            }

            @Override
            public void onJsonArrayResponse(JSONArray response, RelayHeaders relayHeaders) {
                hostClientId = relayHeaders.clientId;

                JSONObject responseJson = new JSONObject();
                for (int i = 0; i < response.length(); i++) {
                    JSONObject pair;
                    String pairId = null;
                    try {
                        pair = response.getJSONObject(i);
                        pairId = pair.getString("pairId");
                        Pair currentPair = pairMap.get(pairId);
                        if (currentPair == null) {
                            listener.onError("Response Pair not found in pairMap", null);
                            return;
                        }
                        currentPair.encResponderEncryptedSecret = convertB64ToBytes(pair.getString("decoderSecret"));
                        currentPair.encNonce = Long.parseLong(pair.getString("decoderNonce"));
                        currentPair.decResponderEncryptedSecret = convertB64ToBytes(pair.getString("encoderSecret"));
                        currentPair.decNonce = Long.parseLong(pair.getString("encoderNonce"));
                    } catch (JSONException e) {
                        listener.onError(e.getMessage(), null);
                    }
                    pairMap.get(pairId).createEncoderAndDecoder();
                }
                try {
                    responseJson.put("Successfully Paired with Relay Server","Response Code: 200");
                } catch (JSONException e) {
                    listener.onError(e.getMessage(), null);
                }
                try {
                    notifyPaired();
                } catch (JSONException e) {
                    listener.onError("Error: " + e.getMessage(), null);
                }
                listener.onResponse(responseJson, null);
            }

            @Override
            public void onByteArrayResponse(byte[] byteArrayResponse, RelayHeaders relayHeaders) {
                listener.onError("Unexpected Volley jsonArrayResponse. Response: " + byteArrayResponse.toString(), null);
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
                                                 RelayResponseListener listener) throws InterruptedException, UnsupportedEncodingException {
        while (!hostPaired) {
            wait();
        }
        // Get the original route to put in the new route
        String origRoute = null;
        String origUrlStr = origRequest.getUrl();
        try {
            URL origUrl = new URL(origUrlStr);
            origRoute = origUrl.getPath().substring(1); // remove the preceding '/'
        } catch (MalformedURLException e) {
            listener.onError(e.getMessage(), null);
        }
        EncodeResult encryptedRouteResult = mteHelper.encode(null, origRoute);
        String urlEncodedRoute = URLEncoder.encode(encryptedRouteResult.encodedStr, StandardCharsets.UTF_8.toString());
        EncodeResult encryptHeadersResult = encryptHeaders(encryptedRouteResult.pairId, origRequest, headersToEncrypt, listener);
        EncodeResult encryptBodyBytesResult = encryptBodyBytes(encryptHeadersResult.pairId, origRequest, listener);
        RelayConnectionModel relayConnectionModel = new RelayConnectionModel(
                hostUrl,
                RequestMethod.POST,
                urlEncodedRoute,
                null,
                null,
                encryptBodyBytesResult.encodedBytes,
                "",
                new RelayHeaders(hostClientId,
                        encryptBodyBytesResult.pairId,
                        "MKE",
                        encryptHeadersResult.encodedStr,
                        null),
                setRelayOptions(RequestMethod.POST, encryptedRouteResult.pairId));
        webHelper.sendBytes(relayConnectionModel, origRequest, new RWHResponseListener() {
            @Override
            public void onError(int code, byte[] data, RelayHeaders relayHeaders) {
                rePairCheck(code, origRequest, headersToEncrypt, listener);
                Map<String, List<String>> responseHeaders = null;
                String responseString = "";
                try {
                    responseHeaders = NetworkHeaderHelper.processVolleyResponseHeaders(relayHeaders, mteHelper);
                    DecodeResult bodyDecodeResult;
                    if (data != null) {
                        bodyDecodeResult = mteHelper.decode(relayHeaders.pairId, data);
                        if (bodyDecodeResult.decodedBytes != null) {
                            responseString = new String(bodyDecodeResult.decodedBytes, StandardCharsets.UTF_8);
                        }
                        try {
                            conditionallyStoreStates();
                        } catch (JSONException e) {
                            listener.onError(e.getMessage(), responseHeaders);
                        }
                    }
                } catch (IOException e) {
                    listener.onError(e.getMessage(), responseHeaders);
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
                } catch (IOException e) {
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

    private void rePairCheck(int code, Request origRequest, String[] headersToEncrypt, RelayResponseListener listener) {
        if (559 <= code && code <= 569) {
            if (rePairAttempts < pairPoolSize) {
                rePairAttempts ++;
                rePairWithHost(listener);
                reSendRequest(origRequest, headersToEncrypt, listener);
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

    private EncodeResult encryptHeaders(String pairId, Request origRequest, String[] headersToEncrypt, RelayResponseListener listener) {
        EncodeResult encodeResult = null;
        try {
            encodeResult = NetworkHeaderHelper.processRequestHeaders(mteHelper, pairId, headersToEncrypt, origRequest.getHeaders());
        } catch (AuthFailureError e) {
            listener.onError(e.getMessage(),null);
        }
        return encodeResult;
    }

    private EncodeResult encryptBodyBytes(String pairId, Request origRequest, RelayResponseListener listener) {
        byte[] origBody = new byte[0];
        try {
            origBody = origRequest.getBody();
        } catch (AuthFailureError e) {
            listener.onError(e.getMessage(), null);
        }
        return mteHelper.encode(pairId, origBody);
    }



    synchronized public void uploadFile(RelayFileRequestProperties reqProperties, String route, RelayResponseListener listener) {
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
                        setRelayOptions(RequestMethod.POST, pairId),
                        reqProperties.relayStreamCallback);
                FileUploadHelper fileUploadHelper = new FileUploadHelper(properties, listener);
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

    synchronized public void downloadFile(RelayFileRequestProperties reqProperties, RelayResponseListener listener) throws IOException {
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
                setRelayOptions(RequestMethod.GET, pairId));
        FileDownloadHelper connectionHelper = new FileDownloadHelper(properties, listener);
        connectionHelper.downloadFile(() -> {
            try {
                conditionallyStoreStates();
            } catch (JSONException e) {
                listener.onError("Error: " + e.getMessage(), null);
            }
        });
    }



    private void retryUploadFile(RelayFileRequestProperties reqProperties, String route, RelayResponseListener listener) {
        Thread sendingTread = new Thread(() -> {
            uploadFile(reqProperties, route, listener);
        });
        sendingTread.start();
    }

    private void retryDownloadFile(RelayFileRequestProperties reqProperties, RelayResponseListener listener) {
        Thread sendingTread = new Thread(() -> {
            try {
                downloadFile(reqProperties, listener);
            } catch (IOException e) {
                listener.onError("Error: " + e.getMessage(), null);
            }
        });
        sendingTread.start();
    }

    public void rePairWithHost(RelayResponseListener listener) {
        try {
            hostStorageHelper.removeStoredHost();
            hostPaired = false;
            if (mteHelper.pairMap != null) {
                mteHelper.pairMap.clear();
            }
            Thread pairingTread = new Thread(() -> checkForRelayServer(listener));
            pairingTread.start();
        } catch (JSONException e) {
            listener.onError(e.getMessage(), null);
        }
    }

    private byte[] convertB64ToBytes(String value) {
        return Base64.getDecoder().decode(value);
    }

    String bytesToB64Str(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

}
