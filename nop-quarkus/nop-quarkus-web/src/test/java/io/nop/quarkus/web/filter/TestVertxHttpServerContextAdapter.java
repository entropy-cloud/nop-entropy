package io.nop.quarkus.web.filter;

import io.vertx.core.MultiMap;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-4.2-001 (plan 2406): direct contract tests for the vertx IHttpServerContext adapter.
 * Case set mirrors {@code io.nop.spring.web.filter.TestServletHttpServerContextAdapter}; the
 * implementations intentionally diverge (host/requestUrl/requestPath/query/header-collision/
 * setResponseHeader(null)/charset timing/addCookie) and each divergence is asserted explicitly.
 * All vertx types used here are interfaces (SocketAddress included), so JDK dynamic proxies keep
 * this test dependency-free beyond junit.
 */
public class TestVertxHttpServerContextAdapter {

    static class Stub {
        String normalizedPath = "/app/graphql";
        String absoluteURI = "http://host:8080/app/graphql?query=%7Bme%7D";
        String host = "host:8080";
        String remoteAddress = "10.0.0.1";
        int remotePort = 12345;
        final Map<String, List<String>> queryParams = new LinkedHashMap<>();
        final Map<String, String> headers = new LinkedHashMap<>();
        final Map<String, String> responseHeaders = new LinkedHashMap<>();
        Cookie cookie;
        final List<Cookie> addedCookies = new ArrayList<>();
        final List<String> removedCookies = new ArrayList<>();
        final List<Integer> removedCookieArgCounts = new ArrayList<>();
        String acceptableContentType = "application/json";
        String redirect;
        int status;
        String sentBody;
        boolean sent;

        Stub() {
            queryParams.put("query", List.of("%7Bme%7D"));
            queryParams.put("lang", List.of("zh"));
        }

