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

import android.util.Log;

import com.android.volley.Header;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class NetworkHeaderHelper {

    public static EncodeResult processRequestHeaders(MteHelper mteHelper,
                                                     String pairId,
                                                     String[] headersToEncode,
                                                     Map<String, String> origHeaders) {

        Map<String, String> ctHeader = new HashMap<>();

        // encode original headers as necessary
        List<String> headersToEncodeList = Arrays.asList(headersToEncode);
        for (Map.Entry<String, String> origHeader : origHeaders.entrySet()) {
            // Content-Type always gets encrypted if it exists
            if (origHeader.getKey().equals("content-type") ||
                    origHeader.getKey().equals("Content-Type")) {
                ctHeader.put(origHeader.getKey(), origHeader.getValue());
                break;
            }
            if (headersToEncodeList.contains(origHeader.getKey())) {
                ctHeader.put(origHeader.getKey(), origHeader.getValue());
            }
        }

        // Remove headers to be encoded from Original Headers Map
        for (Map.Entry<String, String> headerToEncode : ctHeader.entrySet()) {
            origHeaders.remove(headerToEncode.getKey());
        }

        JSONObject headersJson = new JSONObject(ctHeader);
        EncodeResult encodedHeadersResult = mteHelper.encode(pairId, headersJson.toString());
        return encodedHeadersResult;
    }

    public static RelayOptions getRelayHeaderValues(HttpURLConnection httpConn) {
        String relayHeaderStr = httpConn.getHeaderField("x-mte-relay");
        if (relayHeaderStr == null) {
            throw new RelayException("MteRelayHeader", "No x-mte-relay response header.");
        }
        RelayOptions responseRelayOptions = RelayOptions.parseMteRelayHeader(relayHeaderStr);
        if (responseRelayOptions.pairId == null || responseRelayOptions.pairId == "") {
            throw new RelayException("MteRelayHeader",
                    "No pairId in x-mte-relay response header.");
        }
        return responseRelayOptions;
    }

    static Map<String, List<String>> processHttpConnResponseHeaders(HttpURLConnection httpConn, MteHelper mteHelper, String responsePairId) throws IOException {
        Map<String, List<String>> headerMap = httpConn.getHeaderFields();
        Map<String, List<String>> updatedResponseHeaders = new HashMap<>(headerMap);
        int status = httpConn.getResponseCode();
        if (status == HttpURLConnection.HTTP_OK) {
            // Decrypt encrypted headers
            String ehHeader = httpConn.getHeaderField("x-mte-relay-eh");
            processResponseHeaders(mteHelper, responsePairId, updatedResponseHeaders, ehHeader);
        }
        return updatedResponseHeaders;
    }

    static Map<String, List<String>> processVolleyResponseHeaders(RelayHeaders relayHeaders, MteHelper mteHelper) throws IOException {
        Map<String, List<String>> updatedResponseHeaders = new HashMap<>();
        for (Header header : relayHeaders.responseHeaderList) {
            updatedResponseHeaders.put(header.getName(), Collections.singletonList(header.getValue()));
        }
        processResponseHeaders(mteHelper, relayHeaders.pairId, updatedResponseHeaders, relayHeaders.encryptedDecryptedHeaders);
        return updatedResponseHeaders;
    }

    private static void processResponseHeaders(MteHelper mteHelper, String responsePairId, Map<String, List<String>> updatedResponseHeaders, String ehHeader) {
        if (ehHeader != null && !ehHeader.isEmpty()) {
            DecodeResult decodeResult = mteHelper.decode(responsePairId, ehHeader);
            try {
                JSONObject decodedHeaders = new JSONObject(decodeResult.decodedStr);
                Iterator<String> keysIterator = decodedHeaders.keys();
                while (keysIterator.hasNext()) {
                    String key = keysIterator.next();
                    String value = decodedHeaders.getString(key);
                    updatedResponseHeaders.put(key, Collections.singletonList(value));
                }
            } catch (JSONException e) {
                throw new RelayException("MteRelayHeader",
                        "Unable to create JSONObject from x-mte-relay response header str.");
            }
        }
        updatedResponseHeaders.remove("x-mte-relay-eh");
        updatedResponseHeaders.remove("x-mte-relay");
        List<String> headerNameList = new ArrayList<>();
        headerNameList.add("access-control-allow-headers");
        headerNameList.add("access-control-expose-headers");
        removeAccessControlMteHeaderStrings(headerNameList, updatedResponseHeaders);
    }

    private static void removeAccessControlMteHeaderStrings(List<String> headerNameList,
                                                            Map<String, List<String>> updatedResponseHeaders) {
        for (String headerName : headerNameList) {
            List<String> list = updatedResponseHeaders.get(headerName);
            if (list == null) {
                return;
            }
            String[] splitString = list.get(0).split(", ");
            ArrayList<String> accessControlHeadersList = new ArrayList<>(Arrays.asList(splitString));

            accessControlHeadersList.removeIf(element -> element.equals(RelayHeaderType.relayHeader));
            accessControlHeadersList.removeIf(element -> element.equals(RelayHeaderType.encryptedHeaders));

            String str = String.join(", ", accessControlHeadersList);
            List<String> updatedList = new ArrayList<>(Collections.singleton(str));

            updatedResponseHeaders.remove(headerName);
            updatedResponseHeaders.put(headerName, updatedList);
        }
    }
}
