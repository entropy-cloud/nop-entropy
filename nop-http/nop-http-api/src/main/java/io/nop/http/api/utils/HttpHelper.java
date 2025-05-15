package io.nop.http.api.utils;

import io.nop.http.api.HttpApiConstants;

public class HttpHelper {
    public static boolean isOk(int status) {
        return status >= 200 && status < 300;
    }

    public static boolean isSecretHeader(String name) {
        return HttpApiConstants.HEADER_AUTHORIZATION.equalsIgnoreCase(name);
    }
}
