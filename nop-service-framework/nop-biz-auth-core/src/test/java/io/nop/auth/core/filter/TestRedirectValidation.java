package io.nop.auth.core.filter;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证开放重定向防护（M-1）与 __Host- cookie 前缀的纯逻辑部分。
 * cookie 属性（依赖运行期配置）见 nop-auth-service 下的 TestAuthBrowserHardening。
 */
public class TestRedirectValidation {

    private AuthHttpServerFilter newFilter(AuthFilterConfig config) {
        AuthHttpServerFilter filter = new AuthHttpServerFilter();
        filter.setConfig(config);
        return filter;
    }

    @Test
    public void testProtocolRelativeAndBackslashRedirectsRejected() {
        AuthHttpServerFilter filter = newFilter(new AuthFilterConfig());

        for (String evil : new String[]{"//evil.com/x", "/\\evil.com", "/\\\\evil.com",
                "/\tevil", "/foo\r\n", "http://evil.com/", "\\evil.com"}) {
            assertFalse(filter.isAllowedRedirectUri(evil), "should be rejected: " + evil);
        }
    }

    @Test
    public void testSafeRelativeAndAllowedPrefixAccepted() {
        AuthFilterConfig config = new AuthFilterConfig();
        config.setAllowedRedirectPrefixes(Collections.singletonList("https://app.example.com/"));
        AuthHttpServerFilter filter = newFilter(config);

        assertTrue(filter.isAllowedRedirectUri("/dashboard"));
        assertTrue(filter.isAllowedRedirectUri("/a/b/c?x=1"));
        assertTrue(filter.isAllowedRedirectUri("https://app.example.com/home"));
        assertFalse(filter.isAllowedRedirectUri("https://evil.com/home"));
        assertFalse(filter.isAllowedRedirectUri(""));
        assertFalse(filter.isAllowedRedirectUri(null));
    }

    @Test
    public void testHostPrefixLogic() {
        assertEquals("__Host-nop-token",
                AuthHttpServerFilter.hostPrefixedCookieName("nop-token", true));
        assertEquals("nop-token",
                AuthHttpServerFilter.hostPrefixedCookieName("nop-token", false));
        assertEquals("__Host-x",
                AuthHttpServerFilter.hostPrefixedCookieName("__Host-x", true));
    }
}
