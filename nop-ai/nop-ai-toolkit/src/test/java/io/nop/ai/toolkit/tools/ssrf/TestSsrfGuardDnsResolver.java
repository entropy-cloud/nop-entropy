package io.nop.ai.toolkit.tools.ssrf;

import io.nop.http.api.IDnsResolver;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SsrfGuardDnsResolver}. Validates fail-closed behavior for internal
 * addresses, multi-address (DNS-rebinding) rejection, encoded-IP normalization, and public-host
 * pass-through.
 */
class TestSsrfGuardDnsResolver {

    private InetAddress addr(String ip) throws UnknownHostException {
        return InetAddress.getByName(ip);
    }

    /**
     * Minimal mock IDnsResolver whose resolve result can be configured per-test.
     */
    private static class MockResolver implements IDnsResolver {
        final InetAddress[] result;
        final UnknownHostException error;

        MockResolver(InetAddress[] result) {
            this.result = result;
            this.error = null;
        }

        MockResolver(UnknownHostException error) {
            this.result = null;
            this.error = error;
        }

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            if (error != null) throw error;
            return result;
        }

        @Override
        public String resolveCanonicalHostname(String host) {
            return host;
        }
    }

    @Test
    void testInternalIpLiteralRejected() {
        SsrfGuardDnsResolver resolver = new SsrfGuardDnsResolver();
        assertThrows(UnknownHostException.class, () -> resolver.resolve("127.0.0.1"));
        assertThrows(UnknownHostException.class, () -> resolver.resolve("10.0.0.1"));
        assertThrows(UnknownHostException.class, () -> resolver.resolve("169.254.169.254"));
        assertThrows(UnknownHostException.class, () -> resolver.resolve("::1"));
    }

    @Test
    void testEncodedIpLiteralRejected() {
        SsrfGuardDnsResolver resolver = new SsrfGuardDnsResolver();
        assertThrows(UnknownHostException.class, () -> resolver.resolve("2130706433"));
        assertThrows(UnknownHostException.class, () -> resolver.resolve("0x7f000001"));
        assertThrows(UnknownHostException.class, () -> resolver.resolve("::ffff:127.0.0.1"));
    }

    @Test
    void testPublicIpLiteralAllowedAndPinned() throws Exception {
        SsrfGuardDnsResolver resolver = new SsrfGuardDnsResolver();
        InetAddress[] result = resolver.resolve("8.8.8.8");
        assertEquals(1, result.length, "resolver must pin to a single address");
        assertEquals("8.8.8.8", result[0].getHostAddress());
    }

    @Test
    void testBlockedHostNameRejected() {
        SsrfGuardDnsResolver resolver = new SsrfGuardDnsResolver();
        assertThrows(UnknownHostException.class, () -> resolver.resolve("localhost"));
        assertThrows(UnknownHostException.class, () -> resolver.resolve("metadata.google.internal"));
    }

    @Test
    void testPublicHostNameResolvedAndPinned() throws Exception {
        MockResolver mock = new MockResolver(new InetAddress[]{
                addr("93.184.216.34"),
                addr("93.184.216.35")
        });
        SsrfGuardDnsResolver resolver = new SsrfGuardDnsResolver(mock);
        InetAddress[] result = resolver.resolve("example.com");
        assertEquals(1, result.length, "resolver must pin to a single validated address");
    }

    @Test
    void testDnsRebindingMixedSetRejected() throws Exception {
        MockResolver mock = new MockResolver(new InetAddress[]{
                addr("93.184.216.34"),
                addr("127.0.0.1")
        });
        SsrfGuardDnsResolver resolver = new SsrfGuardDnsResolver(mock);
        UnknownHostException ex = assertThrows(UnknownHostException.class,
                () -> resolver.resolve("evil.com"));
        assertTrue(ex.getMessage().contains("blocked address"));
    }

    @Test
    void testDnsRebindingAllInternalRejected() throws Exception {
        MockResolver mock = new MockResolver(new InetAddress[]{
                addr("10.0.0.1"),
                addr("10.0.0.2")
        });
        SsrfGuardDnsResolver resolver = new SsrfGuardDnsResolver(mock);
        assertThrows(UnknownHostException.class, () -> resolver.resolve("internal.com"));
    }

    @Test
    void testUnresolvedHostFailsClosed() {
        MockResolver mock = new MockResolver(new InetAddress[0]);
        SsrfGuardDnsResolver resolver = new SsrfGuardDnsResolver(mock);
        assertThrows(UnknownHostException.class, () -> resolver.resolve("nonexistent.example"));
    }

    @Test
    void testNullDelegateFallsBackToSystemDns() {
        SsrfGuardDnsResolver resolver = new SsrfGuardDnsResolver((IDnsResolver) null);
        assertNotNull(resolver);
    }

    @Test
    void testEmptyHostFailsClosed() {
        SsrfGuardDnsResolver resolver = new SsrfGuardDnsResolver();
        assertThrows(UnknownHostException.class, () -> resolver.resolve(""));
        assertThrows(UnknownHostException.class, () -> resolver.resolve(null));
    }

    @Test
    void testResolveRedirectTarget() {
        SsrfGuardDnsResolver resolver = new SsrfGuardDnsResolver();
        UnknownHostException ex = assertThrows(UnknownHostException.class,
                () -> resolver.resolve("169.254.169.254"));
        assertTrue(ex.getMessage().contains("metadata") || ex.getMessage().contains("blocked"));
    }
}
