package com.mte.relay;

public interface RetryDownloadCallback {
    void onCompletion(int code, RelayStreamResponseListener listener);
}
