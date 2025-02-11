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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileDownloadHelper {

    private final HttpURLConnection httpConn;
    private final MteHelper mteHelper;
    private final String pairId;
    private String responsePairId;
    private final String downloadPath;
    private final RelayDataTaskListener listener;

    public FileDownloadHelper(FileDownloadProperties properties, RelayDataTaskListener listener) throws IOException {

        this.pairId = properties.relayOptions.pairId;
        this.mteHelper = properties.mteHelper;
        this.downloadPath = properties.downloadPath;
        this.listener = listener;

        URL url = new URL(properties.hostUrl + properties.route);
        EncodeResult encodedHeadersResult = encodeHeaders(properties.headersToEncrypt, properties.origHeaders);
        httpConn = (HttpURLConnection) url.openConnection();
        httpConn.setUseCaches(false);
        httpConn.setDoOutput(false); // indicates GET method
        httpConn.setDoInput(true);
        httpConn.setRequestProperty("x-mte-relay-eh", encodedHeadersResult.encodedStr);
        httpConn.setRequestProperty("x-mte-relay", RelayOptions.formatMteRelayHeader(properties.relayOptions));
    }

    public void downloadFile(StoreStatesCallback callback) {
        Thread networkThread = new Thread(() -> {
            Map<String, List<String>> processedHeaders = Collections.emptyMap();
            try {
                if (httpConn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    RelayOptions responseRelayOptions = NetworkHeaderHelper.getRelayHeaderValues(httpConn);
                    responsePairId = responseRelayOptions.pairId;
                    processedHeaders = NetworkHeaderHelper.processHttpConnResponseHeaders(httpConn,
                            mteHelper,
                            responsePairId);
                    processFileDownloadStream(downloadPath);

                    JSONObject jsonResponse = getJsonResponse(downloadPath);
                    listener.onResponse(jsonResponse, processedHeaders);
                    callback.onCallback();
                } else {
                    listener.onError(httpConn.getResponseMessage(),processedHeaders);
                    callback.onCallback();
                }
            } catch (IOException | JSONException | MteException e) {
                listener.onError(e.getMessage(), processedHeaders);
            } finally {
                httpConn.disconnect();
            }
        });
        networkThread.start();
    }

    private void processFileDownloadStream(String downloadPath) throws IOException {
        InputStream inputStream = httpConn.getInputStream();
        FileOutputStream outputStream = new FileOutputStream(downloadPath);
        byte[] buffer = new byte[RelaySettings.streamChunkSize];
        int bytesRead;
        mteHelper.startDecrypt(responsePairId);
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            byte[] buf = Arrays.copyOfRange(buffer, 0, bytesRead);
            DecodeResult decodeChunkResult = mteHelper.decryptChunk(responsePairId, buf);
            outputStream.write(decodeChunkResult.decodedBytes, 0, decodeChunkResult.decodedBytes.length);
        }
        DecodeResult finishDecryptResult = mteHelper.finishDecrypt(responsePairId);
        if (finishDecryptResult.decodedBytes != null && finishDecryptResult.decodedBytes.length > 0) {
            outputStream.write(finishDecryptResult.decodedBytes, 0, finishDecryptResult.decodedBytes.length);
        }
    }

    private JSONObject getJsonResponse(String downloadPath) throws JSONException, IOException {
        Path path = FileSystems.getDefault().getPath(downloadPath);
        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put("Response", httpConn.getResponseMessage());
        jsonResponse.put("Http Response Code", httpConn.getResponseCode());
        jsonResponse.put("File Size", Files.size(path));
        jsonResponse.put("Download Location", downloadPath);
        return jsonResponse;
    }

    private EncodeResult encodeHeaders(String[] headersToEncode, Map<String, String> origHeaders) {
        Map<String, String> ctHeader = new HashMap<>();

        // encode original headers as necessary
        List<String> headersToEncodeList = Arrays.asList(headersToEncode);
        for (Map.Entry<String, String> origHeader : origHeaders.entrySet()) {
            if (headersToEncodeList.contains(origHeader.getKey())) {
                ctHeader.put(origHeader.getKey(), origHeader.getValue());
            }
        }

        // Remove headers to be encoded from Original Headers Map
        for (Map.Entry<String, String> headerToEncode : ctHeader.entrySet()) {
            origHeaders.remove(headerToEncode.getKey());
        }

        JSONObject headersJson = new JSONObject(ctHeader);
        return mteHelper.encode(pairId, headersJson.toString());
    }
}
