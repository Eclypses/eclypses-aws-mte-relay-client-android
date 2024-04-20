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
    private static final int pairPoolSize = 3;
    private final Object lock = new Object();
    private int rePairAttempts = 1;

    public Host(Context ctx, String hostUrl) throws IOException {
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
                        public void foundStoredPairs(String storedHostsStr) {
                            JSONObject storedPairs;
                            try {
                                storedPairs = new JSONObject(storedHostsStr);
                                Settings.clientId = storedPairs.getString("clientId");
                                String pairMapStates = storedPairs.getString("pairMapStates");
                                if (!pairMapStates.isEmpty()) {
                                    boolean paired = mteHelper.refillPairMap(pairMapStates);
                                    if (paired) {
                                        notifyPaired();
                                    } else {
                                        pairWithHost();
                                    }
                                }
                            } catch (JSONException e) {
                                throw new RelayException(this.getClass().getSimpleName(),
                                        "Unable to refill pairMap from Stored Pairs. Error: " + e.getMessage());
                            }
                        }
                        @Override
                        public void noStoredPairs() {
                            pairWithHost();
                        }
                    });
                }
            } catch (IOException e) {
                throw new RelayException(this.getClass().getSimpleName(),
                        "Constructor Exception. Error: " + e.getMessage());
            }
        });
        storageThread.start();
    }

    private void pairWithHost() {
        Thread pairingThread = new Thread(() -> {
            synchronized (lock) {
                while (hostStorageHelper == null) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        throw new RelayException(this.getClass().getSimpleName(),
                                "Wait() Exception. Error: " + e.getMessage());
                    }
                }
                if (hostStorageHelper.foundStoredHost) {
                    getStoredStates();
                } else {
                    makeHeadRequest(new RelayResponseListener() {
                        @Override
                        public void onError(String message) {
                            throw new RelayException(this.getClass().getSimpleName(),
                                    "Unable to makeHeadRequest. Error: " + message);
                        }

                        @Override
                        public void onResponse(byte[] responseBytes, Map<String, List<String>> responseHeaders) {
                            throw new RelayException(this.getClass().getSimpleName(),
                                    "Unexpected Volley jsonArrayResponse. Response: " + responseBytes.toString());
                        }

                        @Override
                        public void onResponse(JSONObject responseJson, Map<String, List<String>> responseHeaders) {
                            if (BuildConfig.DEBUG) {
                                Log.i("MTE", "Completed HEAD Request");
                            }
                        }
                    });
                }
            }
        });
        pairingThread.start();
    }

    private void getStoredStates() {
        JSONObject storedPairs;
        try {
            storedPairs = new JSONObject(hostStorageHelper.getStoredPairsForHost(hostUrlB64));
            Settings.clientId = storedPairs.getString("clientId");
            boolean paired = mteHelper.refillPairMap(storedPairs.getString("pairMapStates"));
            if (paired) {
                notifyPaired();
            }
        } catch (JSONException e) {
            throw new RelayException(this.getClass().getSimpleName(),
                    "Unable to refill pairMap with stored pairs. Error: " + e.getMessage());
        }
    }

    public <T> void sendRequest(Request<T> req, String[] headersToEncrypt, RelayResponseListener listener) {
        Thread sendingTread = new Thread(() -> {
            try {
                sendUpdatedRequest(req, headersToEncrypt, listener);
            } catch (InterruptedException | UnsupportedEncodingException e) {
                throw new RelayException(getClass().getSimpleName(),
                        "sendRequestException: Error: " + e.getMessage());
            }
        });
        sendingTread.start();
    }

    public <T> void reSendRequest(Request<T> req, String[] headersToEncrypt, RelayResponseListener listener) {
        Thread sendingTread = new Thread(() -> {
            try {
                sendUpdatedRequest(req, headersToEncrypt, listener);
            } catch (InterruptedException | UnsupportedEncodingException e) {
                throw new RelayException(getClass().getSimpleName(),
                        "reSendRequestException: Error: " + e.getMessage());
            }
        });
        sendingTread.start();
    }

    RelayOptions setRelayOptions(String requestMethod, String pairId) {
        boolean bodyIsEncoded = requestMethod == "POST" ? true : false;
        return new RelayOptions(
                Settings.clientId,
                pairId,
                "MKE",
                true,
                true,
                bodyIsEncoded);
    }

    synchronized private void makeHeadRequest(RelayResponseListener listener) {
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
            public void onError(int code, String message, RelayHeaders relayHeaders) {
                throw new RelayException(this.getClass().getSimpleName(),
                        "sendJson Volley error. Error: " + message);
            }

            @Override
            public void onJsonResponse(JSONObject response, RelayHeaders relayHeaders) {
                Settings.clientId = relayHeaders.clientId;
                SharedPreferencesHelper.saveString(ctx, "ClientId", relayHeaders.clientId);
                pairWithHost(hostUrl, listener);
            }

            @Override
            public void onJsonArrayResponse(JSONArray jsonArrayResponse, RelayHeaders relayHeaders) {
                throw new RelayException(this.getClass().getSimpleName(),
                        "Unexpected Volley jsonArrayResponse. Response: " + jsonArrayResponse.toString());
            }

            @Override
            public void onByteArrayResponse(byte[] byteArrayResponse, RelayHeaders relayHeaders) {
                throw new RelayException(this.getClass().getSimpleName(),
                        "Unexpected Volley byteArrayResponse. Response: " + byteArrayResponse.toString());
            }
        });
    }

    synchronized private void pairWithHost(String hostUrl, RelayResponseListener listener) {
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
                throw new RelayException(this.getClass().getSimpleName(),
                        "Unable to add elements to JSONObject. Error: " + e.getMessage());
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
                new RelayHeaders(Settings.clientId,
                        null,
                        "MKE",
                        "",
                        null),
                setRelayOptions(RequestMethod.POST,null)
        );
        webHelper.sendJsonArray(connectionModel, null, new RWHResponseListener() {
            @Override
            public void onError(int code, String message, RelayHeaders relayHeaders) {
                throw new RelayException(this.getClass().getSimpleName(),
                        "sendJsonArray Volley error. Error: " + message);
            }

            @Override
            public void onJsonResponse(JSONObject jsonResponse, RelayHeaders relayHeaders) {
                throw new RelayException(this.getClass().getSimpleName(),
                        "Unexpected Volley jsonResponse. Response: " + jsonResponse.toString());
            }

            @Override
            public void onJsonArrayResponse(JSONArray response, RelayHeaders relayHeaders) {
                Settings.clientId = relayHeaders.clientId;
                for (int i = 0; i < response.length(); i++) {
                    JSONObject pair;
                    String pairId;
                    try {
                        pair = response.getJSONObject(i);
                        pairId = pair.getString("pairId");
                        Pair currentPair = pairMap.get(pairId);
                        if (currentPair == null) {
                            throw new RelayException(getClass().getSimpleName(),
                                    "Response Pair not found in pairMap");
                        }
                        currentPair.encResponderEncryptedSecret = convertB64ToBytes(pair.getString("decoderSecret"));
                        currentPair.encNonce = Long.parseLong(pair.getString("decoderNonce"));
                        currentPair.decResponderEncryptedSecret = convertB64ToBytes(pair.getString("encoderSecret"));
                        currentPair.decNonce = Long.parseLong(pair.getString("encoderNonce"));
                    } catch (JSONException e) {
                        throw new RelayException(getClass().getSimpleName(),
                                "Unable to create json Exception: Error: " + e.getMessage());
                    }
                    pairMap.get(pairId).createEncoderAndDecoder();
                    JSONObject responseJson = new JSONObject();
                    try {
                        responseJson.put("Response Code","200");
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                    listener.onResponse(responseJson, null);
                }
                notifyPaired();
            }

            @Override
            public void onByteArrayResponse(byte[] byteArrayResponse, RelayHeaders relayHeaders) {
                throw new RelayException(this.getClass().getSimpleName(),
                        "Unexpected Volley byteArrayResponse. Response: " + byteArrayResponse.toString());
            }
        });
    }

    synchronized private void notifyPaired() {
        hostPaired = true;
        storeStates();
        notify();
    }

    synchronized private void sendUpdatedRequest(Request origRequest,
                                                 String[] headersToEncrypt,
                                                 RelayResponseListener listener) throws InterruptedException, UnsupportedEncodingException {
        while (!hostPaired) {
            wait();
        }
        // Get the original route to put in the new route
        String origRoute;
        String origUrlStr = origRequest.getUrl();
        try {
            URL origUrl = new URL(origUrlStr);
            origRoute = origUrl.getPath().substring(1); // remove the preceding '/'
        } catch (MalformedURLException e) {
            throw new RelayException(this.getClass().getSimpleName(),
                    "MalformedUrlException. Error: " + e.getMessage());
        }
        EncodeResult encryptedRouteResult = mteHelper.encode(null, origRoute);
        String urlEncodedRoute = URLEncoder.encode(encryptedRouteResult.encodedStr, StandardCharsets.UTF_8.toString());
        EncodeResult encryptHeadersResult = encryptHeaders(encryptedRouteResult.pairId, origRequest, headersToEncrypt);
        EncodeResult encryptBodyBytesResult = encryptBodyBytes(encryptHeadersResult.pairId, origRequest);
        RelayConnectionModel relayConnectionModel = new RelayConnectionModel(
                hostUrl,
                RequestMethod.POST,
                urlEncodedRoute,
                null,
                null,
                encryptBodyBytesResult.encodedBytes,
                "",
                new RelayHeaders(Settings.clientId,
                        encryptBodyBytesResult.pairId,
                        "MKE",
                        encryptHeadersResult.encodedStr,
                        null),
                setRelayOptions(RequestMethod.POST, encryptedRouteResult.pairId));
        webHelper.sendBytes(relayConnectionModel, origRequest, new RWHResponseListener() {
            @Override
            public void onError(int code, String message, RelayHeaders relayHeaders) {
                rePairCheck(code, origRequest, headersToEncrypt, listener);
                if (BuildConfig.DEBUG) {
                    Log.e("MTE", message);
                }
                listener.onError(message);
            }

            @Override
            public void onJsonResponse(JSONObject jsonResponse, RelayHeaders relayHeaders) {
                throw new RelayException(this.getClass().getSimpleName(),
                        "Unexpected Volley jsonResponse. Response: " + jsonResponse.toString());
            }

            @Override
            public void onJsonArrayResponse(JSONArray jsonArrayResponse, RelayHeaders relayHeaders) {
                throw new RelayException(this.getClass().getSimpleName(),
                        "Unexpected Volley jsonArrayResponse. Response: " + jsonArrayResponse.toString());
            }

            @Override
            public void onByteArrayResponse(byte[] byteArrayResponse, RelayHeaders relayHeaders) {
                Map<String, List<String>> responseHeaders;
                try {
                    responseHeaders = NetworkHeaderHelper.processVolleyResponseHeaders(relayHeaders, mteHelper);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if (byteArrayResponse != null) {
                    DecodeResult bodyDecodeResult = mteHelper.decode(relayHeaders.pairId, byteArrayResponse);
                    storeStates();
                    rePairAttempts = 1;
                    listener.onResponse(bodyDecodeResult.decodedBytes, responseHeaders);
                }
            }
        });
    }

    private void rePairCheck(int code, Request origRequest, String[] headersToEncrypt, RelayResponseListener listener) {
        if (560 <= code && code <= 562) {
            if (rePairAttempts < pairPoolSize) {
                if (BuildConfig.DEBUG) {
                    Log.w("MTE", "Server is no longer paired with Client so we will attempt to rePair and reSend.");
                }
                rePairAttempts ++;
                rePairWithHost(listener);
                reSendRequest(origRequest, headersToEncrypt, listener);
            }
        }
    }

    public static String convertBytestoIntArrayString(byte[] bytes) {
        int[] intArray = new int[bytes.length];

        // converting byteArray to intArray
        for (int i = 0; i < bytes.length; intArray[i] = Byte.toUnsignedInt(bytes[i++]));

        return Arrays.toString(intArray);
    }

    private void storeStates() {
        try {
            String pairMapStates = mteHelper.getPairMapStates();
            JSONObject stateToStore = new JSONObject();
            stateToStore
                    .put("pairMapStates", pairMapStates)
                    .put("clientId", Settings.clientId);
            hostStorageHelper.saveHostToFile(stateToStore.toString());
        } catch (JSONException e) {
            throw new RelayException(getClass().getSimpleName(), e.getMessage());
        }
    }

    private EncodeResult encryptHeaders(String pairId, Request origRequest, String[] headersToEncrypt) {
        EncodeResult encodeResult;
        try {
            encodeResult = NetworkHeaderHelper.processRequestHeaders(mteHelper, pairId, headersToEncrypt, origRequest.getHeaders());
        } catch (AuthFailureError e) {
            throw new RelayException(getClass().getSimpleName(), e.getMessage());
        }
        return encodeResult;
    }

    private EncodeResult encryptBodyBytes(String pairId, Request origRequest) {
        byte[] origBody;
        try {
            origBody = origRequest.getBody();
        } catch (AuthFailureError e) {
            throw new RelayException(getClass().getSimpleName(), e.getMessage());
        }
        return mteHelper.encode(pairId, origBody);
    }



    synchronized public void uploadFile(RelayFileRequestProperties reqProperties, String route, RelayResponseListener listener) {
        while (!hostPaired) {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new RelayException(this.getClass().getSimpleName(),
                        "InterruptedException. Error: " + e.getMessage());
            }
        }
        Thread sendingTread = new Thread(() -> {
            try {
                String pairId = mteHelper.getNextPairId();
                RelayFileUploadProperties properties = new RelayFileUploadProperties(
                        hostUrl,
                        route,
                        reqProperties.file,
                        mteHelper,
                        reqProperties.headersToEncrypt,
                        reqProperties.origHeaders,
                        setRelayOptions(RequestMethod.POST, pairId),
                        reqProperties.relayStreamCallback);
                FileUploadHelper fileUploadHelper = new FileUploadHelper(properties, listener);
                fileUploadHelper.encryptAndSend(this::storeStates);
            } catch (IOException  | MteException e) {
                listener.onError(getClass().getSimpleName() + " Exception. Error: " +e.getMessage());
            }
        });
        sendingTread.start();
    }

    synchronized public void downloadFile(RelayFileRequestProperties reqProperties, RelayResponseListener listener) throws IOException {
        while (!hostPaired) {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new RelayException(this.getClass().getSimpleName(),
                        "InterruptedException. Error: " + e.getMessage());
            }
        }
        // Get PairId to do this download
        String pairId = mteHelper.getNextPairId();
        FileDownloadProperties properties = new FileDownloadProperties(
                hostUrl,
                reqProperties.route,
                reqProperties.downloadPath,
                mteHelper,
                reqProperties.headersToEncrypt,
                reqProperties.origHeaders,
                setRelayOptions(RequestMethod.GET, pairId));
        FileDownloadHelper connectionHelper = new FileDownloadHelper(properties, listener);
        connectionHelper.downloadFile(this::storeStates);
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
                throw new RuntimeException(e);
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
            Thread pairingTread = new Thread(() -> makeHeadRequest(listener));
            pairingTread.start();
        } catch (IOException| JSONException e) {
            throw new RelayException(getClass().getSimpleName(), e.getMessage());
        }
    }

    private byte[] convertB64ToBytes(String value) {
        return Base64.getDecoder().decode(value);
    }

    String bytesToB64Str(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static String bytesToDecimal(byte[] bytes) {
        StringBuilder decimal = new StringBuilder();
        for (byte b : bytes)
            decimal.append(Byte.toUnsignedInt(b)).append(", ");
        return decimal.toString();
    }
}
