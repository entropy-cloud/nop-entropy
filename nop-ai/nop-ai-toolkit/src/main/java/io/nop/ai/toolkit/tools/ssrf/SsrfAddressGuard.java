package io.nop.ai.toolkit.tools.ssrf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * SSRF defense utility shared by {@code HttpRequestExecutor}, {@code GraphqlQueryExecutor},
 * and {@code SsrfGuardDnsResolver}. Centralizes three concerns:
 *
 * <ol>
 *   <li><b>Encoded-IP normalization</b> — decimal, hex, octal IPv4 and IPv6-mapped forms
 *       are normalized to an {@link InetAddress} so that the internal/external verdict is the
 *       same as the literal dotted/colon form. Java {@code InetAddress.getByName()} handles
 *       this normalization locally (no DNS) for IP literals.</li>
 *   <li><b>Internal / blocked-address detection</b> — covers loopback, private, link-local,
 *       any-local, IPv6 ULA, carrier-grade NAT, and cloud-metadata endpoints.</li>
 *   <li><b>IP-literal detection</b> — a conservative heuristic that decides whether a host
 *       string is safe to pass to {@code InetAddress.getByName()} without triggering DNS.
 *       Real hostnames always contain a non-hex letter or a dash and are therefore excluded.</li>
 * </ol>
 */
public final class SsrfAddressGuard {
    static final Logger LOG = LoggerFactory.getLogger(SsrfAddressGuard.class);

    /**
     * Cloud-metadata endpoints and well-known SSRF targets that must be blocked by host text
     * regardless of IP-literal parsing.
     */
    public static final Set<String> BLOCKED_HOSTS = Set.of(
            "169.254.169.254", "169.254.170.2", "fd00:ec2::23", "100.100.100.200",
            "metadata.google.internal", "169.254.169.253",
            "metadata", "metadata.azure.com"
    );

    private SsrfAddressGuard() {
    }

    /**
     * Validates a raw host string extracted from a URL. Returns {@code null} when the host is
     * acceptable, or a human-readable error message when it should be blocked.
     *
     * <p>This is the pre-flight defense-in-depth text check. The authoritative
     * resolution-time enforcement lives in {@link SsrfGuardDnsResolver}, which the toolkit
     * HTTP tools ({@code HttpRequestExecutor} / {@code GraphqlQueryExecutor}) consume as
     * their default {@code IDnsResolver} right before sending each request: it validates
     * resolved addresses (catching DNS rebinding and internal-address resolution that
     * pre-flight text checks cannot see) and fails closed.
     *
     * @param host the host portion of a URL (may include IPv6 brackets)
     * @return {@code null} if acceptable, error message if blocked
     */
    public static String validateHost(String host) {
        if (host == null || host.isEmpty()) {
            return "No host in URL";
        }

        String lowerHost = host.toLowerCase();
        String strippedHost = stripBrackets(lowerHost);

        if (BLOCKED_HOSTS.contains(strippedHost)) {
            return "Blocked host: " + host;
        }
        if ("localhost".equals(strippedHost)) {
            return "Internal host not allowed: " + host;
        }

        if (looksLikeIpLiteral(strippedHost)) {
            InetAddress parsed = tryParseIp(strippedHost);
            if (parsed == null) {
                return "Unresolvable IP literal: " + host;
            }
            if (isInternalAddress(parsed)) {
                return "Internal/private IP addresses are not allowed: " + host
                        + " (normalized: " + parsed.getHostAddress() + ")";
            }
        }

        return null;
    }

    /**
     * Determines whether an already-resolved {@link InetAddress} is internal, loopback,
     * link-local, or a known cloud-metadata address.
     */
    public static boolean isInternalAddress(InetAddress addr) {
        if (addr == null) {
            return true;
        }
        if (addr.isAnyLocalAddress()) {
            return true;
        }
        if (addr.isLoopbackAddress()) {
            return true;
        }
        if (addr.isLinkLocalAddress()) {
            return true;
        }
        if (addr.isSiteLocalAddress()) {
            return true;
        }
        if (addr.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = addr.getAddress();
        if (bytes == null) {
            return true;
        }

        if (bytes.length == 4) {
            return isInternalIpv4(bytes);
        }
        if (bytes.length == 16) {
            return isInternalIpv6(bytes);
        }
        return true;
    }

    /**
     * Conservative heuristic: returns {@code true} when the string contains only characters
     * that could form an IP literal (digits, dots, colons, hex letters a–f, and encoding
     * prefixes 0x/0b/0o). A real hostname always contains at least one non-hex letter
     * (g–z) or a dash, so it is never misclassified.
     *
     * <p>When this method returns {@code true}, it is safe to call
     * {@code InetAddress.getByName()} on the string without triggering a DNS lookup.
     */
    public static boolean looksLikeIpLiteral(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        if (host.indexOf(':') >= 0) {
            return true;
        }
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (!isIpLiteralChar(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parses a host string that {@link #looksLikeIpLiteral} has already classified as an IP
     * literal. Returns the normalized {@link InetAddress}, or {@code null} if parsing fails.
     * Does NOT trigger DNS — only local parsing for IP literals.
     */
    public static InetAddress tryParseIp(String host) {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    static String stripBrackets(String host) {
        if (host == null) {
            return null;
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private static boolean isInternalIpv4(byte[] b) {
        int first = b[0] & 0xFF;

        if (first == 0) {
            return true;
        }
        if (first == 10) {
            return true;
        }
        if (first == 127) {
            return true;
        }
        if (first == 169 && (b[1] & 0xFF) == 254) {
            return true;
        }
        if (first == 172) {
            int second = b[1] & 0xFF;
            if (second >= 16 && second <= 31) {
                return true;
            }
        }
        if (first == 192 && (b[1] & 0xFF) == 168) {
            return true;
        }
        if (first == 100) {
            int second = b[1] & 0xFF;
            if (second >= 64 && second <= 127) {
                return true;
            }
        }

        return false;
    }

    private static boolean isInternalIpv6(byte[] b) {
        if ((b[0] & 0xFE) == 0xFC) {
            return true;
        }
        boolean loopback = true;
        for (int i = 0; i < 15; i++) {
            if (b[i] != 0) {
                loopback = false;
                break;
            }
        }
        if (loopback && b[15] == 1) {
            return true;
        }
        if (b[0] == (byte) 0xFE && (b[1] & 0xC0) == 0x80) {
            return true;
        }

        if (b[0] == 0 && b[1] == 0 && b[2] == 0 && b[3] == 0
                && b[4] == 0 && b[5] == 0 && b[6] == 0 && b[7] == 0
                && b[8] == 0 && b[9] == 0 && b[10] == 0xFF
                && b[11] == 0xFF) {
            byte[] v4 = new byte[4];
            v4[0] = b[12];
            v4[1] = b[13];
            v4[2] = b[14];
            v4[3] = b[15];
            if (isInternalIpv4(v4)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isIpLiteralChar(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F')
                || c == '.'
                || c == 'x' || c == 'X'
                || c == 'b' || c == 'B'
                || c == 'o' || c == 'O';
    }
}
