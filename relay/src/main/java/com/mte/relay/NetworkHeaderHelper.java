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

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class NetworkHeaderHelper {

    // region Public Methods
    public static EncodeResult processRequestHeaders(MteHelper mteHelper,
                                                     String pairId,
                                                     String[] headersToEncode,
                                                     Map<String, String> origHeaders) {
        return processRequestHeaders((RelayCodec) mteHelper, pairId, headersToEncode, origHeaders);
    }

    public static EncodeResult processRequestHeaders(RelayCodec relayCodec,
                                                     String pairId,
                                                     String[] headersToEncode,
                                                     Map<String, String> origHeaders) {
        if (headersToEncode == null || origHeaders == null || origHeaders.isEmpty()) {
            return new EncodeResult(pairId, "");
        }

        HashSet<String> headersToEncodeSet = new HashSet<>(Arrays.asList(headersToEncode));

        Map<String, String> encodedHeaders = new HashMap<>();

        Iterator<Map.Entry<String, String>> iterator = origHeaders.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            String key = entry.getKey();

            if (key.equalsIgnoreCase("content-type") || headersToEncodeSet.contains(key)) {
                encodedHeaders.put(key, entry.getValue());
                iterator.remove(); // Remove after encoding to prevent duplication
            }
        }
        JSONObject headersJson = new JSONObject(encodedHeaders);
        return relayCodec.encode(pairId, headersJson.toString());
    }

    public static RelayOptions getRelayHeaderValues(HttpURLConnection httpConn) {
        String relayHeaderStr = httpConn.getHeaderField(Constants.X_MTE_RELAY_KEY);
        if (relayHeaderStr == null) {
            throw new RelayException("MteRelayHeader", "No x-mte-relay response header.");
        }
        RelayOptions responseRelayOptions = RelayOptions.parseMteRelayHeader(relayHeaderStr);
        if (responseRelayOptions != null) {
            if (responseRelayOptions.pairId == null || responseRelayOptions.pairId.isEmpty()) {
                throw new RelayException("MteRelayHeader",
                        "No pairId in x-mte-relay response header.");
            }
        }
        return responseRelayOptions;
    }

    public static void processResponseHeaders(MteHelper mteHelper, String responsePairId, Map<String, List<String>> updatedResponseHeaders, String ehHeader) throws MteException {
        processResponseHeaders((RelayCodec) mteHelper, responsePairId, updatedResponseHeaders, ehHeader);
    }

    public static void processResponseHeaders(RelayCodec relayCodec, String responsePairId, Map<String, List<String>> updatedResponseHeaders, String ehHeader) throws MteException {
        if (ehHeader != null && !ehHeader.isEmpty()) {
            DecodeResult decodeResult = relayCodec.decode(responsePairId, ehHeader);
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
        updatedResponseHeaders.remove(Constants.X_MTE_RELAY_EH_KEY);
        updatedResponseHeaders.remove(Constants.X_MTE_RELAY_KEY);
        List<String> headerNameList = new ArrayList<>();
        headerNameList.add("Access-Control-Allow-Headers");
        headerNameList.add("access-control-expose-headers");
        removeAccessControlMteHeaderStrings(headerNameList, updatedResponseHeaders);
    }
    // endregion

    //region Private Methods
    private static void removeAccessControlMteHeaderStrings(List<String> headerNameList,
                                                            Map<String, List<String>> updatedResponseHeaders) {
        for (String headerName : headerNameList) {
            String actualKey = null;

            // Find the actual key in the map (case-insensitive)
            for (String key : updatedResponseHeaders.keySet()) {
                if (key != null && key.equalsIgnoreCase(headerName)) {
                    actualKey = key;
                    break;
                }
            }

            // If the key is not found, continue
            if (actualKey == null) {
                continue;
            }

            List<String> list = updatedResponseHeaders.get(actualKey);
            if (list == null || list.isEmpty()) {
                continue;
            }

            // Split values and normalize them
            List<String> accessControlHeadersList = new ArrayList<>();
            for (String s : list.get(0).split(", ")) {
                accessControlHeadersList.add(s.trim().toLowerCase());
            }

            // Remove the specific headers in a case-insensitive way
            accessControlHeadersList.removeIf(element ->
                    element.equalsIgnoreCase(RelayHeaderType.relayHeader) ||
                            element.equalsIgnoreCase(RelayHeaderType.encryptedHeaders));

            // If the header values are now empty, remove the entire header
            if (accessControlHeadersList.isEmpty()) {
                updatedResponseHeaders.remove(actualKey);
            } else {
                // Otherwise, update the header with the modified values
                updatedResponseHeaders.put(actualKey,
                        new ArrayList<>(Collections.singleton(String.join(", ", accessControlHeadersList))));
            }
        }
    }
    // endregion
}
