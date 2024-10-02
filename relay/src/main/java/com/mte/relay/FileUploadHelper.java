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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FileUploadHelper {

    private final String hostUrl;
    private final String route;

    private URL url;
    private final HttpURLConnection httpConn;
    private String charset = "UTF-8";
    private final OutputStream outputStream;
    private final MteHelper mteHelper;
    private final String pairId;
    private final RelayResponseListener listener;
    private final RelayStreamCallback relayStreamCallback;
    private PipedOutputStream pipedOutputStream;
    private PipedInputStream pipedInputStream;

    public FileUploadHelper(RelayFileUploadProperties properties, RelayResponseListener listener) throws IOException {

        this.relayStreamCallback = properties.relayStreamCallback;
        File fileToUpload = properties.fileToUpload;
        // Check that file size is less than 2 gig with a little room for FinishEncrypt bytes
        if (fileToUpload.length() > 2147480000) {
            throw new RelayException("RelayFileUploadHelper", "File to upload too large.");
        }

        this.hostUrl = properties.hostUrl;
        this.route = properties.route;
        this.pairId = properties.relayOptions.pairId;
        this.mteHelper = properties.mteHelper;
        this.url = encodeRoute();
        this.listener = listener;

        int origContentLength = Integer.parseInt(properties.origHeaders.get("Content-Length"));
        int relayContentLength = origContentLength + getEncryptFinishBytes();
        EncodeResult encodedHeadersResult = NetworkHeaderHelper.processRequestHeaders(mteHelper,
                pairId,
                properties.headersToEncrypt,
                properties.origHeaders);

        httpConn = (HttpURLConnection) url.openConnection();
        httpConn.setUseCaches(false);
        httpConn.setDoOutput(true); // indicates POST method
        httpConn.setDoInput(true);
        httpConn.setRequestProperty("Content-Length", String.valueOf(relayContentLength));
        httpConn.setRequestProperty("x-mte-relay-eh", encodedHeadersResult.encodedStr);
        httpConn.setRequestProperty("x-mte-relay", RelayOptions.formatMteRelayHeader(properties.relayOptions));
        outputStream = httpConn.getOutputStream();
    }

    private URL encodeRoute() throws UnsupportedEncodingException, MalformedURLException {
        EncodeResult encodeResult = mteHelper.encode(pairId, route);
        encodeResult.encodedStr = URLEncoder.encode(encodeResult.encodedStr, charset);
        String urlStr = hostUrl + encodeResult.encodedStr;
        return new URL(urlStr);
    }

    public void encryptAndSend(StoreStatesCallback callback) throws IOException {

        // Start by calling StartEncrypt
        mteHelper.startEncrypt(pairId);
        getPipedStreams();

        // Start thread to encrypt File Bytes in chunks
        Thread encryptThread = new Thread(() -> {
            try {
                byte[] buffer = new byte[RelaySettings.uploadChunkSize];
                int bytesRead;

                while ((bytesRead = pipedInputStream.read(buffer)) != -1) {
                    mteHelper.encryptChunk(pairId, buffer, bytesRead);
                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush();
                }

                // Now, write Finish Encrypt Bytes to Output Stream
                EncodeResult finishEncryptResult = mteHelper.finishEncrypt(pairId);
                outputStream.write(finishEncryptResult.encodedBytes);
                outputStream.flush();

            } catch (IOException e) {
                String threadName = Thread.currentThread().getName();
                throw new RelayException(this.getClass().getSimpleName(),
                        "Exception in " + threadName + ". Exception: " + e.getMessage());
            }
        });
        encryptThread.start();

        Thread readFileThread = new Thread(() -> {
            relayStreamCallback.getRequestBodyStream(pipedOutputStream);
            try {
                // Pause this thread until the encryptThread is finished, to keep the OutputStream open.
                encryptThread.join();
            } catch (InterruptedException e) {
                String threadName = Thread.currentThread().getName();
                throw new RelayException(this.getClass().getSimpleName(),
                        "Exception in " + threadName + ". Exception: " + e.getMessage());
            }
        });
        readFileThread.start();

        // Pause calling thread until encryptThread is complete
        try {
            encryptThread.join();
        } catch (InterruptedException e) {
            String threadName = Thread.currentThread().getName();
            throw new RelayException(this.getClass().getSimpleName(),
                    "Exception in " + threadName + ". Exception: " + e.getMessage());
        }

        getResponse(callback);
        pipedOutputStream.close(); // Closes the pipedInputStream too.
        outputStream.close();
    }

    private void getPipedStreams() throws IOException {
        pipedOutputStream = new PipedOutputStream();
        pipedInputStream = new PipedInputStream(pipedOutputStream);
    }

    private int getEncryptFinishBytes() {
        return mteHelper.getEncryptFinishBytes();
    }

    public void getResponse(StoreStatesCallback callback) throws IOException {

        int status = httpConn.getResponseCode();
        Map<String, List<String>> processedHeaders = Collections.emptyMap();
        if (status == HttpURLConnection.HTTP_OK) {
            RelayOptions responseRelayOptions = NetworkHeaderHelper.getRelayHeaderValues(httpConn);
            String responsePairId = responseRelayOptions.pairId;
            processedHeaders = NetworkHeaderHelper.processHttpConnResponseHeaders(httpConn,
                    mteHelper,
                    responsePairId);
            InputStream inputStream = httpConn.getInputStream();
            StringBuilder sb = new StringBuilder();
            byte[] buffer = new byte[1024];
            mteHelper.startDecrypt(responsePairId);
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byte[] decrypted = new byte[bytesRead];
                int bytesDecrypted = mteHelper.decryptChunk(responsePairId,
                        buffer,
                        0,
                        bytesRead,
                        decrypted,
                        0);
                sb.append(new String(decrypted, charset), 0, bytesDecrypted);
            }
            DecodeResult finishEncryptResult = mteHelper.finishDecrypt(responsePairId);
            if (finishEncryptResult.decodedBytes != null &&
                    finishEncryptResult.decodedBytes.length > 0) {
                sb.append(new String(finishEncryptResult.decodedBytes, charset));
            }
            try {
                listener.onResponse(new JSONObject(sb.toString()), processedHeaders);
                callback.onCallback();
            } catch (JSONException e) {
                throw new RelayException("RelayFileUploadHelper", "Unable to convert response to JSON. Exception: " + e);
            }
            httpConn.disconnect();
        } else {
            listener.onError("Server returned non-OK status: " + status, processedHeaders);
        }
    }

    static String getRandomStr(Integer length) {
        String AlphaNumericString = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvxyz23456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index
                    = (int) (AlphaNumericString.length()
                    * Math.random());
            sb.append(AlphaNumericString
                    .charAt(index));
        }
        return sb.toString();
    }
}
