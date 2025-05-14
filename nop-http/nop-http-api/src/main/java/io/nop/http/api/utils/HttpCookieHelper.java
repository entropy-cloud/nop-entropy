package io.nop.http.api.utils;

import io.nop.api.core.util.ApiStringHelper;

import java.net.HttpCookie;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpCookieHelper {
    public static List<HttpCookie> parseSetCookie(String cookieStr) {
        return HttpCookie.parse(cookieStr);
    }

    public static Map<String, String> parseSetCookieAsMap(String cookieStr) {
        List<HttpCookie> cookis = parseSetCookie(cookieStr);
        Map<String, String> cookieMap = new HashMap<>();
        for (HttpCookie cookie : cookis) {
            cookieMap.put(cookie.getName(), cookie.getValue());
        }
        return cookieMap;
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

    public static String buildCookie(Map<String, String> cookieMap) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cookieMap.entrySet()) {
            if (ApiStringHelper.isEmpty(entry.getValue()))
                continue;

            if (sb.length() > 0) {
                sb.append(";");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    public static String updateCookie(String cookie, String setCookie) {
        if (ApiStringHelper.isEmpty(setCookie))
            return cookie;

        if (ApiStringHelper.isEmpty(cookie)) {
            return buildCookie(parseSetCookieAsMap(setCookie));
        }

        Map<String, String> map = parseCookie(cookie);
        map.putAll(parseSetCookieAsMap(setCookie));
        return buildCookie(map);
    }
}
