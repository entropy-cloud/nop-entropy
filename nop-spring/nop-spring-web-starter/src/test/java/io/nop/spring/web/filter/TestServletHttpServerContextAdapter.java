package io.nop.spring.web.filter;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-4.2-002 (plan 2406): direct contract tests for the servlet IHttpServerContext adapter.
 * Mirrors {@code TestVertxHttpServerContextAdapter} case-for-case (quarkus module); the two
 * implementations intentionally diverge in several places and those divergences are asserted
 * explicitly here (see the class list in the plan 2406 Baseline). JDK dynamic proxies keep this
 * dependency-free (precedent: TestServletHttpServerContextRemoveCookie, plan 2391).
 */
public class TestServletHttpServerContextAdapter {

    static class Stub {
        String method = "POST";
        String remoteAddr = "10.0.0.1";
        int remotePort = 12345;
        String remoteHost = "client.example.com";
        String requestURI = "/app/graphql";
        StringBuffer requestURL = new StringBuffer("http://host:8080/app/graphql");
        String queryString = "query=%7Bme%7D";
        final Map<String, String> params = new LinkedHashMap<>();
        final Map<String, String> headers = new LinkedHashMap<>(); // case-sensitive: two case-variant keys survive
        Cookie[] cookies;

        final Map<String, String> responseHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final List<Cookie> addedCookies = new ArrayList<>();
        String contentType;
        String characterEncoding;
        int status;
        String redirect;
        boolean committed;
        final StringWriter body = new StringWriter();

