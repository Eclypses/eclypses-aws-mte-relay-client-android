package com.mte.relay;

public interface RetryUploadCallback {
    void onCompletion(int code, RelayStreamResponseListener listener);
}
