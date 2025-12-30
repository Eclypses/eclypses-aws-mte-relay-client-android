package com.mte.relay;

public final class NetworkUtils {

    private NetworkUtils() {
        throw new UnsupportedOperationException("NetworkUtils class - cannot be instantiated");
    }

    public static boolean shouldRePairWithHost(int code, RetryableRequestData requestData) {
        boolean shouldRePair = Constants.lowMteErrorCode <= code &&
                code <= Constants.highMteErrorCode &&
                requestData != null;
        if (shouldRePair) {
            LogHelper.info("RePair", "Status code: " + code + " so we'll rePair and retry previous Request");
        }
        return shouldRePair;
    }

}