        HttpServletRequest newRequest() {
            return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{HttpServletRequest.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getMethod":
                                return this.method;
                            case "getRemoteAddr":
                                return remoteAddr;
                            case "getRemotePort":
                                return remotePort;
                            case "getRemoteHost":
                                return remoteHost;
                            case "getRequestURI":
                                return requestURI;
                            case "getRequestURL":
                                return requestURL;
                            case "getQueryString":
                                return queryString;
                            case "getParameter":
                                return params.get(args[0]);
                            case "getParameterNames":
                                return Collections.enumeration(params.keySet());
                            case "getHeaderNames":
                                return Collections.enumeration(headers.keySet());
                            case "getHeader":
                                return headers.get(args[0]);
                            case "getCookies":
                                return cookies;
                            default:
                                return defaultFor(method.getReturnType());
                        }
                    });
        }

        HttpServletResponse newResponse() {
            return (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{HttpServletResponse.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getContentType":
                                return contentType;
                            case "setContentType":
                                contentType = (String) args[0];
                                return null;
                            case "getCharacterEncoding":
                                return characterEncoding;
                            case "setCharacterEncoding":
                                characterEncoding = (String) args[0];
                                return null;
                            case "setHeader":
                            case "addHeader":
                                responseHeaders.put((String) args[0], (String) args[1]);
                                return null;
                            case "setStatus":
                                status = (Integer) args[0];
                                return null;
                            case "sendRedirect":
                                redirect = (String) args[0];
                                return null;
                            case "getWriter":
                                return new PrintWriter(body);
                            case "isCommitted":
                                return committed;
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

        ServletHttpServerContext newContext() {
            return new ServletHttpServerContext(newRequest(), newResponse());
        }
    }

    @Test
    public void testBasicFields() {
        Stub stub = new Stub();
        ServletHttpServerContext ctx = stub.newContext();

        assertEquals("POST", ctx.getMethod());
        assertEquals("10.0.0.1", ctx.getRemoteAddr());
        assertEquals(12345, ctx.getRemotePort());
        assertEquals("client.example.com", ctx.getHost(), "spring getHost delegates to getRemoteHost (client host)");
        assertEquals("/app/graphql", ctx.getRequestPath(), "spring getRequestPath uses getRequestURI (un-normalized)");
    }

    @Test
    public void testGetRequestUrlIncludesQueryString() {
        Stub stub = new Stub();
        assertEquals("http://host:8080/app/graphql?query=%7Bme%7D", stub.newContext().getRequestUrl());

        stub.queryString = null;
        assertEquals("http://host:8080/app/graphql", stub.newContext().getRequestUrl());

        stub.queryString = "";
        assertEquals("http://host:8080/app/graphql", stub.newContext().getRequestUrl(),
                "empty queryString must not append a bare '?'");
    }

    @Test
    public void testQueryParams() {
        Stub stub = new Stub();
        stub.params.put("b", "2");
        stub.params.put("a", "1");
        ServletHttpServerContext ctx = stub.newContext();

        assertEquals("1", ctx.getQueryParam("a"));
        assertNull(ctx.getQueryParam("missing"));
        assertEquals(List.of("b", "a"), new ArrayList<>(ctx.getQueryParams().keySet()), "insertion order preserved");
    }

    @Test
    public void testRequestHeadersAreLowerCasedAndLastValueWins() {
        Stub stub = new Stub();
        stub.headers.put("X-Token", "first");
        stub.headers.put("x-token", "last");
        ServletHttpServerContext ctx = stub.newContext();

        assertEquals(1, ctx.getRequestHeaders().size(),
                "both case-variant raw keys normalize onto one entry");
        assertEquals("last", ctx.getRequestHeaders().get("x-token"),
                "spring uses put(): later case-variant wins (diverges from vertx putIfAbsent)");
        assertFalse(ctx.getRequestHeaders().containsKey("X-Token"), "keys normalized to lower case");
        assertEquals("last", ctx.getRequestHeader("x-token"));
    }

    @Test
    public void testGetCookie() {
        Stub stub = new Stub();
        stub.cookies = new Cookie[]{new Cookie("nop-token", "abc")};
        assertEquals("abc", stub.newContext().getCookie("nop-token"));
        assertNull(stub.newContext().getCookie("other"));

        stub.cookies = null;
        assertNull(stub.newContext().getCookie("nop-token"), "null cookies must be tolerated");
    }

    @Test
    public void testSetResponseHeaderNullIsLiteralNull() {
        Stub stub = new Stub();
        stub.newContext().setResponseHeader("x-test", null);
        assertEquals("null", stub.responseHeaders.get("x-test"),
                "spring writes String.valueOf(null) == \"null\" (diverges from vertx remove)");
    }

    @Test
    public void testCharacterEncodingIsAppendedOnSetContentType() {
        Stub stub = new Stub();
        ServletHttpServerContext ctx = stub.newContext();

        ctx.setResponseCharacterEncoding("UTF-8");
        ctx.setResponseContentType("text/html");
        assertEquals("text/html;charset=UTF-8", ctx.getResponseContentType());

        ctx.setResponseContentType("application/json;charset=GBK");
        assertEquals("application/json;charset=GBK", ctx.getResponseContentType(),
                "an explicit charset must not be overwritten");
    }

    @Test
    public void testSendResponseAndIsResponseSent() {
        Stub stub = new Stub();
        ServletHttpServerContext ctx = stub.newContext();

        ctx.sendResponse(200, "ok");
        assertEquals(200, stub.status);
        assertEquals("ok", stub.body.toString());
        assertFalse(ctx.isResponseSent(),
                "spring isResponseSent delegates to response.isCommitted() (diverges from vertx flag)");

        stub.committed = true;
        assertTrue(ctx.isResponseSent());
    }

    @Test
    public void testSendRedirect() {
        Stub stub = new Stub();
        Stub responseStub = stub;
        ServletHttpServerContext ctx = stub.newContext();

        ctx.sendRedirect("/login");
        assertEquals("/login", responseStub.redirect);
    }

    @Test
    public void testAddCookieSetsMaxAgeAndIgnoresSameSite() {
        Stub stub = new Stub();
        java.net.HttpCookie cookie = new java.net.HttpCookie("nop-token", "v");
        cookie.setPath("/");
        cookie.setMaxAge(60);
        cookie.setHttpOnly(true);

        stub.newContext().addCookie("Lax", cookie);

        assertEquals(1, stub.addedCookies.size());
        Cookie added = stub.addedCookies.get(0);
        assertEquals("nop-token", added.getName());
        assertEquals(60, added.getMaxAge(), "spring maps HttpCookie maxAge (no sameSite support)");
    }
}
