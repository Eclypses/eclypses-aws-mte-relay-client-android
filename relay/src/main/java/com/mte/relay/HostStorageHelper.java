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

    import org.json.JSONException;
    import org.json.JSONObject;

    import java.io.File;
    import java.io.FileInputStream;
    import java.io.FileOutputStream;
    import java.io.IOException;
    import java.security.KeyStoreException;
    import java.security.NoSuchAlgorithmException;
    import java.util.HashMap;
    import java.util.Map;
    import java.util.Objects;

    import javax.crypto.SecretKey;

    public class HostStorageHelper {

        Context ctx;
        SecretKey secretKey;
        String  host, encryptedHostFilename;
        boolean foundStoredHost = false;
        Map<String, String> storedHosts = new HashMap<>(1);
        boolean storageInstantiated = false;
        KeyHelper keyHelper;

        public HostStorageHelper(Context ctx, String host, HostStorageHelperCallback callback) throws IOException {
            this.ctx = ctx;
            this.host = host;
            this.encryptedHostFilename = host + "-encryptedHostFile";
            Thread instantiateStorage = new Thread(this::instantiateKeyHelper);
            instantiateStorage.start();

            Thread loadStoredPairsThread = new Thread(() -> {
                try {
                    loadStoredHosts(callback);
                } catch (InterruptedException e) {
                    throw new RelayException(getClass().getSimpleName(),
                            "Load Stored Host Exception: Error: " + e.getMessage());
                }
            });
            loadStoredPairsThread.start();
        }

        private void instantiateKeyHelper() {
            try {
                keyHelper = new KeyHelper(host);
                confirmStoredKey();
            } catch (
                    IOException | NoSuchAlgorithmException | KeyStoreException e) {
                throw new RelayException(getClass().getSimpleName(),
                        "instantiateKeyHelper Exception: Error: " + e.getMessage());
            }
        }

        synchronized private void confirmStoredKey() throws IOException, NoSuchAlgorithmException, KeyStoreException {

            Boolean hasSecretKey = keyHelper.hasSecretKey();
            if (!hasSecretKey) {
                keyHelper.generateSecretKey();
            }
            storageInstantiated = true;
            notify();
        }

        synchronized private void loadStoredHosts(HostStorageHelperCallback callback) throws InterruptedException {
            while (!storageInstantiated) {
                wait();
            }
            try {
                String storedHostStr = readHostFromFile();
                if (Objects.equals(storedHostStr, "")) {
                    callback.noStoredPairs();
                    return;
                }
                JSONObject storedPairs = new JSONObject(storedHostStr);
                String clientId = storedPairs.getString("clientId");
                String pairMapStates = storedPairs.optString("pairMapStates", "");
                if (pairMapStates.isEmpty()) {
                    callback.foundClientId(clientId);
                } else {
                    storedHosts.put(host, storedHostStr);
                    foundStoredHost = true;
                    callback.foundStoredPairs(storedHosts.get(host));
                }
            } catch (JSONException e) {
                callback.onError(e.getMessage());
            }
        }

        String getStoredPairsForHost(String host) {
            return storedHosts.get(host);
        }

        void removeStoredHost() throws JSONException {
            String storedHostStr = readHostFromFile();

            // If there is no file for this host, just return.
            if (Objects.equals(storedHostStr, "")) {
                return;
            }
            JSONObject stateToStore = new JSONObject(storedHostStr);
            stateToStore.put("pairMapStates", "");
            saveHostToFile(stateToStore.toString());
        }

        public void saveHostToFile(String data) {
            try {
                // Open a file output stream in private mode (MODE_PRIVATE)
                FileOutputStream fileOutputStream = ctx.openFileOutput(encryptedHostFilename,
                                                                        Context.MODE_PRIVATE);

                byte[] encryptedHost = keyHelper.encryptString(data);
                fileOutputStream.write(encryptedHost);

                // Close the file output stream
                fileOutputStream.close();
            } catch (Exception e) {
                throw new RelayException(getClass().getSimpleName(),
                        "Unable to store Host: Error: " + e.getMessage());
            }
        }

        public String readHostFromFile() {
            try {
                // Open a file input stream
                FileInputStream fileInputStream = ctx.openFileInput(encryptedHostFilename);

                // Read the data from the file
                byte[] fileBytes = new byte[fileInputStream.available()];
                fileInputStream.read(fileBytes);
                String decrypted = keyHelper.decryptBytes(fileBytes);

                // Close the file input stream
                fileInputStream.close();
                return decrypted;
            } catch (Exception e) {
                return "";
            }
        }

        public void deleteStoredHost() {
            File file = new File(ctx.getFilesDir(), encryptedHostFilename);

            if (file.exists()) {
                if (!file.delete()) {
                    throw new RelayException(getClass().getSimpleName(),
                            "Unable to remove stored Host. ");
                }
            } else {
                if (BuildConfig.DEBUG) {
                    Log.i("MTE", "Found no stored host file " + encryptedHostFilename + " to remove.");
                }

            }
        }

    }
