package io.nop.http.api.utils;

import io.nop.api.core.util.ApiStringHelper;

import java.net.HttpCookie;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpCookieHelper {
    public static List<HttpCookie> parseSetCookie(String cookieStr) {
        return HttpCookie.parse(cookieStr);
    }

    public static String buildSetCookie(List<HttpCookie> cookies) {
        return ApiStringHelper.join(cookies, ";");
    }

    public static Map<String, String> parseCookie(String cookieHeader) {

        Map<String, String> cookieMap = new LinkedHashMap<>();

        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return cookieMap;
        }

        List<String> cookies = ApiStringHelper.split(cookieHeader, ';');

        for (String cookie : cookies) {
            List<String> parts = ApiStringHelper.split(cookie.trim(), '=');
            if (parts.size() == 2) {
                cookieMap.put(parts.get(0), parts.get(1));
            }
        }

        return cookieMap;
    }
}
