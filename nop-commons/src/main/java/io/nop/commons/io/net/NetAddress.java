/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.io.net;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.annotations.data.ImmutableBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.env.DnsResolver;
import io.nop.commons.util.StringHelper;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Objects;

import static io.nop.commons.CommonErrors.ARG_HOST;
import static io.nop.commons.CommonErrors.ERR_NET_UNKNOWN_HOST;

@ImmutableBean
public class NetAddress implements Serializable {

    private static final long serialVersionUID = 7005779976439574359L;

    /**
     * Magic value indicating the absence of a port number.
     */
    private static final int NO_PORT = -1;

    /**
     * Hostname, IPv4/IPv6 literal, or unvalidated nonsense.
     */
    private final String host;

    /**
     * Validated port number in the range [0..65535], or NO_PORT
     */
    private final int port;

    private transient volatile InetSocketAddress addr; //NOSONAR

    public NetAddress(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public NetAddress(InetSocketAddress addr) {
        this.host = addr.getHostString();
        this.port = addr.getPort();
        this.addr = addr;
    }

    /**
     * Return true for valid port numbers.
     */
    public static boolean isValidPort(int port) {
        return port >= 0 && port <= 65535;
    }

    public boolean hasPort() {
        return port >= 0;
    }

    public boolean isValidPort() {
        return isValidPort(port);
    }

    public String getHost() {
        return host;
    }

    /**
     * Get the current port number, failing if no port is defined.
     *
     * @return a validated port number, in the range [0..65535]
     */
    public int getPort() {
        Guard.checkArgument(hasPort(), "net.err_no_port");
        return port;
    }

    public InetAddress getInetAddress() {
        return this.resolve().getAddress();
    }

    /**
     * Returns the current port number, with a default if no port is defined.
     */
    public int getPortOrDefault(int defaultPort) {
        return hasPort() ? port : defaultPort;
    }

    /**
     * Rebuild the host:port string, including brackets if necessary.
     */
    @Override
    public String toString() {
        // "[]:12345" requires 8 extra bytes.
        StringBuilder builder = new StringBuilder(host.length() + 8);
        if (host.indexOf(':') >= 0) {
            builder.append('[').append(host).append(']');
        } else {
            builder.append(host);
        }
        if (hasPort()) {
            builder.append(':').append(port);
        }
        return builder.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof NetAddress) {
            NetAddress that = (NetAddress) other;
            return this.port == that.port && Objects.equals(this.host, that.host);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int h = host.hashCode();
        h = h * 37 + port;
        return h;
    }

    public InetSocketAddress resolve() {
        if (addr != null)
            return addr;
        synchronized (this) {
            if (host == null || host.isEmpty()) {
                addr = new InetSocketAddress(port);
            } else {
                try {
                    InetAddress netAddr = DnsResolver.instance().resolve(host);
                    addr = new InetSocketAddress(netAddr, port);
                } catch (Exception e) {
                    throw new NopException(ERR_NET_UNKNOWN_HOST, e).param(ARG_HOST, host);
                }
            }
            return addr;
        }
    }

    /**
     * Split a freeform string into a host and port, without strict validation.
     * <p>
     * Note that the host-only formats will leave the port field undefined.
     *
     * @param hostPortString the input string to parse.
     * @return if parsing was successful, a populated HostAndPort object.
     * @throws IllegalArgumentException if nothing meaningful could be parsed.
     */
    @StaticFactoryMethod
    public static NetAddress fromString(String hostPortString) {
        Guard.notNull(hostPortString, "net.err_null_hostPort_string");
        String host;
        String portString = "";

        if (hostPortString.startsWith("[")) {
            String[] hostAndPort = getHostAndPortFromBracketedHost(hostPortString);
            host = hostAndPort[0];
            portString = hostAndPort[1];
        } else {
            int colonPos = hostPortString.indexOf(':');
            if (colonPos >= 0 && hostPortString.indexOf(':', colonPos + 1) == -1) {
                // Exactly 1 colon. Split into host:port.
                host = hostPortString.substring(0, colonPos);
                portString = hostPortString.substring(colonPos + 1);
            } else {
                // 0 or 2+ colons. Bare hostname or IPv6 literal.
                host = hostPortString;
            }
        }

        int port = NO_PORT;
        if (!StringHelper.isEmpty(portString)) {
            // Try to parse the whole port string as a number.
            // JDK7 accepts leading plus signs. We don't want to.
            Guard.checkArgument(!portString.startsWith("+"), "Unparseable port number", hostPortString);
            try {
                port = Integer.parseInt(portString);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Unparseable port number: " + hostPortString);
            }
            Guard.checkArgument(isValidPort(port), "Port number out of range", hostPortString);
        }

        return new NetAddress(host, port);
    }

    /**
     * Parses a bracketed host-port string, throwing IllegalArgumentException if parsing fails.
     *
     * @param hostPortString the full bracketed host-port specification. Post might not be specified.
     * @return an array with 2 strings: host and port, in that order.
     * @throws IllegalArgumentException if parsing the bracketed host-port string fails.
     */
    private static String[] getHostAndPortFromBracketedHost(String hostPortString) {
        int colonIndex = 0;
        int closeBracketIndex = 0;
        Guard.checkArgument(hostPortString.charAt(0) == '[', "Bracketed host-port string must start with a bracket",
                hostPortString);
        colonIndex = hostPortString.indexOf(':');
        closeBracketIndex = hostPortString.lastIndexOf(']');
        Guard.checkArgument(colonIndex > -1 && closeBracketIndex > colonIndex, "Invalid bracketed host/port",
                hostPortString);

        String host = hostPortString.substring(1, closeBracketIndex);
        if (closeBracketIndex + 1 == hostPortString.length()) {
            return new String[]{host, ""};
        } else {
            Guard.checkArgument(hostPortString.charAt(closeBracketIndex + 1) == ':',
                    "Only a colon may follow a close bracket", hostPortString);
            for (int i = closeBracketIndex + 2; i < hostPortString.length(); ++i) {
                Guard.checkArgument(Character.isDigit(hostPortString.charAt(i)), "Port must be numeric",
                        hostPortString);
            }
            return new String[]{host, hostPortString.substring(closeBracketIndex + 2)};
        }
    }
}