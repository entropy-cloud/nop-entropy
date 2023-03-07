/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.commons.util;

// copy from spring framework SocketUtils

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

import static io.nop.commons.CommonConfigs.CFG_NET_IGNORE_INTERFACE_PATTERN;
import static io.nop.commons.CommonConfigs.CFG_NET_PREFER_NETWORK_PATTERN;
import static io.nop.commons.CommonConfigs.CFG_NET_USE_ONLY_SITE_LOCAL_INTERFACES;
import static io.nop.commons.CommonErrors.ARG_IP;
import static io.nop.commons.CommonErrors.ERR_NET_INVALID_IP_STRING;

public class NetHelper {
    static final Logger LOG = LoggerFactory.getLogger(NetHelper.class);

    /**
     * The default minimum value for port ranges used when finding an available socket port.
     */
    public static final int PORT_RANGE_MIN = 1024;

    /**
     * The default maximum value for port ranges used when finding an available socket port.
     */
    public static final int PORT_RANGE_MAX = 65535;

    /**
     * The {@link Inet4Address} that represents the IPv4 loopback address '127.0.0.1'
     */
    private static Inet4Address LOCALHOST4;

    /**
     * The {@link Inet6Address} that represents the IPv6 loopback address '::1'
     */
    private static Inet6Address LOCALHOST6;

    /**
     * GraalVM不允许静态创建InetAddress以及其派生类，必须动态创建
     */
    public static Inet4Address LOCALHOST4(){
        if(LOCALHOST4 == null){
            _initLocalHost();
        }
        return LOCALHOST4;
    }

    public static InetAddress LOCALHOST6(){
        if(LOCALHOST6 == null){
            _initLocalHost();
        }
        return LOCALHOST6;
    }

    private static void _initLocalHost(){
        byte[] LOCALHOST4_BYTES = {127, 0, 0, 1};
        byte[] LOCALHOST6_BYTES = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};

        // Create IPv4 loopback address.
        Inet4Address localhost4 = null;
        try {
            localhost4 = (Inet4Address) InetAddress.getByAddress("localhost", LOCALHOST4_BYTES);
        } catch (Exception e) {
            // We should not get here as long as the length of the address is
            // correct.
            LOG.error("util.err_invalid_localhost_addr", e);
        }
        LOCALHOST4 = localhost4;

