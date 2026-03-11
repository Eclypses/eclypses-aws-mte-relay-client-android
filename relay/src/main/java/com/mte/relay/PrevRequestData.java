package com.mte.relay;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;

public class PrevRequestData implements RetryableRequestData {
    final Host host;
    final Request<?> request;
    final String[] headersToEncrypt;
    final boolean preventStreaming;
    final RelayVolleyRequestListener listener;


    public PrevRequestData(Host host,
                           Request<?> request,
                           String[] headersToEncrypt,
                           boolean preventStreaming,
                           RelayVolleyRequestListener listener) {
        this.host = host;
        this.request =  request;
        this.headersToEncrypt = headersToEncrypt;
        this.preventStreaming = preventStreaming;
        this.listener = listener;
    }

    @Override
    public void retry() {
        Thread sendingTread = new Thread(() -> {
            try {
                host.sendUpdatedRequest(
                        this.request,
                        this.headersToEncrypt,
                        this.preventStreaming,
                        this.listener);
            } catch (InterruptedException |
                     UnsupportedEncodingException |
                     AuthFailureError |
                     MalformedURLException e) {
                this.listener.onError(null, e.getMessage(), null);
            }
        });
        sendingTread.start();
    }
}
