package com.mte.relay;

import java.io.File;
import java.util.Map;

public class RelayFileDownloadProperties {

    String hostUrl;
    String route;
    String downloadPath;
    MteHelper mteHelper;
    String[] headersToEncrypt;
    Map<String,String> origHeaders;
    RelayOptions relayOptions;

    public RelayFileDownloadProperties(String hostUrl,
                                     String route,
                                     String downloadPath,
                                     MteHelper mteHelper,
                                     String[] headersToEncrypt,
                                     Map<String, String> origHeaders,
                                     RelayOptions relayOptions) {
        this.hostUrl = hostUrl;
        this.route = route;
        this.downloadPath = downloadPath;
        this.mteHelper = mteHelper;
        this.headersToEncrypt = headersToEncrypt;
        this.origHeaders = origHeaders;
        this.relayOptions = relayOptions;
    }

}
