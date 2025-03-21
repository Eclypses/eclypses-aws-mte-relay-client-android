package com.mte.relay;

public class PrevUploadData implements RetryableRequestData {

    final Host host;
    final RelayFileRequestProperties reqProperties;
    final String route;
    final RelayStreamResponseListener listener;
    final RelayStreamCompletionCallback completionCallback;

    public PrevUploadData(Host host,
                          RelayFileRequestProperties reqProperties,
                          String route,
                          RelayStreamResponseListener listener,
                          RelayStreamCompletionCallback completionCallback) {
        this.host = host;
        this.reqProperties = reqProperties;
        this.route = route;
        this.listener = listener;
        this.completionCallback = completionCallback;
    }

    @Override
    public void retry() {
        Thread sendingTread = new Thread(() -> {
            host.uploadFile(reqProperties, route, listener, completionCallback);
        });
        sendingTread.start();
    }
}
