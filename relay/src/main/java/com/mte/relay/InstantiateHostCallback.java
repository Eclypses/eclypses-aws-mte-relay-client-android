package com.mte.relay;

public interface InstantiateHostCallback {

    void onError(String message);

    void hostInstantiated(String hostUrl, Host host);

}
