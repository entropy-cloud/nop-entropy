package io.nop.ai.toolkit.tools.ssrf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SsrfAddressGuard}. Covers encoded-IP normalization, internal-address
 * detection, and IP-literal detection heuristics required by DR-4a Phase 1/2.
 */
class TestSsrfAddressGuard {

    @Test
    void testBlockedHosts() {
        assertNotNull(SsrfAddressGuard.validateHost("169.254.169.254"));
        assertNotNull(SsrfAddressGuard.validateHost("metadata.google.internal"));
        assertNotNull(SsrfAddressGuard.validateHost("100.100.100.200"));
    }

    @Test
    void testLocalhostBlocked() {
        assertNotNull(SsrfAddressGuard.validateHost("localhost"));
        assertNotNull(SsrfAddressGuard.validateHost("LOCALHOST"));
    }

    @Test
    void testPublicHostAllowed() {
        assertNull(SsrfAddressGuard.validateHost("example.com"));
        assertNull(SsrfAddressGuard.validateHost("api.openai.com"));
    }

    @Test
    void testStandardInternalIpv4Blocked() {
        assertNotNull(SsrfAddressGuard.validateHost("127.0.0.1"));
        assertNotNull(SsrfAddressGuard.validateHost("10.0.0.1"));
        assertNotNull(SsrfAddressGuard.validateHost("172.16.0.1"));
        assertNotNull(SsrfAddressGuard.validateHost("172.31.255.255"));
        assertNotNull(SsrfAddressGuard.validateHost("192.168.1.1"));
        assertNotNull(SsrfAddressGuard.validateHost("0.0.0.0"));
        assertNotNull(SsrfAddressGuard.validateHost("169.254.1.1"));
    }

    @Test
    void testStandardInternalIpv4BoundaryAllowed() {
        assertNull(SsrfAddressGuard.validateHost("172.15.0.1"));
        assertNull(SsrfAddressGuard.validateHost("172.32.0.1"));
        assertNull(SsrfAddressGuard.validateHost("8.8.8.8"));
    }

    @Test
    void testEncodedDecimalIpv4Blocked() {
        String reason = SsrfAddressGuard.validateHost("2130706433");
        assertNotNull(reason, "decimal-encoded 127.0.0.1 must be blocked");
        assertTrue(reason.contains("normalized"));
    }

    @Test
    void testEncodedHexIpv4Blocked() {
        String reason = SsrfAddressGuard.validateHost("0x7f000001");
        assertNotNull(reason, "hex-encoded 127.0.0.1 must be blocked (parsed or unresolvable)");
    }

    @Test
    void testIpv6LoopbackBlocked() {
        assertNotNull(SsrfAddressGuard.validateHost("::1"));
        assertNotNull(SsrfAddressGuard.validateHost("[::1]"));
    }

    @Test
    void testIpv6LinkLocalBlocked() {
        assertNotNull(SsrfAddressGuard.validateHost("fe80::1"));
    }

    @Test
    void testIpv6UniqueLocalBlocked() {
        assertNotNull(SsrfAddressGuard.validateHost("fc00::1"));
        assertNotNull(SsrfAddressGuard.validateHost("fd00::1"));
    }

    @Test
    void testIpv6MappedIpv4Blocked() {
        String reason = SsrfAddressGuard.validateHost("::ffff:127.0.0.1");
        assertNotNull(reason, "IPv6-mapped 127.0.0.1 must be blocked");
        assertTrue(reason.contains("normalized"));
    }

    @Test
    void testIpv6MappedIpv4WithBracketsBlocked() {
        assertNotNull(SsrfAddressGuard.validateHost("[::ffff:10.0.0.1]"));
    }

    @Test
    void testCarrierGradeNatBlocked() {
        assertNotNull(SsrfAddressGuard.validateHost("100.64.0.1"));
        assertNotNull(SsrfAddressGuard.validateHost("100.127.255.255"));
    }

    @Test
    void testCarrierGradeNatBoundaryAllowed() {
        assertNull(SsrfAddressGuard.validateHost("100.63.255.255"));
        assertNull(SsrfAddressGuard.validateHost("100.128.0.1"));
    }

    @Test
    void testEncodedDecimalIpv4PublicAllowed() {
        String reason = SsrfAddressGuard.validateHost("134744072");
        assertNull(reason, "decimal-encoded 8.8.8.8 should be allowed");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1", "2130706433", "0x7f000001", "017700000001",
            "::1", "[::1]", "::ffff:127.0.0.1", "fe80::1", "fc00::1",
            "10.0.0.1", "192.168.1.1", "169.254.169.254", "0.0.0.0"
    })
    void testLooksLikeIpLiteralTrue(String host) {
        String stripped = SsrfAddressGuard.stripBrackets(host.toLowerCase());
        assertTrue(SsrfAddressGuard.looksLikeIpLiteral(stripped),
                "expected '" + host + "' to be classified as IP literal");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "example.com", "api.openai.com", "localhost",
            "metadata.google.internal", "sub-domain.example.org"
    })
    void testLooksLikeIpLiteralFalse(String host) {
        assertFalse(SsrfAddressGuard.looksLikeIpLiteral(host),
                "hostname '" + host + "' must NOT be classified as IP literal");
    }

    @Test
    void testIsInternalAddressDirectly() throws Exception {
        assertTrue(SsrfAddressGuard.isInternalAddress(InetAddress.getByName("127.0.0.1")));
        assertTrue(SsrfAddressGuard.isInternalAddress(InetAddress.getByName("10.0.0.1")));
        assertTrue(SsrfAddressGuard.isInternalAddress(InetAddress.getByName("172.16.0.1")));
        assertTrue(SsrfAddressGuard.isInternalAddress(InetAddress.getByName("192.168.1.1")));
        assertTrue(SsrfAddressGuard.isInternalAddress(InetAddress.getByName("169.254.1.1")));
        assertTrue(SsrfAddressGuard.isInternalAddress(InetAddress.getByName("0.0.0.0")));
        assertTrue(SsrfAddressGuard.isInternalAddress(InetAddress.getByName("::1")));
        assertTrue(SsrfAddressGuard.isInternalAddress(InetAddress.getByName("fe80::1")));
        assertTrue(SsrfAddressGuard.isInternalAddress(InetAddress.getByName("fc00::1")));

        assertFalse(SsrfAddressGuard.isInternalAddress(InetAddress.getByName("8.8.8.8")));
        assertFalse(SsrfAddressGuard.isInternalAddress(InetAddress.getByName("1.1.1.1")));
    }

    @Test
    void testNullSafe() {
        assertNotNull(SsrfAddressGuard.validateHost(null));
        assertNotNull(SsrfAddressGuard.validateHost(""));
        assertTrue(SsrfAddressGuard.isInternalAddress(null));
    }
}
