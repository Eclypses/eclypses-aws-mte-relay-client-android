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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused") // All public methods are called externally
public class Relay {

    // region Class Variables
    private static Relay instance;
    private final Map<String, Host> pairedHosts = new HashMap<>();
    private final Context ctx;
    private final RelayResponseListener relayResponseListener;
    // endregion

    // region Constructors
    public static Relay getInstance(Context context, RelayResponseListener listener) {
        if (instance == null) {
            System.setProperty("LOG_DIR", context.getFilesDir().getAbsolutePath());
            instance = new Relay(context, listener);
        }
        return instance;
    }

    private Relay(Context context, RelayResponseListener listener) {
        LogHelper.setFileLoggingEnabled(true);
        LogHelper.trace("Relay", "Logging initialized.");
        if (!MteBase.initLicense(RelaySettings.licenseCompanyName, RelaySettings.licenseKey)) {
            String errorMessage = "MTE License Check Failed";
            LogHelper.error("Relay", errorMessage);
            throw new RelayException(getClass().getSimpleName(), "MTE License Check Failed");
        }
        LogHelper.info("Relay", "Using Relay Version " + RelaySettings.relayVersion + " and Mte Version " + MteBase.getVersion());
        ctx = context;
        relayResponseListener = listener;
    }
    // endregion

    // region Public Methods
    public <T> void addToMteRequestQueue(Request<T> req, String[] headersToEncrypt, RelayDataTaskListener listener) {
        addToMteRequestQueue(req, headersToEncrypt, null, listener);
    }

    public <T> void addToMteRequestQueue(Request<T> req, String[] headersToEncrypt, String pathnamePrefix, RelayDataTaskListener listener) {
        LogHelper.trace("Relay", "Volley Request added to Queue");
        String relayServerPath = null;
        try {
            URL relayServerUrl = new URL(req.getUrl());
            String protocol = relayServerUrl.getProtocol();
            String authority = relayServerUrl.getAuthority();
            relayServerPath = protocol + "://" + authority;
        } catch (MalformedURLException e) {
            LogHelper.error("Relay", e.getMessage());
            listener.onError(e.getMessage(), null);
        }
        getHost(buildHostUrl(relayServerPath, pathnamePrefix),
                new InstantiateHostCallback() {
                    @Override
                    public void onError(String message) {
                        LogHelper.error("Relay",message);
                        listener.onError(message, null); }

                    @Override
                    public void hostInstantiated(String hostUrl, Host host) {
                        host.sendRequest(req, headersToEncrypt, listener);
                    }
                });
    }

    public void uploadFile(RelayFileRequestProperties reqProperties,
                           String route,
                           RelayStreamResponseListener listener,
                           RelayStreamCompletionCallback completionCallback) {
        uploadFile(reqProperties, route, null, listener, completionCallback);
    }

    public void uploadFile(RelayFileRequestProperties reqProperties,
                           String route,
                           String pathnamePrefix,
                           RelayStreamResponseListener listener,
                           RelayStreamCompletionCallback completionCallback) {
        LogHelper.trace("Relay", "Uploading File");
        try {
            getHost(buildHostUrl(reqProperties.serverPath, pathnamePrefix),
                    new InstantiateHostCallback() {
                        @Override
                        public void onError(String message) {
                            LogHelper.error("Relay", message);
                            listener.relayStreamResponse(
                                false,
                                null,
                                message,
                                null); }

                        @Override
                        public void hostInstantiated(String hostUrl, Host host) {
                            host.uploadFile(reqProperties, route, listener, completionCallback);
                        }
                    });
        } catch (RelayException e) {
            relayResponseListener.onCompletion(false, e.getMessage());
        }
    }

    public void downloadFile(RelayFileRequestProperties reqProperties, RelayStreamResponseListener listener) {
        downloadFile(reqProperties, null, listener);
    }

    public void downloadFile(RelayFileRequestProperties reqProperties, String pathnamePrefix, RelayStreamResponseListener listener) {
        LogHelper.trace("Relay", "Downloading File");
        try {
            getHost(buildHostUrl(reqProperties.serverPath, pathnamePrefix),
                    new InstantiateHostCallback() {
                        @Override
                        public void onError(String message) {
                            LogHelper.error("Relay",message);
                            listener.relayStreamResponse(
                                    false,
                                    null,
                                    message,
                                    null);
                        }

                        @Override
                        public void hostInstantiated(String hostUrl, Host host) {
                            try {
                                host.downloadFile(reqProperties, listener);
                            } catch (IOException e) {
                                listener.relayStreamResponse(
                                        false,
                                        null,
                                        e.getMessage(),
                                        null);
                            }
                        }
                    });
        } catch (RelayException e) {
            LogHelper.error("Relay", e.getMessage());
            relayResponseListener.onCompletion(false, e.getMessage());
        }
    }

