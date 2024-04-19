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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Relay {

    private static String[] relayHosts;
    private static Relay instance;
    private final Map<String, Host> hosts;

    public static Relay getInstance(Context context, String[] relayHosts) throws IOException {
        if (instance == null) {
            instance = new Relay(context, relayHosts);
        }
        return instance;
    }

    private Relay(Context context, String[] relayHosts) throws IOException {
        this.relayHosts = relayHosts;
        if (!MteBase.initLicense(RelaySettings.licenseCompanyName, RelaySettings.licenseKey)) {
            throw new RelayException(getClass().getSimpleName(), "MTE License Check Failed");
        }
        hosts = new HashMap<>();
        for (String host : relayHosts) {
           Host h = new Host(context, host);
           hosts.put(host, h);
        }
    }

    public <T> void addToMteRequestQueue(Request<T> req, RelayResponseListener listener) {
        Host host = hosts.get(resolveHost());
        Objects.requireNonNull(host).sendRequest(req, new RelayResponseListener() {
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

    public void uploadFile(RelayFileRequestProperties reqProperties, String route, RelayResponseListener listener) {
        Host host = hosts.get(resolveHost());
        host.uploadFile(reqProperties, route, listener);
    }

    public void downloadFile(RelayFileRequestProperties reqProperties, RelayResponseListener listener) throws IOException {
        Host host = hosts.get(resolveHost());
        host.downloadFile(reqProperties, listener);
    }

    public String resolveHost() {
        if (relayHosts.length == 1) {
            return relayHosts[0];
        } else {
            // TODO: Logic here to determine which server to use if there are multiple hosts.
            return "";
        }
    }

    public void rePairWithHost(RelayResponseListener listener) {
        Host host = hosts.get(resolveHost());
        Objects.requireNonNull(host).rePairWithHost(listener);
    }

    public static String bytesToDecimal(byte[] bytes) {
        StringBuilder decimal = new StringBuilder();
        for (byte b : bytes)
            decimal.append(Byte.toUnsignedInt(b)).append(", ");
        return decimal.toString();
    }
}

