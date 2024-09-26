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

import com.android.volley.BuildConfig;
import com.android.volley.Request;
import com.eclypses.mte.MteBase;

import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Relay {

    private static Relay instance;
    private final Map<String, Host> pairedHosts = new HashMap<>();
    private final Context ctx;

    public static Relay getInstance(Context context) {
        if (instance == null) {
            instance = new Relay(context);
        }
        if (BuildConfig.DEBUG) {
            Log.d("MTE", "Relay Instantiated.");
        }
        return instance;
    }

    private Relay(Context context) {
        if (!MteBase.initLicense(RelaySettings.licenseCompanyName, RelaySettings.licenseKey)) {
            throw new RelayException(getClass().getSimpleName(), "MTE License Check Failed");
        }
        ctx = context;
    }

    public <T> void addToMteRequestQueue(Request<T> req, String[] headersToEncrypt, RelayResponseListener listener) {
        String relayServerPath = null;
        try {
            URL relayServerUrl = new URL(req.getUrl());
            String protocol = relayServerUrl.getProtocol();
            String authority = relayServerUrl.getAuthority();
            relayServerPath = protocol + "://" + authority + "/";
        } catch (MalformedURLException e) {
            listener.onError(e.getMessage());
        }
        getHost(relayServerPath, listener, new InstantiateHostCallback() {
            @Override
            public void onError(String message) { listener.onError(message); }

            @Override
            public void hostInstantiated(String hostUrl, Host host) {
                host.sendRequest(req, headersToEncrypt, new RelayResponseListener() {
                    @Override
                    public void onError(String message) {
                        listener.onError(message);
                    }

                    @Override
                    public void onResponse(byte[] responseBytes, Map<String, List<String>> responseHeaders) {
                        listener.onResponse(responseBytes, responseHeaders);
                    }

                    @Override
                    public void onResponse(JSONObject responseJson, Map<String, List<String>> responseHeaders) {
                        listener.onResponse(responseJson, responseHeaders);
                    }
                });
            }
        });
    }

    public void uploadFile(RelayFileRequestProperties reqProperties, String route, RelayResponseListener listener) {

        getHost(reqProperties.serverPath, listener, new InstantiateHostCallback() {
            @Override
            public void onError(String message) { listener.onError(message); }

            @Override
            public void hostInstantiated(String hostUrl, Host host) {
                host.uploadFile(reqProperties, route, listener);
            }
        });

    }

    public void downloadFile(RelayFileRequestProperties reqProperties, RelayResponseListener listener) {
        getHost(reqProperties.serverPath, listener, new InstantiateHostCallback() {
            @Override
            public void onError(String message) { listener.onError(message); }

            @Override
            public void hostInstantiated(String hostUrl, Host host) {
                try {
                    host.downloadFile(reqProperties, listener);
                } catch (IOException e) {
                    listener.onError(e.getMessage());
                }
            }
        });
    }

    private void getHost(String hostUrl, RelayResponseListener listener, InstantiateHostCallback callback) {
        final Host[] hostToReturn = { pairedHosts.get(hostUrl) };
        if (hostToReturn[0] == null) {
            new Host(ctx, hostUrl, new InstantiateHostCallback() {

                @Override
                public void onError(String message) {
                    listener.onError(message);
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

    public void rePairWithRelayServer(String serverUrl, RelayResponseListener listener) {
        getHost(serverUrl, listener, new InstantiateHostCallback() {
            @Override
            public void onError(String message) { listener.onError(message); }

            @Override
            public void hostInstantiated(String hostUrl, Host host) {
                host.rePairWithHost(listener);
            }
        });
    }

    public void setPersistPairs(boolean bool) {
        RelaySettings.persistPairs = bool;
    }

    public String[] getHostList() {
        return pairedHosts.keySet().toArray(new String[0]);
    }

}