    public void rePairWithRelayServer(String serverUrl) {
        rePairWithRelayServer(serverUrl, null);
    }

    public void rePairWithRelayServer(String serverUrl, String pathnamePrefix) {
        LogHelper.trace("Relay", "Repairing with Server");
        try {
            getHost(buildHostUrl(serverUrl, pathnamePrefix),
                    new InstantiateHostCallback() {
                        @Override
                        public void onError(String message) { relayResponseListener.onCompletion(false, message); }

                        @Override
                        public void hostInstantiated(String hostUrl, Host host) {
                            host.rePairWithHost(new InstantiateHostCallback() {
                                @Override
                                public void onError(String message) {
                                    LogHelper.error("Relay", message);
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
        } catch (RelayException e) {
            relayResponseListener.onCompletion(false, e.getMessage());
        }
    }

    public String adjustRelaySettings(String serverUrl,
                                      int newStreamChunkSize,
                                      int newPairPoolSize,
                                      Boolean persistPairs) {
        return adjustRelaySettings(
                serverUrl,
                null,
                newStreamChunkSize,
                newPairPoolSize,
                persistPairs);
    }

    public String adjustRelaySettings(String serverUrl,
                                      String pathnamePrefix,
                                      int newStreamChunkSize,
                                      int newPairPoolSize,
                                      Boolean persistPairs) {
        LogHelper.trace("Relay", "Adjusting Relay Settings");
        String responseMessage = "";
        try {
            serverUrl = buildHostUrl(serverUrl, pathnamePrefix);
        } catch (RelayException e) {
            relayResponseListener.onCompletion(false, e.getMessage());
            responseMessage = e.getMessage();
            LogHelper.error("Relay", responseMessage);
            return responseMessage;
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
            rePairWithRelayServer(serverUrl, pathnamePrefix);
            responseMessage = responseMessage + "\nAlso, Relay was Re-Paired with " + serverUrl ;
        }
        LogHelper.info("Relay", responseMessage);
        return responseMessage;
    }

    public static void setFileLoggingEnabled(Boolean isEnabled) {
        LogHelper.trace("Relay", "Setting FileLogging to " + isEnabled);
        LogHelper.setFileLoggingEnabled(isEnabled);
    }

    public static String readLogFile() {
        LogHelper.trace("Relay", "Reading Log File");
        return LogHelper.readLogFileContents();
    }

    public static void clearLogFile() {
        LogHelper.trace("Relay", "Clearing log file");
        LogHelper.clearLogFileContents();
    }

    public String[] getHostList() {
        LogHelper.trace("Relay", "getting Host List");
        return pairedHosts.keySet().toArray(new String[0]);
    }
    // endregion

    // region Private Methods
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

    private int getStreamChunkSizeSetting() {
        return RelaySettings.streamChunkSize;
    }

    private int getPairPoolSizeSetting() {
        return RelaySettings.pairPoolSize;
    }

    private boolean getPersistPairsSetting() {
        return RelaySettings.persistPairs;
    }

    private void setStreamChunkSize(int newSize) {
        RelaySettings.streamChunkSize = newSize;
    }

    private void setPairPoolSize(int newSize) {
        RelaySettings.pairPoolSize = newSize;
    }

    private void setPersistPairs(boolean bool) {
        RelaySettings.persistPairs = bool;
    }
    // endregion

    // region Static Methods
    static String buildHostUrl(String serverUrl, String pathnamePrefix) {
        if (serverUrl == null || serverUrl.isEmpty()) {
            String errorMessage = "ServerUrl must be a valid String path";
            LogHelper.error("Relay", errorMessage);
            throw new RelayException("Relay", errorMessage);
        }
        if (
                pathnamePrefix != null &&
                        !serverUrl.endsWith(pathnamePrefix)) {
            if (serverUrl.endsWith("/")) {
                serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
            }
            return serverUrl + "/" + pathnamePrefix;
        } else {
            return serverUrl;
        }
    }
    // endregion
}

