package io.nop.spring.web.filter;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P1-6.8-001（plan 2391）：removeCookie 对 request.getCookies()==null（Servlet 规范
 * 允许——无 Cookie 的请求）必须 NPE 安全，落到兜底过期 Cookie 分支。此前无 Cookie 的
 * 登出请求（过期会话/扫描器/curl）高频 NPE 500。request/response 均为接口，用 JDK
 * 动态代理 stub（零新增依赖；同 IUserContext 代理先例）。
 */
public class TestServletHttpServerContextRemoveCookie {

    static class Captor {
        Cookie[] cookies;
        final List<Cookie> addedCookies = new ArrayList<>();

        HttpServletRequest newRequest() {
            return (HttpServletRequest) Proxy.newProxyInstance(
                    TestServletHttpServerContextRemoveCookie.class.getClassLoader(),
                    new Class[]{HttpServletRequest.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getCookies":
                                return cookies;
                            case "getMethod":
                                return "GET";
                            default:
                                return defaultFor(method.getReturnType());
                        }
                    });
        }

        HttpServletResponse newResponse() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    TestServletHttpServerContextRemoveCookie.class.getClassLoader(),
                    new Class[]{HttpServletResponse.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "addCookie":
                                addedCookies.add((Cookie) args[0]);
                                return null;
                            default:
                                return defaultFor(method.getReturnType());
                        }
                    });
        }

        private static Object defaultFor(Class<?> type) {
            if (type == boolean.class)
                return false;
            if (type == int.class)
                return 0;
            if (type == long.class)
                return 0L;
            return null;
        }
    }

    private static ServletHttpServerContext newContext(Captor captor) {
        return new ServletHttpServerContext(captor.newRequest(), captor.newResponse());
    }

    @Test
    public void testRemoveCookieWithNullCookiesFallsBackToExpiryCookie() {
        Captor captor = new Captor();
        captor.cookies = null; // 缺陷场景：无任何 Cookie 的请求
        ServletHttpServerContext ctx = newContext(captor);

        ctx.removeCookie("nop-token");

        assertEquals(1, captor.addedCookies.size(), "fallback expiry cookie must be planted");
        Cookie planted = captor.addedCookies.get(0);
        assertEquals("nop-token", planted.getName());
        assertEquals("", planted.getValue());
        assertEquals(0, planted.getMaxAge(), "maxAge=0 expires the cookie immediately");
    }

    @Test
    public void testRemoveCookieWithMatchingCookieExpiresIt() {
        Captor captor = new Captor();
        captor.cookies = new Cookie[]{new Cookie("nop-token", "abc")};
        ServletHttpServerContext ctx = newContext(captor);

        ctx.removeCookie("nop-token");

        assertEquals(1, captor.addedCookies.size());
        assertEquals(0, captor.addedCookies.get(0).getMaxAge(), "matching cookie reused with maxAge=0");
    }

    @Test
    public void testRemoveCookieWithoutMatchFallsBackToExpiryCookie() {
        Captor captor = new Captor();
        captor.cookies = new Cookie[]{new Cookie("other", "v")};
        ServletHttpServerContext ctx = newContext(captor);

        ctx.removeCookie("nop-token");

        assertEquals(1, captor.addedCookies.size(), "no match → fallback expiry cookie");
        assertEquals("nop-token", captor.addedCookies.get(0).getName());
        assertEquals(0, captor.addedCookies.get(0).getMaxAge());
    }
}
