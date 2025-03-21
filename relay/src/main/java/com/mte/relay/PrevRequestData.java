package com.mte.relay;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;

public class PrevRequestData implements RetryableRequestData {
    Host host;
    Request<?> request;
    String[] headersToEncrypt;
    RelayDataTaskListener listener;


    public PrevRequestData(Host host, Request<?> request, String[] headersToEncrypt, RelayDataTaskListener listener) {
        this.host = host;
        this.request =  request;
        this.headersToEncrypt = headersToEncrypt;
        this.listener = listener;
    }

    @Override
    public void retry() {
        Thread sendingTread = new Thread(() -> {
            try {
                host.sendUpdatedRequest(
                        this.request,
                        this.headersToEncrypt,
                        this.listener);
            } catch (InterruptedException |
                     UnsupportedEncodingException |
                     AuthFailureError |
                     MalformedURLException e) {
                this.listener.onError(e.getMessage(), null);
            }
        });
        sendingTread.start();
    }
}