        MultiMap multiMap(Map<String, ?> map) {
            return (MultiMap) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{MultiMap.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "get":
                                Object v = map.get(String.valueOf(args[0]));
                                if (v instanceof List)
                                    return ((List<?>) v).isEmpty() ? null : String.valueOf(((List<?>) v).get(0));
                                return v instanceof CharSequence ? v.toString() : v;
                            case "set":
                            case "add":
                                ((Map) map).put(String.valueOf(args[0]), args[1]);
                                return proxy;
                            case "remove":
                                map.remove(String.valueOf(args[0]));
                                return proxy;
                            case "contains":
                            case "containsKey":
                                return map.containsKey(String.valueOf(args[0]));
                            case "size":
                                return map.size();
                            case "isEmpty":
                                return map.isEmpty();
                            case "iterator":
                                return map.entrySet().iterator();
                            case "forEach":
                                for (Map.Entry<String, ?> e : map.entrySet()) {
                                    ((java.util.function.BiConsumer<String, Object>) args[0])
                                            .accept(e.getKey(), e.getValue() instanceof List
                                                    ? ((List<?>) e.getValue()).get(0) : e.getValue());
                                }
                                return null;
                            default:
                                return defaultFor(method.getReturnType());
                        }
                    });
        }

        HttpServerResponse newResponse() {
            return (HttpServerResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{HttpServerResponse.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "headers":
                                return multiMap(responseHeaders);
                            case "setStatusCode":
                                status = (Integer) args[0];
                                return proxy;
                            case "send":
                                sent = true;
                                sentBody = (String) args[0];
                                return null;
                            case "addCookie":
                                addedCookies.add((Cookie) args[0]);
                                return proxy;
                            case "removeCookie":
                                removedCookies.add((String) args[0]);
                                removedCookieArgCounts.add(args.length);
                                return null; // HttpServerResponse.removeCookie(String) returns Cookie
                            default:
                                return defaultFor(method.getReturnType());
                        }
                    });
        }

        HttpServerRequest newRequest() {
            return (HttpServerRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{HttpServerRequest.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "method":
                                return HttpMethod.POST;
                            case "host":
                                return host;
                            case "absoluteURI":
                                return absoluteURI;
                            case "remoteAddress":
                                return (SocketAddress) Proxy.newProxyInstance(getClass().getClassLoader(),
                                        new Class[]{SocketAddress.class}, (p2, m2, a2) -> {
                                            if (m2.getName().equals("hostAddress"))
                                                return remoteAddress;
                                            if (m2.getName().equals("port"))
                                                return remotePort;
                                            return defaultFor(m2.getReturnType());
                                        });
                            case "headers":
                                return multiMap(headers);
                            case "getHeader":
                                return headers.get(args[0]);
                            case "getCookie":
                                return cookie;
                            default:
                                return defaultFor(method.getReturnType());
                        }
                    });
        }

        RoutingContext newContext() {
            return (RoutingContext) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{RoutingContext.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "request":
                                return newRequest();
                            case "response":
                                return newResponse();
                            case "normalizedPath":
                                return normalizedPath;
                            case "queryParam":
                                List<String> list = queryParams.get(args[0]);
                                return list == null ? List.of() : list;
                            case "queryParams":
                                return multiMap(queryParams);
                            case "getAcceptableContentType":
                                return acceptableContentType;
                            case "redirect":
                                redirect = (String) args[0];
                                return null;
                            default:
                                return defaultFor(method.getReturnType());
                        }
                    });
        }

        VertxHttpServerContext newServerContext() {
            return new VertxHttpServerContext(newContext());
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

    private static Cookie cookie(String name, String value) {
        return (Cookie) Proxy.newProxyInstance(TestVertxHttpServerContextAdapter.class.getClassLoader(),
                new Class[]{Cookie.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getValue"))
                        return value;
                    if (method.getName().equals("getName"))
                        return name;
                    return null;
                });
    }

    @Test
    public void testBasicFields() {
        Stub stub = new Stub();
        VertxHttpServerContext ctx = stub.newServerContext();

        assertEquals("POST", ctx.getMethod());
        assertEquals("10.0.0.1", ctx.getRemoteAddr());
        assertEquals(12345, ctx.getRemotePort());
        assertEquals("host:8080", ctx.getHost(), "vertx getHost uses the Host header (request().host())");
        assertEquals("/app/graphql", ctx.getRequestPath(), "vertx getRequestPath uses normalizedPath()");
        assertEquals("http://host:8080/app/graphql?query=%7Bme%7D", ctx.getRequestUrl());
    }

    @Test
    public void testQueryParams() {
        Stub stub = new Stub();
        VertxHttpServerContext ctx = stub.newServerContext();

        assertEquals("%7Bme%7D", ctx.getQueryParam("query"), "first value of the query parameter");
        assertNull(ctx.getQueryParam("missing"), "absent query parameter -> null (empty list)");
        assertEquals(List.of("query", "lang"), new ArrayList<>(ctx.getQueryParams().keySet()));
    }

    @Test
    public void testRequestHeadersLowerCasedAndFirstValueWins() {
        Stub stub = new Stub();
        stub.headers.put("X-Token", "first");
        stub.headers.put("x-token", "last");
        VertxHttpServerContext ctx = stub.newServerContext();

        assertEquals("first", ctx.getRequestHeaders().get("x-token"),
                "vertx uses putIfAbsent(): first case-variant wins (diverges from spring put())");
        assertFalse(ctx.getRequestHeaders().containsKey("X-Token"));
        assertEquals("last", ctx.getRequestHeader("x-token"),
                "raw getHeader is exact-key lookup (normalization applies only to getRequestHeaders)");
    }

    @Test
    public void testGetCookie() {
        Stub stub = new Stub();
        stub.cookie = cookie("nop-token", "abc");
        assertEquals("abc", stub.newServerContext().getCookie("nop-token"));

        stub.cookie = null;
        assertNull(stub.newServerContext().getCookie("nop-token"));
    }

    @Test
    public void testSetResponseHeaderNullRemovesHeader() {
        Stub stub = new Stub();
        VertxHttpServerContext ctx = stub.newServerContext();

        ctx.setResponseHeader("x-test", "v");
        assertEquals("v", stub.responseHeaders.get("x-test"));

        ctx.setResponseHeader("x-test", null);
        assertNull(stub.responseHeaders.get("x-test"),
                "vertx removes the header on null (diverges from spring literal \"null\")");
    }

    @Test
    public void testCharacterEncodingReappliedToExistingContentType() {
        Stub stub = new Stub();
        VertxHttpServerContext ctx = stub.newServerContext();

        ctx.setResponseContentType("text/html");
        ctx.setResponseCharacterEncoding("UTF-8");
        assertEquals("text/html;charset=UTF-8", ctx.getResponseContentType(),
                "vertx replays charset on setResponseCharacterEncoding (diverges from spring ordering)");
    }

    @Test
    public void testSendResponseRedirectAndIsResponseSent() {
        Stub stub = new Stub();
        VertxHttpServerContext ctx = stub.newServerContext();

        assertFalse(ctx.isResponseSent());
        ctx.sendResponse(200, "ok");
        assertEquals(200, stub.status);
        assertEquals("ok", stub.sentBody);
        assertTrue(ctx.isResponseSent(), "vertx tracks its own responseSent flag");

        ctx.sendRedirect("/login");
        assertEquals("/login", stub.redirect);
    }

    @Test
    public void testAcceptableContentType() {
        Stub stub = new Stub();
        assertEquals("application/json", stub.newServerContext().getAcceptableContentType());
    }

    @Test
    public void testAddCookieMapsSameSiteAndDoesNotSetMaxAge() {
        Stub stub = new Stub();
        VertxHttpServerContext ctx = stub.newServerContext();

        HttpCookie httpCookie = new HttpCookie("nop-token", "v");
        httpCookie.setPath("/");
        httpCookie.setHttpOnly(true);
        ctx.addCookie("Lax", httpCookie);
        ctx.addCookie("Strict", httpCookie);
        ctx.addCookie("None", httpCookie);
        ctx.addCookie("unknown", httpCookie);

        assertEquals(4, stub.addedCookies.size());
        assertEquals("Lax", stub.addedCookies.get(0).getSameSite().toString());
        assertEquals("Strict", stub.addedCookies.get(1).getSameSite().toString());
        assertEquals("None", stub.addedCookies.get(2).getSameSite().toString());
        assertNull(stub.addedCookies.get(3).getSameSite(), "unknown sameSite must not be applied");
        assertTrue(stub.addedCookies.get(0).getMaxAge() < 0,
                "vertx does not map HttpCookie.maxAge (diverges from spring)");
    }

    @Test
    public void testRemoveCookieDelegatesToResponse() {
        Stub stub = new Stub();
        VertxHttpServerContext ctx = stub.newServerContext();

        ctx.removeCookie("nop-token");
        ctx.removeCookie("nop-token", "example.com", "/");
        assertEquals(List.of("nop-token", "nop-token"), stub.removedCookies);
        assertEquals(List.of(1, 3), stub.removedCookieArgCounts, "both removeCookie overloads must delegate");
    }
}
