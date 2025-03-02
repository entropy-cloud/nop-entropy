package io.nop.http.api.utils;

public class HttpHelper {
    public static boolean isOk(int status) {
        return status >= 200 && status < 300;
    }
}
