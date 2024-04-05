package com.mte.relay;

import java.io.File;
import java.net.URL;
import java.util.Map;

public class RelayFileUploadProperties {

    String hostUrl;
    String route;
    File fileToUpload;
    MteHelper mteHelper;
    String[] headersToEncrypt;
    Map<String,String> origHeaders;
    RelayOptions relayOptions;
    RelayStreamCallback relayStreamCallback;

    public RelayFileUploadProperties(String hostUrl,
                                     String route,
                                     File fileToUpload,
                                     MteHelper mteHelper,
                                     String[] headersToEncrypt,
                                     Map<String, String> origHeaders,
                                     RelayOptions relayOptions,
                                     RelayStreamCallback relayStreamCallback) {
        this.hostUrl = hostUrl;
        this.route = route;
        this.fileToUpload = fileToUpload;
        this.mteHelper = mteHelper;
        this.headersToEncrypt = headersToEncrypt;
        this.origHeaders = origHeaders;
        this.relayOptions = relayOptions;
        this.relayStreamCallback = relayStreamCallback;
    }
}
