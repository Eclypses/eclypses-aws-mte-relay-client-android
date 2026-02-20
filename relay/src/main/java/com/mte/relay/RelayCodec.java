package com.mte.relay;

public interface RelayCodec {
    EncodeResult encode(String pairId, String plaintext) throws MteException;

    DecodeResult decode(String pairId, String encoded) throws MteException;
}