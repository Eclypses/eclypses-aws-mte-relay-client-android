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

import com.android.volley.Request;
import com.eclypses.mte.MteBase;

import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Relay {

    private static Relay instance;
    private final Map<String, Host> hosts;

    public static Relay getInstance(Context context, String[] relayHosts, InstantiateRelayCallback callback) throws IOException {
        if (instance == null) {
            instance = new Relay(context, relayHosts, callback);
        }
        return instance;
    }

    private Relay(Context context, String[] relayHosts, InstantiateRelayCallback callback) throws IOException {
        if (!MteBase.initLicense(Settings.licenseCompanyName, Settings.licenseKey)) {
            throw new RelayException(getClass().getSimpleName(), "MTE License Check Failed");
        }
        hosts = new HashMap<>();
        for (String host : relayHosts) {
            Host h = new Host(context, host, callback);
            hosts.put(host, h);
        }
    }

    public <T> void addToMteRequestQueue(String serverUrl, Request<T> req, String[] headersToEncrypt, RelayResponseListener listener) {
        Host host = getHost(serverUrl, listener);
        Objects.requireNonNull(host).sendRequest(req, headersToEncrypt, new RelayResponseListener() {
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

    public void uploadFile(String serverUrl, RelayFileRequestProperties reqProperties, String route, RelayResponseListener listener) {
        Host host = getHost(serverUrl, listener);
        host.uploadFile(reqProperties, route, listener);
    }

    public void downloadFile(String serverUrl, RelayFileRequestProperties reqProperties, RelayResponseListener listener) throws IOException {
        Host host = getHost(serverUrl, listener);
        host.downloadFile(reqProperties, listener);
    }

    private Host getHost(String hostUrl, RelayResponseListener listener) {
        Host host = hosts.get(hostUrl);
        if (host == null) {
            listener.onError("Server Url " + hostUrl + " not found in list of Relay Servers");
        }
        return host;
    }

    public void rePairWithHost(String serverUrl, RelayResponseListener listener) {
        Host host = getHost(serverUrl, listener);
        Objects.requireNonNull(host).rePairWithHost(listener);
    }

}

