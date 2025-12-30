package com.mte.relay;

import java.util.Map;

public class RelayFileUploadProperties {

    final String hostUrl;
    String route;
    final MteHelper mteHelper;
    final String[] headersToEncrypt;
    final Map<String,String> origHeaders;
    final RelayOptions relayOptions;
    final RelayStreamCallback relayStreamCallback;

    public RelayFileUploadProperties(String hostUrl,
                                     String route,
                                     MteHelper mteHelper,
                                     String[] headersToEncrypt,
                                     Map<String, String> origHeaders,
                                     RelayOptions relayOptions,
                                     RelayStreamCallback relayStreamCallback) {
        this.hostUrl = hostUrl;
        this.route = route;
        this.mteHelper = mteHelper;
        this.headersToEncrypt = headersToEncrypt;
        this.origHeaders = origHeaders;
        this.relayOptions = relayOptions;
        this.relayStreamCallback = relayStreamCallback;
    }
}
