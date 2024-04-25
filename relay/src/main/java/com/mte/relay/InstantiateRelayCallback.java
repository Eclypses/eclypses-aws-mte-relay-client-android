package com.mte.relay;

public interface InstantiateRelayCallback {

    void onError(String message);

    void relayInstantiated();
}
