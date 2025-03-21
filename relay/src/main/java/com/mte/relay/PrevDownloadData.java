package com.mte.relay;

import java.io.IOException;

public class PrevDownloadData implements RetryableRequestData {

    final Host host;
    final RelayFileRequestProperties reqProperties;
    final RelayStreamResponseListener listener;

    public PrevDownloadData(Host host, RelayFileRequestProperties reqProperties, RelayStreamResponseListener listener) {
        this.host = host;
        this.reqProperties = reqProperties;
        this.listener = listener;
    }

    @Override
    public void retry() {
        Thread sendingTread = new Thread(() -> {
            try {
                host.downloadFile(reqProperties, listener);
            } catch (IOException e) {
                listener.relayStreamResponse(false, "", e.getMessage(), null);
            }
        });
        sendingTread.start();
    }
}
