package com.mte.relay;

import java.util.List;
import java.util.Map;

public interface RelayStreamResponseListener {
    void relayStreamResponse(boolean success,
                             String responseStr,
                             String errorMessage,
                             Map<String, List<String>> responseHeaders);
}
