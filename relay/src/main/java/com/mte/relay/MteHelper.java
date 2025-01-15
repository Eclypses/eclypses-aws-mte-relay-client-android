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

import android.util.Base64;
import android.util.Log;

import com.eclypses.mte.MteBase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MteHelper {

    public Map<String, Pair> pairMap;

    int nextPair = 0;

    synchronized public boolean refillPairMap(String storedPairs) {
        JSONArray storedPairsArray;
        try {
            storedPairsArray = new JSONArray(storedPairs);
        } catch (JSONException e) {
            throw new RelayException(getClass().getSimpleName(),
                    "JSONException: Error: " + e.getMessage());
        }
        // Recreate the Pairs from Stored Values
        pairMap = new LinkedHashMap<>();
        for (int i = 0; i < storedPairsArray.length(); i++) {
            JSONObject storedPair;
            try {
                storedPair = storedPairsArray.getJSONObject(i);
                Pair pair = new Pair(storedPair.getString("pairId"),
                        storedPair.getString("encoderState"),
                        storedPair.getString("decoderState"));
                pairMap.put(pair.pairId, pair);
            } catch (JSONException e) {
                throw new RelayException(getClass().getSimpleName(),
                        "JSONException: Error: " + e.getMessage());
            }
        }
        return true;
    }

    public Map<String, Pair> createPairMap(int count) {
        pairMap = new LinkedHashMap<>();
        for (int i=0; i<count; i++) {
            Pair pair = new Pair();
            pairMap.put(pair.pairId, pair);
        }
        return pairMap;
    }

    public EncodeResult encode(String pairId, String plaintext) throws MteException {
        EncodeResult result = new EncodeResult();
        Pair pair;
        if (pairId != null) {
            pair = pairMap.get(pairId);
        } else {
            pair = getNextPair();
        }
        if (pair != null) {
            result.pairId = pair.pairId;
            result.encodedStr = pair.encode(plaintext);
        }
        return result;
    }

    public EncodeResult encode(String pairId, byte[] bytes) throws MteException {
        EncodeResult result = new EncodeResult();
        Pair pair;
        if (pairId != null) {
            pair = pairMap.get(pairId);
        } else {
            pair = getNextPair();
        }
        if (pair != null) {
            result.pairId = pair.pairId;
            result.encodedBytes = pair.encode(bytes);
        }
        return result;
    }

    public DecodeResult decode(String pairId, String encoded) throws MteException {
        DecodeResult decodeResult = new DecodeResult();
        checkPairId(getClass().getSimpleName(), pairId);
        Pair pair = pairMap.get(pairId);
        if (pair != null) {
            decodeResult = pair.decode(encoded);
        }
        return decodeResult;
    }



    public DecodeResult decode(String pairId, byte[] encoded) throws MteException {
        DecodeResult decodeResult = new DecodeResult();
        checkPairId(getClass().getSimpleName(), pairId);
        Pair pair = pairMap.get(pairId);
        if (pair != null) {
            decodeResult = pair.decode(encoded);
        }
        return decodeResult;
    }

    public void startDecrypt(String pairId) throws MteException {
        checkPairId(getClass().getSimpleName(), pairId);
        Pair pair = pairMap.get(pairId);
        if (pair != null) {
            pair.startDecrypt();
        }
    }

    public DecodeResult decryptChunk(String pairId, byte[] encoded) {
        checkPairId(getClass().getSimpleName(), pairId);
        DecodeResult result = new DecodeResult();
        Pair pair = pairMap.get(pairId);
        if (pair != null) {
            result.pairId = pair.pairId;
            result.decodedBytes = pair.decryptChunk(encoded);
        }
        return result;
    }

    public int decryptChunk(String pairId, byte[] encrypted, int encOff, int encLen, byte[] decrypted, int decOff) {
        checkPairId(getClass().getSimpleName(), pairId);
        int encryptedBytes = 0;
        Pair pair = pairMap.get(pairId);
        if (pair != null) {
            encryptedBytes = pair.decryptChunk(encrypted, encOff, encLen, decrypted, decOff);
        }
        return encryptedBytes;
    }

    public DecodeResult finishDecrypt(String pairId) throws MteException {
        checkPairId(getClass().getSimpleName(), pairId);
        DecodeResult result = new DecodeResult();
        Pair pair = pairMap.get(pairId);
        if (pair != null) {
            result.pairId = pair.pairId;
            MteBase.ArrStatus arrStatus = pair.finishDecrypt();
            result.decodedBytes = arrStatus.arr;
        }
        return result;
    }

    public String getNextPairId() {
        return getNextPair().pairId;
    }

    public int getEncryptFinishBytes() throws MteException {
        Pair pair = getNextPair();
        return pair.getFinishEncryptBytes();
    }

    public void startEncrypt(String pairId) throws MteException {
        checkPairId(getClass().getSimpleName(), pairId);
        Pair pair = pairMap.get(pairId);
        if (pair != null) {
            pair.startEncrypt();
        }
    }

    public void encryptChunk(String pairId, byte[] bytes, int len) throws MteException {
        checkPairId(getClass().getSimpleName(), pairId);
        Pair pair = pairMap.get(pairId);
        if (pair != null) {
            pair.encryptChunk(bytes, len);
        }
    }

    public EncodeResult finishEncrypt(String pairId) throws MteException {
        EncodeResult result = new EncodeResult();
        Pair pair = pairMap.get(pairId);
        if (pair != null) {
            result.pairId = pair.pairId;
            MteBase.ArrStatus arrStatus = pair.finishEncrypt();
            result.encodedBytes = arrStatus.arr;
        }
        return result;
    }


    private Pair getNextPair() {
        Set<String> keySet = pairMap.keySet();
        List<String> listKeys = new ArrayList<>(keySet);
        String key = listKeys.get(nextPair);
        if (nextPair == keySet.size()-1) {
            nextPair = 0;
        } else {
            nextPair++;
        }
        return pairMap.get(key);
    }

    public String getPairMapStates() {
        JSONArray pairMapToStore = new JSONArray();
        pairMap.forEach((pairId, pair) -> {
            JSONObject pairToStore = new JSONObject();
            try {
                pairToStore.put("pairId", pairId)
                .put("encoderState", Base64.encodeToString(pair.getEncoderState(), Base64.DEFAULT))
                .put("decoderState", Base64.encodeToString(pair.getDecoderState(), Base64.DEFAULT));
            } catch (JSONException e) {
                throw new RelayException(getClass().getSimpleName(),
                        "JSONException: Error: " + e.getMessage());
            }
            pairMapToStore.put(pairToStore);
        });
        return pairMapToStore.toString();
    }

    private void checkPairId(String className, String pairId) {
        if (pairId == null) {
            throw new RelayException(className, "No pairId passed to Decoder.");
        }
    }

}
