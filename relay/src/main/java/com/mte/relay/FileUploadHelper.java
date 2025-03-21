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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileUploadHelper {

    // region Class Variables
    private final HttpURLConnection httpConn;
    private final OutputStream outputStream;
    private final MteHelper mteHelper;
    private final String pairId;
    private final RelayStreamResponseListener listener;
    private final RelayStreamCompletionCallback completionCallback;
    private final RetryUploadCallback retryUploadCallback;
    private final int origContentLength;
    private final RelayStreamCallback relayStreamCallback;
    private PipedOutputStream pipedOutputStream;
    private PipedInputStream pipedInputStream;
    // endregion

    // region Constructor
    public FileUploadHelper(RelayFileUploadProperties properties,
                            RelayStreamResponseListener listener,
                            RelayStreamCompletionCallback completionCallback,
                            RetryUploadCallback retryUploadCallback)
            throws IOException, RelayException {
        this.relayStreamCallback = properties.relayStreamCallback;
        this.completionCallback = completionCallback;
        this.pairId = properties.relayOptions.pairId;
        this.mteHelper = properties.mteHelper;
        URL url = new URL(properties.hostUrl + properties.route);
        this.listener = listener;
        this.retryUploadCallback = retryUploadCallback;
        origContentLength = getContentLengthHeader(properties.origHeaders);
        int relayContentLength = origContentLength + getEncryptFinishBytes();
        Map<String, String> origHeaders = properties.origHeaders;
        EncodeResult encodedHeadersResult = NetworkHeaderHelper.processRequestHeaders(mteHelper,
                pairId,
                properties.headersToEncrypt,
                origHeaders);
        httpConn = (HttpURLConnection) url.openConnection();
        httpConn.setUseCaches(false);
        httpConn.setDoOutput(true); // indicates POST method
        httpConn.setDoInput(true);
        for (Map.Entry<String, String> entry : origHeaders.entrySet()) {
            httpConn.setRequestProperty(entry.getKey(), entry.getValue());
        }
        httpConn.setRequestProperty("Content-Length", String.valueOf(relayContentLength));
        httpConn.setRequestProperty("x-mte-relay-eh", encodedHeadersResult.encodedStr);
        httpConn.setRequestProperty("x-mte-relay", RelayOptions.formatMteRelayHeader(properties.relayOptions));

        outputStream = httpConn.getOutputStream();
    }
    // endregion

    // region Public Methods
    public void encryptAndSend(StoreStatesCallback callback) throws IOException {

        // Start by calling StartEncrypt
        mteHelper.startEncrypt(pairId);
        getPipedStreams();

        // Encrypt File Bytes in chunks
        Thread encryptThread = encryptStream();

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
    // endregion

    // region Private Methods
    private int getContentLengthHeader(Map<String, String> origHeaders) {
        String contentLengthValue = null;

        for (Map.Entry<String, String> entry : origHeaders.entrySet()) {
            if (entry.getKey().equalsIgnoreCase("Content-Length")) {
                contentLengthValue = entry.getValue();
                break;
            }
        }

        int origContentLength = 0;
        if (contentLengthValue != null) {
            try {
                origContentLength = Integer.parseInt(contentLengthValue);
            } catch (NumberFormatException e) {
                throw new RelayException("FileUploadHelper", "Invalid Content-Length value: '" + contentLengthValue + "'");
            }
        }
        return origContentLength;
    }

    private Thread encryptStream() {
        Thread encryptThread = new Thread(() -> {
            try {
                byte[] buffer = new byte[RelaySettings.streamChunkSize];
                int bytesRead;
                int totalBytesRead = 0;

                while ((bytesRead = pipedInputStream.read(buffer)) != -1) {
                    totalBytesRead += bytesRead;
                    mteHelper.encryptChunk(pairId, buffer, bytesRead);
                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush();
                    completionCallback.onProgressUpdate(totalBytesRead, origContentLength);
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
        return encryptThread;
    }

    private void getPipedStreams() throws IOException {
        pipedOutputStream = new PipedOutputStream();
        pipedInputStream = new PipedInputStream(pipedOutputStream);
    }

    private int getEncryptFinishBytes() {
        return mteHelper.getEncryptFinishBytes();
    }

    private void getResponse(StoreStatesCallback callback) throws IOException, MteException {

        Map<String, List<String>> processedHeaders = new HashMap<>();

        int status = httpConn.getResponseCode();
        retryUploadCallback.onCompletion(status, listener);

        if (status == HttpURLConnection.HTTP_OK) {
            try {
                RelayOptions responseRelayOptions = NetworkHeaderHelper.getRelayHeaderValues(httpConn);
                String responsePairId = responseRelayOptions.pairId;

                processedHeaders = new HashMap<>(httpConn.getHeaderFields());
                String ehHeader = httpConn.getHeaderField(Constants.X_MTE_RELAY_EH_KEY);
                NetworkHeaderHelper.processResponseHeaders(mteHelper, responsePairId, processedHeaders, ehHeader);

                InputStream inputStream = httpConn.getInputStream();
                StringBuilder sb = new StringBuilder();
                byte[] buffer = new byte[1024];
                mteHelper.startDecrypt(responsePairId);
                int bytesRead;
                String charset = "UTF-8";
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
                listener.relayStreamResponse(
                        true,
                        sb.toString(),
                        null,
                        processedHeaders);
                callback.onCallback();
            } catch ( MteException e) {
                throw new RelayException("RelayFileUploadHelper", "Unable to convert response to JSON. Exception: " + e);
            }
            httpConn.disconnect();
        } else {
            listener.relayStreamResponse(
                    false,
                    null,
                    "Server returned non-OK status: " + status,
                    processedHeaders);
        }
    }
    // endregion

}
