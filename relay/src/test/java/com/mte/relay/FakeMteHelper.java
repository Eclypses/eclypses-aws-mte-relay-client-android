package com.mte.relay;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class FakeMteHelper implements RelayCodec {

    public static class EncodeInvocation {
        public final String pairId;
        public final String plaintext;

        public EncodeInvocation(String pairId, String plaintext) {
            this.pairId = pairId;
            this.plaintext = plaintext;
        }
    }

    public static class DecodeInvocation {
        public final String pairId;
        public final String encoded;

        public DecodeInvocation(String pairId, String encoded) {
            this.pairId = pairId;
            this.encoded = encoded;
        }
    }

    public RuntimeException encodeFailure;
    public RuntimeException decodeFailure;

    public boolean disposed = false;

    public final List<EncodeInvocation> encodeInvocations = new ArrayList<>();
    public final List<DecodeInvocation> decodeInvocations = new ArrayList<>();
    public final List<String> orderedHistory = new ArrayList<>();

    private final ArrayDeque<DecodeResult> queuedDecodeResults = new ArrayDeque<>();

    public String nextEncodePairId = "pair-default";
    public String nextEncodedStr = TestFixtures.ENCODED_HEADERS_FIXTURE;
    public String nextDecodePairId = "pair-default";
    public String nextDecodedJson = TestFixtures.DECODED_HEADERS_JSON;

    @Override
    public synchronized EncodeResult encode(String pairId, String plaintext) {
        if (encodeFailure != null) {
            throw encodeFailure;
        }
        encodeInvocations.add(new EncodeInvocation(pairId, plaintext));
        orderedHistory.add("encode:" + (pairId != null ? pairId : "null"));
        String responsePairId = pairId != null ? pairId : nextEncodePairId;
        return new EncodeResult(responsePairId, nextEncodedStr);
    }

    @Override
    public synchronized DecodeResult decode(String pairId, String encoded) {
        if (decodeFailure != null) {
            throw decodeFailure;
        }
        decodeInvocations.add(new DecodeInvocation(pairId, encoded));
        orderedHistory.add("decode:" + (pairId != null ? pairId : "null"));
        if (!queuedDecodeResults.isEmpty()) {
            return queuedDecodeResults.removeFirst();
        }
        String responsePairId = pairId != null ? pairId : nextDecodePairId;
        return new DecodeResult(responsePairId, nextDecodedJson);
    }

    public synchronized void queueDecodeEvent(String decodedJson, String pairId) {
        queuedDecodeResults.addLast(new DecodeResult(pairId, decodedJson));
        orderedHistory.add("event:queueDecode");
    }

    public synchronized void queueDecodeEvent(String decodedJson) {
        queueDecodeEvent(decodedJson, "pair-queued");
    }

    public synchronized void reset() {
        encodeInvocations.clear();
        decodeInvocations.clear();
        orderedHistory.clear();
        queuedDecodeResults.clear();
        disposed = false;
    }

    public synchronized void dispose() {
        disposed = true;
        orderedHistory.add("lifecycle:dispose");
    }
}
