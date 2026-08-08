package io.nop.auth.core.filter;

import io.nop.api.core.config.AppConfig;
import io.nop.auth.core.AuthCoreConfigs;
import io.nop.http.api.server.IHttpServerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 cookie 属性加固（M-2/M-5，DR-1c）：Secure 默认开启、__Host- 前缀、SameSite。
 * 纯单元测试，使用默认 SimpleConfigProvider 操控配置，无需 IoC 容器或数据库。
 */
public class TestAuthBrowserHardening {

    @BeforeEach
    public void ensureSecureDefault() {
        // 恢复默认值，避免测试间互相影响
        AppConfig.getConfigProvider().updateConfigValue(AuthCoreConfigs.CFG_AUTH_USE_SECURE_COOKIE, true);
    }

    @AfterEach
    public void resetSecure() {
        AppConfig.getConfigProvider().updateConfigValue(AuthCoreConfigs.CFG_AUTH_USE_SECURE_COOKIE, true);
    }

    @Test
    public void testSecureCookieDefaultIsTrue() {
        assertEquals(Boolean.TRUE, AuthCoreConfigs.CFG_AUTH_USE_SECURE_COOKIE.getDefaultValue());
        assertTrue(AuthCoreConfigs.CFG_AUTH_USE_SECURE_COOKIE.get());
    }

    @Test
    public void testAuthCookieAttributesSecureOn() {
        AppConfig.getConfigProvider().updateConfigValue(AuthCoreConfigs.CFG_AUTH_USE_SECURE_COOKIE, true);
        AuthHttpServerFilter filter = newFilter();

        CookieCapture capture = new CookieCapture();
        filter.addCookie(filter.authCookieName(), "token-value", capture.context());

        HttpCookie cookie = capture.cookies.get(0);
        assertEquals("__Host-nop-token", cookie.getName());
        assertTrue(cookie.getSecure(), "Secure should be true");
        assertTrue(cookie.isHttpOnly());
        assertEquals("/", cookie.getPath());
        assertEquals("Lax", capture.sameSite.get(cookie.getName()));
    }

    @Test
    public void testAuthCookieDevOptOutSecureOff() {
        AppConfig.getConfigProvider().updateConfigValue(AuthCoreConfigs.CFG_AUTH_USE_SECURE_COOKIE, false);
        AuthHttpServerFilter filter = newFilter();

        CookieCapture capture = new CookieCapture();
        filter.addCookie(filter.authCookieName(), "token-value", capture.context());

        HttpCookie cookie = capture.cookies.get(0);
        // 开发逃生通道（Secure=false）退回普通名称
        assertEquals("nop-token", cookie.getName());
        assertFalse(cookie.getSecure());
    }

    @Test
    public void testStateCookieHostPrefixAndSecure() {
        AppConfig.getConfigProvider().updateConfigValue(AuthCoreConfigs.CFG_AUTH_USE_SECURE_COOKIE, true);
        StateCookieHelper helper = new StateCookieHelper();
        assertEquals("__Host-" + StateCookieHelper.DEFAULT_STATE_COOKIE_NAME, helper.getCookieName());

        CookieCapture capture = new CookieCapture();
        helper.setStateCookie("state-value", capture.context());
        HttpCookie cookie = capture.cookies.get(0);
        assertTrue(cookie.getSecure());
        assertTrue(cookie.isHttpOnly());
        assertEquals("/", cookie.getPath());
        assertEquals("__Host-" + StateCookieHelper.DEFAULT_STATE_COOKIE_NAME, cookie.getName());
        assertEquals("Strict", capture.sameSite.get(cookie.getName()));
    }

    private AuthHttpServerFilter newFilter() {
        AuthHttpServerFilter filter = new AuthHttpServerFilter();
        filter.setConfig(new AuthFilterConfig());
        return filter;
    }

    private static class CookieCapture {
        final List<HttpCookie> cookies = new ArrayList<>();
        final Map<String, String> sameSite = new HashMap<>();

        IHttpServerContext context() {
            return (IHttpServerContext) Proxy.newProxyInstance(
                    IHttpServerContext.class.getClassLoader(),
                    new Class[]{IHttpServerContext.class},
                    (proxy, method, args) -> {
                        if ("addCookie".equals(method.getName())) {
                            HttpCookie c = (HttpCookie) args[1];
                            cookies.add(c);
                            sameSite.put(c.getName(), (String) args[0]);
                            return null;
                        }
                        return method.getDefaultValue();
                    });
        }
    }
}
