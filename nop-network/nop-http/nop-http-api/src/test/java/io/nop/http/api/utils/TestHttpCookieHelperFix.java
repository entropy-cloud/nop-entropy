package io.nop.http.api.utils;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestHttpCookieHelperFix {

    @Test
    public void testParseCookieWithValueContainingEquals() {
        Map<String, String> map = HttpCookieHelper.parseCookie("a=1; token=abc==");
        assertEquals("1", map.get("a"));
        assertEquals("abc==", map.get("token"));
    }

    @Test
    public void testParsePlainCookie() {
        Map<String, String> map = HttpCookieHelper.parseCookie("a=1; b=2");
        assertEquals(2, map.size());
        assertEquals("1", map.get("a"));
        assertEquals("2", map.get("b"));
    }
}
