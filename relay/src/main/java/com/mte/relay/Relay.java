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

import com.android.volley.Request;
import com.eclypses.mte.MteBase;

import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Relay {

    private static Relay instance;
    private final Map<String, Host> pairedHosts = new HashMap<>();
    private final Context ctx;
    private final RelayResponseListener relayResponseListener;

    public static Relay getInstance(Context context, RelayResponseListener listener) {
        if (instance == null) {
            instance = new Relay(context, listener);
        }
        return instance;
    }

    private Relay(Context context, RelayResponseListener listener) {
        if (!MteBase.initLicense(RelaySettings.licenseCompanyName, RelaySettings.licenseKey)) {
            throw new RelayException(getClass().getSimpleName(), "MTE License Check Failed");
        }
        ctx = context;
        relayResponseListener = listener;
    }

    public <T> void addToMteRequestQueue(Request<T> req, String[] headersToEncrypt, RelayDataTaskListener listener) {
        addToMteRequestQueue(req, headersToEncrypt, null, listener);
    }

    public <T> void addToMteRequestQueue(Request<T> req, String[] headersToEncrypt, String pathnamePrefix, RelayDataTaskListener listener) {
        String relayServerPath = null;
        try {
            URL relayServerUrl = new URL(req.getUrl());
            String protocol = relayServerUrl.getProtocol();
            String authority = relayServerUrl.getAuthority();
            relayServerPath = protocol + "://" + authority;
        } catch (MalformedURLException e) {
            listener.onError(e.getMessage(), null);
        }
        getHost(relayServerPath, new InstantiateHostCallback() {
            @Override
            public void onError(String message) {
                listener.onError(message, null); }

            @Override
            public void hostInstantiated(String hostUrl, Host host) {
                host.sendRequest(req, headersToEncrypt, pathnamePrefix, listener);
            }
        });
    }

    public void uploadFile(RelayFileRequestProperties reqProperties,
                           String route,
                           RelayDataTaskListener listener,
                           RelayStreamCompletionCallback completionCallback) {
        uploadFile(reqProperties, route, null, listener, completionCallback);
    }

    public void uploadFile(RelayFileRequestProperties reqProperties,
                           String route,
                           String pathnamePrefix,
                           RelayDataTaskListener listener,
                           RelayStreamCompletionCallback completionCallback) {

        getHost(reqProperties.serverPath, new InstantiateHostCallback() {
            @Override
            public void onError(String message) { listener.onError(message, null); }

            @Override
            public void hostInstantiated(String hostUrl, Host host) {
                host.uploadFile(reqProperties, route, pathnamePrefix, listener, completionCallback);
            }
        });
    }

    public void downloadFile(RelayFileRequestProperties reqProperties, RelayDataTaskListener listener) {
        downloadFile(reqProperties, null, listener);
    }

    public void downloadFile(RelayFileRequestProperties reqProperties, String pathnamePrefix, RelayDataTaskListener listener) {
        getHost(reqProperties.serverPath, new InstantiateHostCallback() {
            @Override
            public void onError(String message) { listener.onError(message, null); }

            @Override
            public void hostInstantiated(String hostUrl, Host host) {
                try {
                    host.downloadFile(reqProperties, pathnamePrefix, listener);
                } catch (IOException e) {
                    listener.onError(e.getMessage(), null);
                }
            }
        });
    }

    private void getHost(String hostUrl, InstantiateHostCallback callback) {
        final Host[] hostToReturn = { pairedHosts.get(hostUrl) };
        if (hostToReturn[0] == null) {
            new Host(ctx, hostUrl, new InstantiateHostCallback() {
                @Override
                public void onError(String message) {
                    relayResponseListener.onCompletion(false, message);
                }

                @Override
                public void hostInstantiated(String hostUrl, Host host) {
                    pairedHosts.put(hostUrl, host);
                    callback.hostInstantiated(hostUrl, host);
                }
            });
        } else {
            callback.hostInstantiated(hostUrl, hostToReturn[0]);
        }
    }

    public void rePairWithRelayServer(String serverUrl) {
        getHost(serverUrl, new InstantiateHostCallback() {
            @Override
            public void onError(String message) { relayResponseListener.onCompletion(false, message); }

            @Override
            public void hostInstantiated(String hostUrl, Host host) {
                host.rePairWithHost(new InstantiateHostCallback() {
                    @Override
                    public void onError(String message) {
                        relayResponseListener.onCompletion(false, message);
                    }

                    @Override
                    public void hostInstantiated(String hostUrl, Host host) {
                        pairedHosts.put(hostUrl, host);
                        relayResponseListener.onCompletion(true, "Successfully Re-Paired with " + hostUrl);
                    }
                });
            }
        });
    }

    public String adjustRelaySettings(String serverUrl,
                                      int newStreamChunkSize,
                                      int newPairPoolSize,
                                      Boolean persistPairs) {
        String responseMessage = "";
        if (serverUrl == null || serverUrl.isEmpty()) {
            return "ServerUrl is a required parameter, because Relay must be Re-Paired with server when Settings are changed";
        }
        if (newStreamChunkSize != 0 && newStreamChunkSize != getStreamChunkSizeSetting()) {
            setStreamChunkSize(newStreamChunkSize);
            responseMessage = responseMessage + "\nRelaySetting.streamChunkSize adjusted to " + newStreamChunkSize;
        }
        if (newPairPoolSize != 0 && newPairPoolSize != getPairPoolSizeSetting()) {
            setPairPoolSize(newPairPoolSize);
            responseMessage = responseMessage + "\nRelaySetting.pairPoolSize adjusted to " + newPairPoolSize;
        }
        if (persistPairs != getPersistPairsSetting()) {
            setPersistPairs(persistPairs);
            responseMessage = responseMessage + "\nRelaySetting.persistPairs adjusted to " + persistPairs;
        }
        if (responseMessage.isEmpty()) {
            responseMessage = "\nNo Relay Settings were changed based on arguments and existing RelaySettings";
        } else {
            rePairWithRelayServer(serverUrl);
            responseMessage = responseMessage + "\nAlso, Relay was Re-Paired with " + serverUrl ;
        }
        return responseMessage;
    }

    public int getStreamChunkSizeSetting() {
        return RelaySettings.streamChunkSize;
    }

    public void setStreamChunkSize(int newSize) {
        RelaySettings.streamChunkSize = newSize;
    }

    public int getPairPoolSizeSetting() {
        return RelaySettings.pairPoolSize;
    }

    public void setPairPoolSize(int newSize) {
        RelaySettings.pairPoolSize = newSize;
    }

    public boolean getPersistPairsSetting() {
        return RelaySettings.persistPairs;
    }

    public void setPersistPairs(boolean bool) {
        RelaySettings.persistPairs = bool;
    }

    public String[] getHostList() {
        return pairedHosts.keySet().toArray(new String[0]);
    }

}

