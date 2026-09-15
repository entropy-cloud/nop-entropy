package io.nop.ai.toolkit.tools.ssrf;

import io.nop.http.api.IDnsResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Validating {@link IDnsResolver} that rejects internal / cloud-metadata addresses and pins
 * the connection to a single validated address (DR-4a enforcement layer).
 *
 * <p>This resolver is the resolution-time SSRF enforcement of the AI HTTP tools: it is
 * consulted on every hostname resolution the tools perform. When an internal address (or
 * any address in a multi-record set that is internal) is encountered, resolution fails
 * closed by throwing {@link UnknownHostException} — no connection is established.
 *
 * <p><b>Actual assembly (P2 round-4 adjudication):</b>
 * <ul>
 *   <li>{@code HttpRequestExecutor} and {@code GraphqlQueryExecutor} hold a
 *       {@code SsrfGuardDnsResolver} as their default {@code IDnsResolver} and resolve the
 *       target hostname through it immediately before the request is sent. This closes the
 *       gap that pre-flight text checks cannot see: a real hostname that resolves to an
 *       internal address (DNS rebinding / {@code *.internal}) fails closed before the
 *       transport is reached. This is the authoritative resolution-time enforcement of the
 *       toolkit HTTP tools.</li>
 *   <li>Full redirect-hop protection (the resolver is consulted by the HTTP client itself on
 *       every new connection, including after redirects) is only available with Apache
 *       HttpClient: wire this resolver into {@code HttpClientConfig.dnsResolver} alongside a
 *       supporting client. JDK HttpClient and OkHttp do not consult
 *       {@code HttpClientConfig.dnsResolver}, so resolver-level protection does not apply to
 *       them; the executor's resolution-time check above is the only defense for those
 *       clients.</li>
 * </ul>
 */
public class SsrfGuardDnsResolver implements IDnsResolver {
    static final Logger LOG = LoggerFactory.getLogger(SsrfGuardDnsResolver.class);

    private final IDnsResolver delegate;

    /**
     * Creates a resolver that falls back to system DNS ({@code InetAddress.getAllByName})
     * for hostname resolution.
     */
    public SsrfGuardDnsResolver() {
        this.delegate = null;
    }

    /**
     * Creates a resolver that delegates actual DNS lookup to {@code delegate} and validates
     * the results. Use this constructor in tests to inject a mock resolver.
     *
     * @param delegate the underlying resolver, or {@code null} for system DNS
     */
    public SsrfGuardDnsResolver(IDnsResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        if (host == null || host.isEmpty()) {
            throw new UnknownHostException("SSRF guard: empty host");
        }

        String stripped = SsrfAddressGuard.stripBrackets(host);

        if (SsrfAddressGuard.looksLikeIpLiteral(stripped)) {
            InetAddress parsed = SsrfAddressGuard.tryParseIp(stripped);
            if (parsed == null) {
                throw new UnknownHostException("SSRF guard: unresolvable IP literal: " + host);
            }
            if (SsrfAddressGuard.isInternalAddress(parsed)) {
                throw new UnknownHostException(
                        "SSRF guard: blocked internal/metadata address: " + host
                                + " (normalized: " + parsed.getHostAddress() + ")");
            }
            return new InetAddress[]{parsed};
        }

        String blockedReason = checkBlockedHostName(stripped);
        if (blockedReason != null) {
            throw new UnknownHostException(blockedReason);
        }

        InetAddress[] addresses = doResolve(stripped);
        if (addresses == null || addresses.length == 0) {
            throw new UnknownHostException("SSRF guard: unresolved host: " + host);
        }

        for (InetAddress addr : addresses) {
            if (SsrfAddressGuard.isInternalAddress(addr)) {
                throw new UnknownHostException(
                        "SSRF guard: host " + host + " resolves to blocked address "
                                + addr.getHostAddress());
            }
        }

        return new InetAddress[]{addresses[0]};
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        if (delegate != null) {
            return delegate.resolveCanonicalHostname(host);
        }
        InetAddress[] resolved = resolve(host);
        InetAddress addr = resolved[0];
        return addr.getHostName();
    }

    private InetAddress[] doResolve(String host) throws UnknownHostException {
        if (delegate != null) {
            return delegate.resolve(host);
        }
        return InetAddress.getAllByName(host);
    }

    private static String checkBlockedHostName(String host) {
        if (SsrfAddressGuard.BLOCKED_HOSTS.contains(host)) {
            return "SSRF guard: blocked host: " + host;
        }
        if ("localhost".equals(host)) {
            return "SSRF guard: localhost not allowed";
        }
        return null;
    }
}
