package com.mte.relay;

import java.util.Map;

public class FileDownloadProperties {

    String hostUrl;
    String route;
    String downloadPath;
    MteHelper mteHelper;
    String[] headersToEncrypt;
    Map<String,String> origHeaders;
    RelayOptions relayOptions;

    public FileDownloadProperties(String hostUrl,
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
