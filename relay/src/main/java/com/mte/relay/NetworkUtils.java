package com.mte.relay;

public final class NetworkUtils {

    private NetworkUtils() {
        throw new UnsupportedOperationException("NetworkUtils class - cannot be instantiated");
    }

    public static boolean shouldRePairWithHost(int code, RetryableRequestData requestData) {
        return Constants.lowMteErrorCode <= code &&
                code <= Constants.highMteErrorCode &&
                requestData != null;
    }

}