        // Create IPv6 loopback address.
        Inet6Address localhost6 = null;
        try {
            localhost6 = (Inet6Address) InetAddress.getByAddress("localhost", LOCALHOST6_BYTES);
        } catch (Exception e) {
            // We should not get here as long as the length of the address is
            // correct.
            LOG.error("nop.err.commons.util.invalid-localhost", e);
        }
        LOCALHOST6 = localhost6;
    }


    // s private static final Random random = new
    // Random(System.currentTimeMillis());

    /**
     * Find an available TCP port randomly selected from the range [{@value #PORT_RANGE_MIN}, {@value #PORT_RANGE_MAX}].
     *
     * @return an available TCP port number
     * @throws IllegalStateException if no available port could be found
     */
    public static int findAvailableTcpPort() {
        return findAvailableTcpPort(PORT_RANGE_MIN);
    }

    /**
     * Find an available TCP port randomly selected from the range [{@code minPort}, {@value #PORT_RANGE_MAX}].
     *
     * @param minPort the minimum port number
     * @return an available TCP port number
     * @throws IllegalStateException if no available port could be found
     */
    public static int findAvailableTcpPort(int minPort) {
        return findAvailableTcpPort(minPort, PORT_RANGE_MAX);
    }

    /**
     * Find an available TCP port randomly selected from the range [{@code minPort}, {@code maxPort}].
     *
     * @param minPort the minimum port number
     * @param maxPort the maximum port number
     * @return an available TCP port number
     * @throws IllegalStateException if no available port could be found
     */
    public static int findAvailableTcpPort(int minPort, int maxPort) {
        return SocketType.TCP.findAvailablePort(minPort, maxPort);
    }

    /**
     * Find the requested number of available TCP ports, each randomly selected from the range
     * [{@value #PORT_RANGE_MIN}, {@value #PORT_RANGE_MAX}].
     *
     * @param numRequested the number of available ports to find
     * @return a sorted set of available TCP port numbers
     * @throws IllegalStateException if the requested number of available ports could not be found
     */
    public static SortedSet<Integer> findAvailableTcpPorts(int numRequested) {
        return findAvailableTcpPorts(numRequested, PORT_RANGE_MIN, PORT_RANGE_MAX);
    }

    /**
     * Find the requested number of available TCP ports, each randomly selected from the range [{@code minPort},
     * {@code maxPort}].
     *
     * @param numRequested the number of available ports to find
     * @param minPort      the minimum port number
     * @param maxPort      the maximum port number
     * @return a sorted set of available TCP port numbers
     * @throws IllegalStateException if the requested number of available ports could not be found
     */
    public static SortedSet<Integer> findAvailableTcpPorts(int numRequested, int minPort, int maxPort) {
        return SocketType.TCP.findAvailablePorts(numRequested, minPort, maxPort);
    }

    /**
     * Find an available UDP port randomly selected from the range [{@value #PORT_RANGE_MIN}, {@value #PORT_RANGE_MAX}].
     *
     * @return an available UDP port number
     * @throws IllegalStateException if no available port could be found
     */
    public static int findAvailableUdpPort() {
        return findAvailableUdpPort(PORT_RANGE_MIN);
    }

    /**
     * Find an available UDP port randomly selected from the range [{@code minPort}, {@value #PORT_RANGE_MAX}].
     *
     * @param minPort the minimum port number
     * @return an available UDP port number
     * @throws IllegalStateException if no available port could be found
     */
    public static int findAvailableUdpPort(int minPort) {
        return findAvailableUdpPort(minPort, PORT_RANGE_MAX);
    }

    /**
     * Find an available UDP port randomly selected from the range [{@code minPort}, {@code maxPort}].
     *
     * @param minPort the minimum port number
     * @param maxPort the maximum port number
     * @return an available UDP port number
     * @throws IllegalStateException if no available port could be found
     */
    public static int findAvailableUdpPort(int minPort, int maxPort) {
        return SocketType.UDP.findAvailablePort(minPort, maxPort);
    }

    /**
     * Find the requested number of available UDP ports, each randomly selected from the range
     * [{@value #PORT_RANGE_MIN}, {@value #PORT_RANGE_MAX}].
     *
     * @param numRequested the number of available ports to find
     * @return a sorted set of available UDP port numbers
     * @throws IllegalStateException if the requested number of available ports could not be found
     */
    public static SortedSet<Integer> findAvailableUdpPorts(int numRequested) {
        return findAvailableUdpPorts(numRequested, PORT_RANGE_MIN, PORT_RANGE_MAX);
    }

    /**
     * Find the requested number of available UDP ports, each randomly selected from the range [{@code minPort},
     * {@code maxPort}].
     *
     * @param numRequested the number of available ports to find
     * @param minPort      the minimum port number
     * @param maxPort      the maximum port number
     * @return a sorted set of available UDP port numbers
     * @throws IllegalStateException if the requested number of available ports could not be found
     */
    public static SortedSet<Integer> findAvailableUdpPorts(int numRequested, int minPort, int maxPort) {
        return SocketType.UDP.findAvailablePorts(numRequested, minPort, maxPort);
    }

    private enum SocketType {

        TCP {
            @Override
            protected boolean isPortAvailable(int port) {
                try {
                    ServerSocket serverSocket = ServerSocketFactory.getDefault().createServerSocket(port);
                    serverSocket.close();
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            }
        },

        UDP {
            @Override
            protected boolean isPortAvailable(int port) {
                try {
                    DatagramSocket socket = new DatagramSocket(port);
                    socket.close();
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            }
        };

        /**
         * Determine if the specified port for this {@code SocketType} is currently available on {@code localhost}.
         */
        protected abstract boolean isPortAvailable(int port);

        /**
         * Find a pseudo-random port number within the range [{@code minPort}, {@code maxPort}].
         *
         * @param minPort the minimum port number
         * @param maxPort the maximum port number
         * @return a random port number within the specified range
         */
        private int findRandomPort(int minPort, int maxPort) {
            int portRange = maxPort - minPort;
            return minPort + MathHelper.random().nextInt(portRange);
        }

        /**
         * Find an available port for this {@code SocketType}, randomly selected from the range [{@code minPort},
         * {@code maxPort}].
         *
         * @param minPort the minimum port number
         * @param maxPort the maximum port number
         * @return an available port number for this socket type
         * @throws IllegalStateException if no available port could be found
         */
        int findAvailablePort(int minPort, int maxPort) {
            Guard.checkArgument(minPort > 0, "'minPort' must be greater than 0");
            Guard.checkArgument(maxPort > minPort, "'maxPort' must be greater than 'minPort'");
            Guard.checkArgument(maxPort <= PORT_RANGE_MAX, "'maxPort' must be less than or equal to " + PORT_RANGE_MAX);

            int portRange = maxPort - minPort;
            int candidatePort;
            int searchCounter = 0;
            do {
                if (++searchCounter > portRange) {
                    throw new IllegalStateException(
                            String.format("Could not find an available %s port in the range [%d, %d] after %d attempts",
                                    name(), minPort, maxPort, searchCounter));
                }
                candidatePort = findRandomPort(minPort, maxPort);
            } while (!isPortAvailable(candidatePort));

            return candidatePort;
        }

        /**
         * Find the requested number of available ports for this {@code SocketType}, each randomly selected from the
         * range [{@code minPort}, {@code maxPort}].
         *
         * @param numRequested the number of available ports to find
         * @param minPort      the minimum port number
         * @param maxPort      the maximum port number
         * @return a sorted set of available port numbers for this socket type
         * @throws IllegalStateException if the requested number of available ports could not be found
         */
        SortedSet<Integer> findAvailablePorts(int numRequested, int minPort, int maxPort) {
            Guard.checkArgument(minPort > 0, "'minPort' must be greater than 0");
            Guard.checkArgument(maxPort > minPort, "'maxPort' must be greater than 'minPort'");
            Guard.checkArgument(maxPort <= PORT_RANGE_MAX, "'maxPort' must be less than or equal to " + PORT_RANGE_MAX);
            Guard.checkArgument(numRequested > 0, "'numRequested' must be greater than 0");
            Guard.checkArgument((maxPort - minPort) >= numRequested,
                    "'numRequested' must not be greater than 'maxPort' - 'minPort'");

            final SortedSet<Integer> availablePorts = new TreeSet<>();
            int attemptCount = 0;
            while ((++attemptCount <= numRequested + 100) && (availablePorts.size() < numRequested)) {
                availablePorts.add(findAvailablePort(minPort, maxPort));
            }

            if (availablePorts.size() != numRequested) {
                throw new IllegalStateException(
                        String.format("Could not find %d available %s ports in the range [%d, %d]", numRequested,
                                name(), minPort, maxPort));
            }

            return availablePorts;
        }
    }

    public static String findLocalIp() {
        InetAddress addr = findFirstNonLoopbackAddress();
        return addr == null ? LOCALHOST4.getHostAddress() : addr.getHostAddress();
    }

    public static InetAddress findFirstNonLoopbackAddress() {
        InetAddress result = null;
        try {
            int lowest = Integer.MAX_VALUE;
            for (Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces(); nics
                    .hasMoreElements(); ) {
                NetworkInterface ifc = nics.nextElement();
                if (ifc.isUp()) {
                    LOG.trace("Testing interface:{}", ifc.getDisplayName());
                    if (ifc.getIndex() < lowest || result == null) {
                        lowest = ifc.getIndex();
                    } else {
                        continue;
                    }

                    // @formatter:off
                    if (!ignoreInterface(ifc.getDisplayName())) {
                        for (Enumeration<InetAddress> addrs = ifc.getInetAddresses(); addrs.hasMoreElements(); ) {
                            InetAddress address = addrs.nextElement();
                            if (address instanceof Inet4Address && !address.isLoopbackAddress()
                                    && !ignoreAddress(address)) {
                                LOG.trace("Found non-loopback interface:{}", ifc.getDisplayName());
                                result = address;
                            }
                        }
                    }
                    // @formatter:on
                }
            }
        } catch (IOException ex) {
            LOG.error("Cannot get first non-loopback address", ex);
        }

        if (result != null) {
            return result;
        }

        try {
            return InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            LOG.warn("Unable to retrieve localhost");
        }

        return null;
    }

    /**
     * for testing
     */
    static boolean ignoreAddress(InetAddress address) {
        if (CFG_NET_USE_ONLY_SITE_LOCAL_INTERFACES.get() && !address.isSiteLocalAddress()) {
            LOG.trace("Ignoring address: {}", address.getHostAddress());
            return true;
        }

        String pattern = CFG_NET_PREFER_NETWORK_PATTERN.get();
        if (!StringHelper.isEmpty(pattern)) {
            if (!StringHelper.matchSimplePattern(address.getHostAddress(), pattern)) {
                LOG.trace("Ignoring address: {}", address.getHostAddress());
                return true;
            }
        }

        return false;
    }

    /**
     * for testing
     */
    static boolean ignoreInterface(String interfaceName) {
        String pattern = CFG_NET_IGNORE_INTERFACE_PATTERN.get();

        if (pattern != null && !pattern.isEmpty() && StringHelper.matchSimplePattern(interfaceName, pattern)) {
            LOG.trace("Ignoring interface:{}", interfaceName);
            return true;
        }
        return false;
    }

    public static final int IPV4_PART_COUNT = 4;
    public static final int IPV6_PART_COUNT = 8;

    public static final int IPV6_BYTES_LENGTH = 16;
    public static final int IPV4_BYTES_LENGTH = 4;

    public static InetAddress intToInetAddress(int value) {
        byte[] bytes = new byte[]{(byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value};
        return bytesToInetAddress(bytes);
    }

    /**
     * Returns the {@link InetAddress} having the given string representation.
     *
     * <p>
     * This deliberately avoids all nameservice lookups (e.g. no DNS).
     *
     * @param ipString {@code String} containing an IPv4 or IPv6 string literal, e.g. {@code "192.168.0.1"} or
     *                 {@code "2001:db8::1"}
     * @return {@link InetAddress} representing the argument
     * @throws IllegalArgumentException if the argument is not a valid IP string literal
     */
    public static InetAddress stringToInetAddress(String ipString) {
        byte[] addr = ipStringToBytes(ipString);

        // The argument was malformed, i.e. not an IP string literal.
        if (addr == null) {
            throw new NopException(ERR_NET_INVALID_IP_STRING).param(ARG_IP, ipString);
        }

        return bytesToInetAddress(addr);
    }

    public static boolean isIpV6Address(String ipString) {
        byte[] bytes = ipStringToBytes(ipString);
        return bytes != null && bytes.length == IPV6_BYTES_LENGTH;
    }

    public static boolean isIpV4Address(String ipString) {
        byte[] bytes = ipStringToBytes(ipString);
        return bytes != null && bytes.length == IPV4_BYTES_LENGTH;
    }

    /**
     * Returns {@code true} if the supplied string is a valid IP string literal, {@code false} otherwise.
     *
     * @param ipString {@code String} to evaluated as an IP string literal
     * @return {@code true} if the argument is a valid IP string literal
     */
    public static boolean isInetAddress(String ipString) {
        return ipStringToBytes(ipString) != null;
    }

    private static byte[] ipStringToBytes(String ipString) {
        // Make a first pass to categorize the characters in this string.
        boolean hasColon = false;
        boolean hasDot = false;
        for (int i = 0; i < ipString.length(); i++) {
            char c = ipString.charAt(i);
            if (c == '.') {
                hasDot = true;
            } else if (c == ':') {
                if (hasDot) {
                    return null; // Colons must not appear after dots.
                }
                hasColon = true;
            } else if (Character.digit(c, 16) == -1) {
                return null; // Everything else must be a decimal or hex digit.
            }
        }

        // Now decide which address family to parse.
        if (hasColon) {
            if (hasDot) {
                ipString = convertDottedQuadToHex(ipString);
                if (ipString == null) {
                    return null;
                }
            }
            return textToNumericFormatV6(ipString);
        } else if (hasDot) {
            return textToNumericFormatV4(ipString);
        }
        return null; // throw new NopException(ERR_NET_INVALID_IP_STRING).param(ARG_IP,ipString);
    }

    public static byte[] textToNumericFormatV4(String ipString) {
        byte[] bytes = new byte[IPV4_PART_COUNT];
        int i = 0;
        try {
            List<String> parts = StringHelper.split(ipString, '.');
            if (parts.size() != 4)
                return null;

            for (String octet : parts) {
                bytes[i++] = parseOctet(octet);
            }
        } catch (NumberFormatException ex) {
            return null;
        }

        return i == IPV4_PART_COUNT ? bytes : null;
    }

    public static byte[] textToNumericFormatV6(String ipString) {
        // An address can have [2..8] colons, and N colons make N+1 parts.
        String[] parts = ipString.split(":", IPV6_PART_COUNT + 2);
        if (parts.length < 3 || parts.length > IPV6_PART_COUNT + 1) {
            return null;
        }

        // Disregarding the endpoints, find "::" with nothing in between.
        // This indicates that a run of zeroes has been skipped.
        int skipIndex = -1;
        for (int i = 1; i < parts.length - 1; i++) {
            if (parts[i].length() == 0) {
                if (skipIndex >= 0) {
                    return null; // Can't have more than one ::
                }
                skipIndex = i;
            }
        }

        int partsHi; // Number of parts to copy from above/before the "::"
        int partsLo; // Number of parts to copy from below/after the "::"
        if (skipIndex >= 0) {
            // If we found a "::", then check if it also covers the endpoints.
            partsHi = skipIndex;
            partsLo = parts.length - skipIndex - 1;
            if (parts[0].length() == 0 && --partsHi != 0) {
                return null; // ^: requires ^::
            }
            if (parts[parts.length - 1].length() == 0 && --partsLo != 0) {
                return null; // :$ requires ::$
            }
        } else {
            // Otherwise, allocate the entire address to partsHi. The endpoints
            // could still be empty, but parseHextet() will check for that.
            partsHi = parts.length;
            partsLo = 0;
        }

        // If we found a ::, then we must have skipped at least one part.
        // Otherwise, we must have exactly the right number of parts.
        int partsSkipped = IPV6_PART_COUNT - (partsHi + partsLo);
        if (!(skipIndex >= 0 ? partsSkipped >= 1 : partsSkipped == 0)) {
            return null;
        }

        // Now parse the hextets into a byte array.
        ByteBuffer rawBytes = ByteBuffer.allocate(2 * IPV6_PART_COUNT);
        try {
            for (int i = 0; i < partsHi; i++) {
                rawBytes.putShort(parseHextet(parts[i]));
            }
            for (int i = 0; i < partsSkipped; i++) {
                rawBytes.putShort((short) 0);
            }
            for (int i = partsLo; i > 0; i--) {
                rawBytes.putShort(parseHextet(parts[parts.length - i]));
            }
        } catch (NumberFormatException ex) {
            return null;
        }
        return rawBytes.array();
    }

    private static String convertDottedQuadToHex(String ipString) {
        int lastColon = ipString.lastIndexOf(':');
        String initialPart = ipString.substring(0, lastColon + 1);
        String dottedQuad = ipString.substring(lastColon + 1);
        byte[] quad = textToNumericFormatV4(dottedQuad);
        if (quad == null) {
            return null;
        }
        String penultimate = Integer.toHexString(((quad[0] & 0xff) << 8) | (quad[1] & 0xff));
        String ultimate = Integer.toHexString(((quad[2] & 0xff) << 8) | (quad[3] & 0xff));
        return initialPart + penultimate + ":" + ultimate;
    }

    private static byte parseOctet(String ipPart) {
        // Note: we already verified that this string contains only hex digits.
        int octet = Integer.parseInt(ipPart);
        // Disallow leading zeroes, because no clear standard exists on
        // whether these should be interpreted as decimal or octal.
        if (octet > 255 || (ipPart.startsWith("0") && ipPart.length() > 1)) {
            throw new NumberFormatException();
        }
        return (byte) octet;
    }

    private static short parseHextet(String ipPart) {
        // Note: we already verified that this string contains only hex digits.
        int hextet = Integer.parseInt(ipPart, 16);
        if (hextet > 0xffff) {
            throw new NumberFormatException();
        }
        return (short) hextet;
    }

    /**
     * Convert a byte array into an InetAddress.
     * <p>
     * {@link InetAddress#getByAddress} is documented as throwing a checked exception "if IP address is of illegal
     * length." We replace it with an unchecked exception, for use by callers who already know that addr is an array of
     * length 4 or 16.
     *
     * @param addr the raw 4-byte or 16-byte IP address in big-endian order
     * @return an InetAddress object created from the raw IP address
     */
    private static InetAddress bytesToInetAddress(byte[] addr) {
        try {
            return InetAddress.getByAddress(addr);
        } catch (UnknownHostException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Returns the string representation of an {@link InetAddress}.
     *
     * <p>
     * For IPv4 addresses, this is identical to {@link InetAddress#getHostAddress()}, but for IPv6 addresses, the output
     * follows <a href="http://tools.ietf.org/html/rfc5952">RFC 5952</a> section 4. The main difference is that this
     * method uses "::" for zero compression, while Java's version uses the uncompressed form.
     *
     * <p>
     * This method uses hexadecimal for all IPv6 addresses, including IPv4-mapped IPv6 addresses such as "::c000:201".
     * The output does not include a Scope ID.
     *
     * @param ip {@link InetAddress} to be converted to an address string
     * @return {@code String} containing the text-formatted IP address
     * @since 10.0
     */
    public static String toAddrString(InetAddress ip) {
        Guard.notNull(ip, "ip");
        if (ip instanceof Inet4Address) {
            // For IPv4, Java's formatting is good enough.
            return ip.getHostAddress();
        }
        Guard.checkArgument(ip instanceof Inet6Address, ip.toString());
        byte[] bytes = ip.getAddress();
        int[] hextets = new int[IPV6_PART_COUNT];
        for (int i = 0; i < hextets.length; i++) {
            hextets[i] = fromBytes((byte) 0, (byte) 0, bytes[2 * i], bytes[2 * i + 1]);
        }
        compressLongestRunOfZeroes(hextets);
        return hextetsToIPv6String(hextets);
    }

    /**
     * Identify and mark the longest run of zeroes in an IPv6 address.
     *
     * <p>
     * Only runs of two or more hextets are considered. In case of a tie, the leftmost run wins. If a qualifying run is
     * found, its hextets are replaced by the sentinel value -1.
     *
     * @param hextets {@code int[]} mutable array of eight 16-bit hextets
     */
    private static void compressLongestRunOfZeroes(int[] hextets) {
        int bestRunStart = -1;
        int bestRunLength = -1;
        int runStart = -1;
        for (int i = 0; i < hextets.length + 1; i++) {
            if (i < hextets.length && hextets[i] == 0) {
                if (runStart < 0) {
                    runStart = i;
                }
            } else if (runStart >= 0) {
                int runLength = i - runStart;
                if (runLength > bestRunLength) {
                    bestRunStart = runStart;
                    bestRunLength = runLength;
                }
                runStart = -1;
            }
        }
        if (bestRunLength >= 2) {
            Arrays.fill(hextets, bestRunStart, bestRunStart + bestRunLength, -1);
        }
    }

    /**
     * Convert a list of hextets into a human-readable IPv6 address.
     *
     * <p>
     * In order for "::" compression to work, the input should contain negative sentinel values in place of the elided
     * zeroes.
     *
     * @param hextets {@code int[]} array of eight 16-bit hextets, or -1s
     */
    private static String hextetsToIPv6String(int[] hextets) {
        // While scanning the array, handle these state transitions:
        // start->num => "num" start->gap => "::"
        // num->num => ":num" num->gap => "::"
        // gap->num => "num" gap->gap => ""
        StringBuilder buf = new StringBuilder(39);
        boolean lastWasNumber = false;
        for (int i = 0; i < hextets.length; i++) {
            boolean thisIsNumber = hextets[i] >= 0;
            if (thisIsNumber) {
                if (lastWasNumber) {
                    buf.append(':');
                }
                buf.append(Integer.toHexString(hextets[i]));
            } else {
                if (i == 0 || lastWasNumber) {
                    buf.append("::");
                }
            }
            lastWasNumber = thisIsNumber;
        }
        return buf.toString();
    }

    static int fromBytes(byte b1, byte b2, byte b3, byte b4) {
        return b1 << 24 | (b2 & 0xFF) << 16 | (b3 & 0xFF) << 8 | (b4 & 0xFF);
    }

    /**
     * copy from ElasticSearch Cidrs.java
     * <p>
     * Parses an IPv4 address block in CIDR notation into a pair of longs representing the bottom and top of the address
     * block
     *
     * @param cidr an address block in CIDR notation a.b.c.d/n
     * @return array representing the address block
     * @throws IllegalArgumentException if the cidr can not be parsed
     */
    public static long[] cidrMaskToMinMax(String cidr) {
        Objects.requireNonNull(cidr, "cidr");
        String[] fields = cidr.split("/");
        if (fields.length != 2) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "invalid IPv4/CIDR; expected [a.b.c.d, e] but was [%s] after splitting on \"/\" in [%s]",
                    Arrays.toString(fields), cidr));
        }
        // do not try to parse IPv4-mapped IPv6 address
        if (fields[0].contains(":")) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "invalid IPv4/CIDR; expected [a.b.c.d, e] where a, b, c, d are decimal octets but was [%s] after splitting on \"/\" in [%s]",
                    Arrays.toString(fields), cidr));
        }
        byte[] addressBytes;
        try {
            addressBytes = NetHelper.ipStringToBytes(fields[0]);
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "invalid IPv4/CIDR; unable to parse [%s] as an IP address literal", fields[0]), e);
        }
        if (addressBytes == null)
            throw new NopException(ERR_NET_INVALID_IP_STRING).param(ARG_IP, fields[0]);

        long accumulator = ((addressBytes[0] & 0xFFL) << 24) + ((addressBytes[1] & 0xFFL) << 16)
                + ((addressBytes[2] & 0xFFL) << 8) + ((addressBytes[3] & 0xFFL));
        int networkMask;
        try {
            networkMask = Integer.parseInt(fields[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT, "invalid IPv4/CIDR; invalid network mask [%s] in [%s]", fields[1], cidr),
                    e);
        }
        if (networkMask < 0 || networkMask > 32) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "invalid IPv4/CIDR; invalid network mask [%s], out of range in [%s]", fields[1], cidr));
        }

        long blockSize = 1L << (32 - networkMask);
        // validation
        if ((accumulator & (blockSize - 1)) != 0) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "invalid IPv4/CIDR; invalid address/network mask combination in [%s]; perhaps [%s] was intended?",
                    cidr, octetsToCIDR(longToOctets(accumulator - (accumulator & (blockSize - 1))), networkMask)));
        }
        return new long[]{accumulator, accumulator + blockSize};
    }

    static int[] longToOctets(long value) {
        assert value >= 0 && value <= (1L << 32) : value;
        int[] octets = new int[4];
        octets[0] = (int) ((value >> 24) & 0xFF);
        octets[1] = (int) ((value >> 16) & 0xFF);
        octets[2] = (int) ((value >> 8) & 0xFF);
        octets[3] = (int) (value & 0xFF);
        return octets;
    }

    static String octetsToString(int[] octets) {
        assert octets != null;
        assert octets.length == 4;
        return String.format(Locale.ROOT, "%d.%d.%d.%d", octets[0], octets[1], octets[2], octets[3]);
    }

    static String octetsToCIDR(int[] octets, int networkMask) {
        assert octets != null;
        assert octets.length == 4;
        return octetsToString(octets) + "/" + networkMask;
    }

    public static String createCIDR(long ipAddress, int networkMask) {
        return octetsToCIDR(longToOctets(ipAddress), networkMask);
    }
}